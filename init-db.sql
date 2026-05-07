-- Create Keycloak database
CREATE DATABASE keycloak_db;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO finans_user;
GRANT ALL PRIVILEGES ON DATABASE finans_db TO finans_user;
