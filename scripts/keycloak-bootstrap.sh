#!/bin/sh
# Idempotent Keycloak post-start patcher.
#
# Runs every `docker compose up`. Uses kcadm.sh (Keycloak's official admin CLI)
# to ensure realm settings, the finans-backend-admin service-account client,
# and its realm-management role mappings are in place.
#
# Safe to re-run: each step checks existing state before writing.
# Realm.json IGNORE_EXISTING behavior is irrelevant — we patch via REST after import.

set -e

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"
REALM="${REALM:-finans}"
BACKEND_CLIENT_ID="${BACKEND_CLIENT_ID:-finans-backend-admin}"
BACKEND_CLIENT_SECRET="${BACKEND_CLIENT_SECRET:-finans-backend-admin-secret}"

KCADM=/opt/keycloak/bin/kcadm.sh

log() { echo "[bootstrap] $*"; }

log "Waiting for Keycloak admin endpoint at $KC_URL..."
ATTEMPTS=0
until $KCADM config credentials --server "$KC_URL" --realm master \
        --user "$KC_ADMIN_USER" --password "$KC_ADMIN_PASS" >/dev/null 2>&1; do
  ATTEMPTS=$((ATTEMPTS + 1))
  if [ $ATTEMPTS -gt 60 ]; then
    log "ERROR: Keycloak did not become ready in time"
    exit 1
  fi
  sleep 3
done
log "Connected to Keycloak as $KC_ADMIN_USER"

# ── 1) Realm settings ──────────────────────────────────────────────────────
if ! $KCADM get "realms/$REALM" >/dev/null 2>&1; then
  log "Realm '$REALM' not found — was --import-realm run? Aborting."
  exit 1
fi

log "Patching realm '$REALM' settings (rememberMe, session lifespans)..."
$KCADM update "realms/$REALM" \
  -s rememberMe=true \
  -s 'ssoSessionMaxLifespanRememberMe=2592000' \
  -s 'ssoSessionIdleTimeoutRememberMe=86400' \
  -s loginWithEmailAllowed=true \
  -s resetPasswordAllowed=true \
  -s registrationAllowed=true \
  -s 'passwordPolicy=length(8) and upperCase(1) and lowerCase(1) and digits(1) and notUsername(undefined)'
log "  realm settings updated (incl. password policy)"

# ── 1a) Registration validation (username + email) ─────────────────────────
# Declarative user profile: enforce a sane username pattern and a valid email
# (must contain '@') at sign-up. Non-fatal if the KC version differs.
log "Applying user-profile validation (username pattern + valid email)..."
cat > /tmp/finans-user-profile.json <<'JSON'
{
  "attributes": [
    {
      "name": "username",
      "displayName": "${username}",
      "validations": {
        "length": { "min": 3, "max": 255 },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {},
        "pattern": { "pattern": "^[a-zA-Z0-9._-]+$", "error-message": "Kullanıcı adı yalnızca harf, rakam ve . _ - içerebilir (en az 3 karakter)." }
      },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "email",
      "displayName": "${email}",
      "validations": { "email": {}, "length": { "max": 255 } },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "firstName",
      "displayName": "${firstName}",
      "validations": { "length": { "max": 255 }, "person-name-prohibited-characters": {} },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "lastName",
      "displayName": "${lastName}",
      "validations": { "length": { "max": 255 }, "person-name-prohibited-characters": {} },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    }
  ]
}
JSON
if $KCADM update "users/profile" -r "$REALM" -f /tmp/finans-user-profile.json >/dev/null 2>&1; then
  log "  user-profile validation applied (username 3+ [a-zA-Z0-9._-], email required + valid)"
else
  log "  WARN — user-profile update failed (older Keycloak? check version)"
fi

