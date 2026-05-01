import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
    url: "http://localhost:8081",
    realm: "finans",
    clientId: "finans-frontend",
});

export default keycloak;