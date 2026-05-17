package com.finansportali.backend.dto.response.admin;

import java.util.List;

public record KeycloakUserDto(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        Long createdTimestamp,
        List<String> requiredActions,
        boolean totpEnabled
) {
}