# ── 1b) 2FA configuration (OPTIONAL) ──────────────────────────────────────
# Enable CONFIGURE_TOTP as an OPTIONAL action: users CAN set up 2FA in their
# account settings (Account Console → Security → 2FA), but it's NOT enforced
# on first login. The realm's OTP policy (totp, SHA1, 6 digits, 30s period)
# is in finans-realm.json — users who opt-in scan a QR code in the account
# console and enter a 6-digit verify code.
#
# enabled=true makes 2FA available; defaultAction=false leaves it optional.
# If you want to require 2FA for all users, change defaultAction to true.
log "Making CONFIGURE_TOTP available but optional (users opt-in via Account Console)..."
if $KCADM update "authentication/required-actions/CONFIGURE_TOTP" -r "$REALM" \
    -s enabled=true \
    -s defaultAction=false >/dev/null 2>&1; then
  log "  CONFIGURE_TOTP enabled as optional (not enforced on login)"
else
  log "  WARN — CONFIGURE_TOTP update failed (older Keycloak? check version)"
fi

# ── 2) Backend admin client ────────────────────────────────────────────────
log "Ensuring client '$BACKEND_CLIENT_ID' exists in realm '$REALM'..."
CLIENT_UUID=$($KCADM get clients -r "$REALM" \
  -q "clientId=$BACKEND_CLIENT_ID" --fields id --format csv --noquotes \
  | tr -d '\r' | grep -v '^$' | head -n 1 || true)

if [ -z "$CLIENT_UUID" ]; then
  log "  creating client..."
  $KCADM create clients -r "$REALM" \
    -s "clientId=$BACKEND_CLIENT_ID" \
    -s 'name=Finance Backend Admin Service' \
    -s 'description=Service account for Spring Boot admin REST calls' \
    -s enabled=true \
    -s protocol=openid-connect \
    -s publicClient=false \
    -s clientAuthenticatorType=client-secret \
    -s "secret=$BACKEND_CLIENT_SECRET" \
    -s standardFlowEnabled=false \
    -s implicitFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=true \
    -s fullScopeAllowed=true >/dev/null
  CLIENT_UUID=$($KCADM get clients -r "$REALM" \
    -q "clientId=$BACKEND_CLIENT_ID" --fields id --format csv --noquotes \
    | tr -d '\r' | grep -v '^$' | head -n 1)
  log "  created: $CLIENT_UUID"
else
  log "  already exists ($CLIENT_UUID) — resetting attributes & secret"
  $KCADM update "clients/$CLIENT_UUID" -r "$REALM" \
    -s enabled=true \
    -s publicClient=false \
    -s clientAuthenticatorType=client-secret \
    -s "secret=$BACKEND_CLIENT_SECRET" \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=true \
    -s fullScopeAllowed=true
fi

# ── 3) Service account role mappings ───────────────────────────────────────
SA_USERNAME="service-account-$BACKEND_CLIENT_ID"
log "Granting realm-management roles to '$SA_USERNAME'..."

EXISTING_ROLES=$($KCADM get-roles -r "$REALM" \
  --uusername "$SA_USERNAME" \
  --cclientid realm-management \
  --fields name --format csv --noquotes 2>/dev/null \
  | tr -d '\r"' | sed 's/^name$//' | grep -v '^$' | sort || true)

for ROLE in view-users manage-users query-users view-clients query-clients; do
  if echo "$EXISTING_ROLES" | grep -qx "$ROLE"; then
    log "  = $ROLE (already assigned)"
  else
    if $KCADM add-roles -r "$REALM" \
        --uusername "$SA_USERNAME" \
        --cclientid realm-management \
        --rolename "$ROLE" 2>/dev/null; then
      log "  + $ROLE"
    else
      log "  ! $ROLE (failed to assign)"
    fi
  fi
done

# ── 3.5) LDAP user federation (OpenLDAP) ───────────────────────────────────
# Connect OpenLDAP (dc=finance,dc=local) OUT-OF-THE-BOX. The committed realm
# export has no federation, so create the component + standard attribute mappers
# (uid→username, mail→email, sn→lastName, givenName→firstName) + a group mapper,
# set editMode WRITABLE, and import the seed users. Idempotent: re-running finds
# the existing component and only re-patches editMode.
find_ldap() {
  $KCADM get components -r "$REALM" \
    -q "type=org.keycloak.storage.UserStorageProvider" \
    --fields id,providerId --format csv --noquotes 2>/dev/null \
    | tr -d '\r' | grep ',ldap$' | head -1 | cut -d, -f1
}
LDAP_COMPONENT_ID=$(find_ldap)

