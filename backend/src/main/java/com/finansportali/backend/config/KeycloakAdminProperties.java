package com.finansportali.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakAdminProperties {

    private String serverUrl;
    private String realm;
    private String adminClientId;
    private String adminClientSecret;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getAdminClientId() {
        return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }

    public String getAdminClientSecret() {
        return adminClientSecret;
    }

    public void setAdminClientSecret(String adminClientSecret) {
        this.adminClientSecret = adminClientSecret;
    }
}
