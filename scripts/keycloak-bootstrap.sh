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
  -s registrationAllowed=true
log "  realm settings updated"

# ── 1b) 2FA enforcement ───────────────────────────────────────────────────
# Make CONFIGURE_TOTP a default required action: every NEW user gets it
# on their first login (TOTP setup screen → scan QR → 6-digit verify),
# every EXISTING user gets it on their next login until they configure
# it once. The realm's OTP policy (totp, SHA1, 6 digits, 30s period) is
# already in finans-realm.json — this step just flips the action from
# optional to enforced.
#
# defaultAction=true is the load-bearing flag. enabled=true is the
# precondition (Keycloak ships it enabled by default; we set it
# explicitly so the script is idempotent against ops who toggled it off).
log "Enforcing CONFIGURE_TOTP required action (2FA on first/next login)..."
if $KCADM update "authentication/required-actions/CONFIGURE_TOTP" -r "$REALM" \
    -s enabled=true \
    -s defaultAction=true >/dev/null 2>&1; then
  log "  CONFIGURE_TOTP marked as default required action"
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

# ── 3.5) LDAP user federation must be WRITABLE so users can edit their own
# profile (Settings page → "Save"). The default editMode for an imported
# LDAP federation is READ_ONLY, which makes Keycloak reject any attribute
# update with "error-user-attribute-read-only".
log "Ensuring LDAP federation editMode = WRITABLE..."

# Get all UserStorageProvider components, find the one whose providerId is "ldap".
# CSV format: "id","name","providerId" — use grep+cut (no awk available in image).
LDAP_COMPONENT_ID=$($KCADM get components -r "$REALM" \
  -q "type=org.keycloak.storage.UserStorageProvider" \
  --fields id,providerId --format csv --noquotes 2>/dev/null \
  | tr -d '\r' | grep ',ldap$' | head -1 | cut -d, -f1)

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
