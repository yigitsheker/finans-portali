import type Keycloak from "keycloak-js";

/**
 * Extract roles from Keycloak token.
 * 
 * Keycloak can store roles in different places in the token:
 * 1. Direct "roles" claim (configured via client scope mapper)
 * 2. realm_access.roles (realm-level roles)
 * 3. resource_access.{client}.roles (client-specific roles)
 * 
 * This function tries to extract roles from all possible locations.
 */
export function getUserRoles(keycloak: Keycloak): string[] {
    if (!keycloak.authenticated || !keycloak.tokenParsed) {
        return [];
    }

    const token: any = keycloak.tokenParsed;
    const roles: string[] = [];

    // Try direct "roles" claim first (simplest)
    if (token.roles && Array.isArray(token.roles)) {
        roles.push(...token.roles);
    }

    // Try realm_access.roles
    if (token.realm_access && Array.isArray(token.realm_access.roles)) {
        roles.push(...token.realm_access.roles);
    }

    // Try resource_access.{client}.roles
    if (token.resource_access) {
        Object.keys(token.resource_access).forEach((client) => {
            const clientRoles = token.resource_access[client]?.roles;
            if (Array.isArray(clientRoles)) {
                roles.push(...clientRoles);
            }
        });
    }

    // Remove duplicates and filter out Keycloak default roles
    const uniqueRoles = Array.from(new Set(roles));
    return uniqueRoles.filter(role => 
        !role.startsWith('default-') && 
        !role.startsWith('offline_') &&
        !role.startsWith('uma_')
    );
}

/**
 * Check if user has a specific role.
 */
export function hasRole(keycloak: Keycloak, role: string): boolean {
    const roles = getUserRoles(keycloak);
    return roles.includes(role);
}

/**
 * Check if user is an administrator.
 */
export function isAdmin(keycloak: Keycloak): boolean {
    return hasRole(keycloak, 'ADMIN');
}

/**
 * Check if user is a regular user.
 */
export function isUser(keycloak: Keycloak): boolean {
    return hasRole(keycloak, 'USER');
}

/**
 * Get user display name from token.
 */
export function getUserDisplayName(keycloak: Keycloak): string {
    if (!keycloak.authenticated || !keycloak.tokenParsed) {
        return 'Guest';
    }

    const token: any = keycloak.tokenParsed;
    
    // Try different name fields
    return token.name || 
           token.preferred_username || 
           token.given_name || 
           token.email || 
           'User';
}

/**
 * Get user email from token.
 */
export function getUserEmail(keycloak: Keycloak): string | null {
    if (!keycloak.authenticated || !keycloak.tokenParsed) {
        return null;
    }

    const token: any = keycloak.tokenParsed;
    return token.email || null;
}