if [ -z "$LDAP_COMPONENT_ID" ]; then
  log "No LDAP federation found — creating it (OpenLDAP dc=finance,dc=local)..."
  REALM_ID=$($KCADM get "realms/$REALM" --fields id --format csv --noquotes 2>/dev/null | tr -d '\r ' | head -1)
  cat > /tmp/ldap-fed.json <<JSON
{
  "name": "openldap",
  "providerId": "ldap",
  "providerType": "org.keycloak.storage.UserStorageProvider",
  "parentId": "$REALM_ID",
  "config": {
    "enabled": ["true"],
    "priority": ["0"],
    "vendor": ["other"],
    "connectionUrl": ["ldap://openldap:389"],
    "usersDn": ["ou=users,dc=finance,dc=local"],
    "authType": ["simple"],
    "bindDn": ["cn=admin,dc=finance,dc=local"],
    "bindCredential": ["admin_password"],
    "usernameLDAPAttribute": ["uid"],
    "rdnLDAPAttribute": ["uid"],
    "uuidLDAPAttribute": ["entryUUID"],
    "userObjectClasses": ["inetOrgPerson, organizationalPerson, person"],
    "searchScope": ["1"],
    "editMode": ["WRITABLE"],
    "importEnabled": ["true"],
    "syncRegistrations": ["false"],
    "pagination": ["true"],
    "trustEmail": ["true"],
    "batchSizeForSync": ["1000"],
    "fullSyncPeriod": ["-1"],
    "changedSyncPeriod": ["-1"],
    "cachePolicy": ["DEFAULT"]
  }
}
JSON
  if $KCADM create components -r "$REALM" -f /tmp/ldap-fed.json >/dev/null 2>&1; then
    LDAP_COMPONENT_ID=$(find_ldap)
    log "  + LDAP federation created (id=$LDAP_COMPONENT_ID)"

    # Keycloak's LDAPStorageProviderFactory auto-creates the standard attribute
    # mappers on create (username←uid via usernameLDAPAttribute, email←mail,
    # first name←givenName, last name←sn, creation/modify date). We must NOT
    # re-create them or we get conflicting duplicate mappers. Only the group
    # mapper below is not part of the defaults.
    # Group mapper: finance-users / finance-admins → Keycloak groups.
    cat > /tmp/ldap-grp.json <<JSON
{
  "name": "groups",
  "providerId": "group-ldap-mapper",
  "providerType": "org.keycloak.storage.ldap.mappers.LDAPStorageMapper",
  "parentId": "$LDAP_COMPONENT_ID",
  "config": {
    "groups.dn": ["ou=groups,dc=finance,dc=local"],
    "group.name.ldap.attribute": ["cn"],
    "group.object.classes": ["groupOfNames"],
    "membership.ldap.attribute": ["member"],
    "membership.attribute.type": ["DN"],
    "membership.user.ldap.attribute": ["uid"],
    "mode": ["READ_ONLY"],
    "user.roles.retrieve.strategy": ["LOAD_GROUPS_BY_MEMBER_ATTRIBUTE"],
    "preserve.group.inheritance": ["true"],
    "drop.non.existing.groups.during.sync": ["false"]
  }
}
JSON
    $KCADM create components -r "$REALM" -f /tmp/ldap-grp.json >/dev/null 2>&1 \
      && log "    + mapper: groups" || log "    ! mapper failed: groups"

    # Keycloak's default "first name" mapper reads cn (the full name → "John Doe").
    # Point it at givenName so firstName is just "John" (sn covers the surname).
    FN_ID=$($KCADM get components -r "$REALM" -q "parent=$LDAP_COMPONENT_ID" \
      --fields id,name --format csv --noquotes 2>/dev/null | tr -d '\r' \
      | grep ',first name$' | head -1 | cut -d, -f1)
    if [ -n "$FN_ID" ] && $KCADM update "components/$FN_ID" -r "$REALM" \
        -s 'config."ldap.attribute"=["givenName"]' >/dev/null 2>&1; then
      log "    ~ first name mapper -> givenName"
    fi

    if $KCADM create "user-storage/$LDAP_COMPONENT_ID/sync?action=triggerFullSync" -r "$REALM" >/dev/null 2>&1; then
      log "  + LDAP full sync triggered (seed users imported: john.doe, jane.smith, admin.user, test.user)"
    else
      log "  ! LDAP sync failed (users will import lazily on first login)"
    fi
  else
    log "  ! LDAP federation create failed (is OpenLDAP reachable at ldap://openldap:389?)"
  fi
