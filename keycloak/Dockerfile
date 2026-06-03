# Custom Keycloak image — base Keycloak plus the bundled "finance-theme" login
# theme. The realm (finans-realm.json) already sets loginTheme=finance-theme, so
# in docker-compose the theme is supplied via a bind-mount; on GKE there's no
# host mount, so we bake the theme into the image instead. Without this the
# login page silently falls back to the default Keycloak theme.
#
# Kept on start-dev to match the existing deployment (the app runs Keycloak in
# dev mode; switching to a production build is a separate hardening step).
FROM quay.io/keycloak/keycloak:26.5.1

# Theme files live under keycloak-themes/finance-theme in the repo. Copy the
# whole tree into Keycloak's themes dir so it's discoverable as "finance-theme".
COPY --chown=keycloak:keycloak keycloak-themes/finance-theme /opt/keycloak/themes/finance-theme

# Run Keycloak's build/augmentation step at IMAGE BUILD time, not on every pod
# start. Without this the container did Quarkus augmentation (~52s) on each boot,
# which blew past the liveness probe and caused a CrashLoop. Baking it in makes
# startup fast and deterministic. (The pod still runs start-dev; this just
# pre-optimizes the augmented build.)
RUN /opt/keycloak/bin/kc.sh build
