# Use older stable version (1.5.0 has missing files bug)
FROM osixia/openldap:1.4.0

# Copy init.ldif into the image (not bind-mount)
# This way the container has its own copy and can sed it freely
# without touching the host file
COPY init.ldif /container/service/slapd/assets/config/bootstrap/ldif/custom/init.ldif
