package com.finansportali.backend.controller;

import com.finansportali.backend.entity.Notification;
import com.finansportali.backend.repository.NotificationRepository;
import com.finansportali.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class NotificationControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationRepository repo;
    @MockitoBean private UserService userService;

    private Notification stubNotif(Long id, String userId, boolean read) {
        // The Notification entity exposes no setId(); we test the controller
        // by stubbing repo.findById(...) to return this instance, so the
        // controller never touches getId() — id is only used in the URL.
        Notification n = new Notification(userId, "price-alert", "Test", "Body", "ref-1");
        n.setRead(read);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    @Test
    void list_returns_empty_when_user_is_anonymous() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(repo, never()).findByUserIdOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    void list_clamps_limit_to_one_at_minimum() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(List.of(stubNotif(1L, "user-1", false)));

        mvc.perform(get("/api/v1/notifications").param("limit", "0"))
                .andExpect(status().isOk());

        verify(repo).findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 1));
    }

    @Test
    void list_clamps_limit_to_one_hundred_at_max() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/notifications").param("limit", "999"))
                .andExpect(status().isOk());

        verify(repo).findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 100));
    }

    @Test
    void unread_count_returns_zero_for_anonymous() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void unread_count_returns_repo_total() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.countByUserIdAndReadFalse("user-1")).thenReturn(7L);

        mvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void mark_read_404s_when_notification_is_for_another_user() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById(42L)).thenReturn(Optional.of(stubNotif(42L, "user-OTHER", false)));

        mvc.perform(post("/api/v1/notifications/42/read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());

        verify(repo, never()).save(any());
    }

    @Test
    void mark_read_404s_when_id_unknown() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/notifications/99/read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void mark_read_persists_when_owner_matches_and_unread() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        Notification n = stubNotif(5L, "user-1", false);
        when(repo.findById(5L)).thenReturn(Optional.of(n));

        mvc.perform(post("/api/v1/notifications/5/read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        verify(repo, times(1)).save(n);
    }

    @Test
    void mark_read_is_idempotent_when_already_read() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        Notification n = stubNotif(5L, "user-1", true);
        when(repo.findById(5L)).thenReturn(Optional.of(n));

        mvc.perform(post("/api/v1/notifications/5/read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        // No save when the row is already marked read.
        verify(repo, never()).save(any());
    }

    @Test
    void mark_all_read_returns_update_count() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("user-1");
        when(repo.markAllAsRead(eq("user-1"), any(LocalDateTime.class))).thenReturn(4);

        mvc.perform(post("/api/v1/notifications/mark-all-read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(4));
    }

    @Test
    void mark_all_read_returns_zero_for_anonymous_user() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(null);

        mvc.perform(post("/api/v1/notifications/mark-all-read").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }
}
