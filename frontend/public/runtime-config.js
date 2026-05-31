// Default (empty) runtime config. In container deploys this file is replaced
// by a ConfigMap-mounted version carrying environment-specific values, e.g.:
//   window.__RUNTIME_CONFIG__ = { VITE_KEYCLOAK_URL: "http://auth.example.com" };
// Locally it stays empty so the app falls back to build-time env / dev defaults.
window.__RUNTIME_CONFIG__ = {};
