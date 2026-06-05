package com.finansportali.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.UserPreferences;
import com.finansportali.backend.repository.UserPreferencesRepository;
import com.finansportali.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the per-user notification-preferences endpoints.
 * Mirrors {@link UserProfileControllerTest}: {@code @WebMvcTest} + MockMvc
 * + {@link TestSecurityConfig} + {@code @WithMockUser}. The real
 * auto-configured {@link ObjectMapper} is left wired so the controller's
 * (de)serialisation branches actually execute.
 */
@WebMvcTest(UserPreferencesController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class UserPreferencesControllerTest {

    private static final String URL = "/api/v1/users/me/notification-prefs";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserPreferencesRepository repo;
    @MockitoBean private UserService userService;

    // ---------------------------------------------------------------- GET

    @Test
    void get_returns_401_when_user_is_anonymous() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repo);
    }

    @Test
    void get_returns_empty_map_when_no_row_exists() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1")).thenReturn(Optional.empty());

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void get_returns_empty_map_when_stored_json_is_null() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1"))
                .thenReturn(Optional.of(new UserPreferences("user-1", null)));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void get_returns_empty_map_when_stored_json_is_blank() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1"))
                .thenReturn(Optional.of(new UserPreferences("user-1", "   ")));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void get_returns_stored_prefs_when_json_is_valid() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1"))
                .thenReturn(Optional.of(new UserPreferences(
                        "user-1", "{\"transactions\":true,\"push\":false}")));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(true))
                .andExpect(jsonPath("$.push").value(false));
    }

    @Test
    void get_returns_empty_map_when_stored_json_is_unparseable() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1"))
                .thenReturn(Optional.of(new UserPreferences("user-1", "not-json{")));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        verify(repo, never()).save(any());
    }

    // ---------------------------------------------------------------- PUT

    @Test
    void put_returns_401_when_user_is_anonymous() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":true}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repo);
    }

    @Test
    void put_persists_new_row_and_returns_saved_map() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1")).thenReturn(Optional.empty());

        mvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":true,\"push\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(true))
                .andExpect(jsonPath("$.push").value(false));

        ArgumentCaptorHolder.assertSaved(repo, "user-1",
                "{\"transactions\":true,\"push\":false}", objectMapper);
    }

    @Test
    void put_reuses_existing_row_when_present() throws Exception {
        UserPreferences existing = new UserPreferences("user-1", "{\"old\":true}");
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1")).thenReturn(Optional.of(existing));

        mvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(false));

        // The same row instance is mutated and saved, not a fresh one.
        assertThat(existing.getNotificationPrefs()).isEqualTo("{\"transactions\":false}");
        verify(repo).save(existing);
    }

    @Test
    void put_strips_null_values_defensively() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1")).thenReturn(Optional.empty());

        // A malformed client could send a null value; it must be dropped.
        mvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":true,\"push\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(true))
                .andExpect(jsonPath("$.push").doesNotExist());
    }

    @Test
    void put_accepts_empty_map_and_returns_empty_map() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById("user-1")).thenReturn(Optional.empty());

        mvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        verify(repo).save(any(UserPreferences.class));
    }

    // ----------------------------------------------- direct-call branches

    /**
     * The {@code incoming == null} guard cannot be exercised via MockMvc
     * (an absent body is rejected before the handler runs), so invoke the
     * handler method directly to cover the 400 branch.
     */
    @Test
    void put_returns_400_when_incoming_map_is_null() {
        UserPreferencesController controller =
                new UserPreferencesController(repo, userService, new ObjectMapper());
        when(userService.getCurrentUserId()).thenReturn("user-1");

        ResponseEntity<Map<String, Boolean>> resp = controller.put(null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verify(repo, never()).save(any());
    }

    @Test
    void put_returns_401_via_direct_call_when_anonymous() {
        UserPreferencesController controller =
                new UserPreferencesController(repo, userService, new ObjectMapper());
        when(userService.getCurrentUserId()).thenReturn(null);

        ResponseEntity<Map<String, Boolean>> resp = controller.put(Map.of("a", true));

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(repo);
    }

    /**
     * Helper so the captor + jackson decode lives in one place; verifies the
     * row's serialised JSON deserialises back to the expected preference map.
     */
    private static final class ArgumentCaptorHolder {
        static void assertSaved(UserPreferencesRepository repo,
                                String userId,
                                String expectedJson,
                                ObjectMapper mapper) throws Exception {
            org.mockito.ArgumentCaptor<UserPreferences> captor =
                    org.mockito.ArgumentCaptor.forClass(UserPreferences.class);
            verify(repo).save(captor.capture());
            UserPreferences saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);

            Map<String, Boolean> expected =
                    mapper.readValue(expectedJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Map<String, Boolean> actual =
                    mapper.readValue(saved.getNotificationPrefs(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(actual).isEqualTo(expected);
        }
    }
}
