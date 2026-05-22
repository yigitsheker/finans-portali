package com.finansportali.backend.controller;

import com.finansportali.backend.service.KeycloakAdminService;
import com.finansportali.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class UserProfileControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private UserService userService;
    @MockitoBean private KeycloakAdminService keycloakAdminService;

    @Test
    void patch_returns_401_when_user_is_anonymous() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(patch("/api/v1/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void patch_forwards_profile_to_keycloak_admin() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");

        mvc.perform(patch("/api/v1/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"a@b.c\",\"phone\":\"+90...\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.updated").value(true));

        verify(keycloakAdminService).updateUserProfile("user-1", "Ada", "Lovelace", "a@b.c", "+90...");
    }
}