fi

# Ensure editMode WRITABLE so users can edit their own profile (Settings → Save).
# READ_ONLY makes Keycloak reject attribute updates ("error-user-attribute-read-only").
log "Ensuring LDAP federation editMode = WRITABLE..."
if [ -n "$LDAP_COMPONENT_ID" ]; then
  CUR_MODE=$($KCADM get "components/$LDAP_COMPONENT_ID" -r "$REALM" 2>/dev/null \
    | grep -o '"editMode"[^]]*\]' | grep -o '"[A-Z_]*"' | head -1 | tr -d '"')
  if [ "$CUR_MODE" = "WRITABLE" ]; then
    log "  = LDAP editMode already WRITABLE"
  else
    if $KCADM update "components/$LDAP_COMPONENT_ID" -r "$REALM" \
        -s 'config.editMode=["WRITABLE"]' >/dev/null 2>&1; then
      log "  + LDAP editMode set to WRITABLE (was: ${CUR_MODE:-unknown})"
    else
      log "  ! Failed to update LDAP editMode (manual fix may be required)"
    fi
  fi

  # Optional: also try to flip user-attribute mappers. Failures here are
  # non-fatal — once editMode is WRITABLE the mapper defaults usually suffice.
  # Wrapped in `|| true` so this never aborts the bootstrap.
  log "Ensuring LDAP attribute mappers allow writes (best effort)..."
  set +e
  MAPPER_IDS=$($KCADM get "components" -r "$REALM" \
    -q "parent=$LDAP_COMPONENT_ID,providerId=user-attribute-ldap-mapper" \
    --fields id --format csv --noquotes 2>/dev/null \
    | tr -d '\r"' | grep -v '^id$' | grep -v '^$')

  PATCHED=0
  if [ -n "$MAPPER_IDS" ]; then
    for MID in $MAPPER_IDS; do
      [ -z "$MID" ] && continue
      if $KCADM update "components/$MID" -r "$REALM" \
          -s 'config."read.only"=["false"]' >/dev/null 2>&1; then
        PATCHED=$((PATCHED + 1))
      fi
    done
  fi
  log "    + $PATCHED user-attribute mapper(s) patched (zero is OK if editMode handles it)"
  set -e
else
  log "  ! No LDAP user-federation component found (skipping)"
fi

# ── 3.6) Map LDAP groups → realm roles (RBAC) ──────────────────────────────
# finance-admins → ADMIN, finance-users → USER, so federated users get the
# right authorization out-of-the-box. Idempotent + non-fatal (groups only exist
# after an LDAP sync has imported them).
log "Mapping LDAP groups to realm roles..."
$KCADM add-roles -r "$REALM" --gname finance-admins --rolename ADMIN >/dev/null 2>&1 \
  && log "  + finance-admins -> ADMIN" || log "  = finance-admins -> ADMIN (group missing or already mapped)"
$KCADM add-roles -r "$REALM" --gname finance-users --rolename USER >/dev/null 2>&1 \
  && log "  + finance-users -> USER" || log "  = finance-users -> USER (group missing or already mapped)"

# ── 4) Verification (via service-account credentials) ──────────────────────
log "Verifying service-account can call admin REST..."
SA_CONFIG=/tmp/sa-kcadm.json
if $KCADM config credentials --server "$KC_URL" --realm "$REALM" \
    --client "$BACKEND_CLIENT_ID" --secret "$BACKEND_CLIENT_SECRET" \
    --config "$SA_CONFIG" >/dev/null 2>&1; then
  if $KCADM get users -r "$REALM" -q max=1 --config "$SA_CONFIG" >/dev/null 2>&1; then
    log "OK — service-account can list users via Admin REST"
  else
    log "ERROR — service-account got auth but /users call failed"
    exit 1
  fi
else
  log "ERROR — service-account credentials rejected"
  exit 1
fi

log "Bootstrap complete."
