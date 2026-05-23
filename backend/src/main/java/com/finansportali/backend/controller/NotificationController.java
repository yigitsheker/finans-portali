package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.NotificationDto;
import com.finansportali.backend.entity.Notification;
import com.finansportali.backend.repository.NotificationRepository;
import com.finansportali.backend.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository repo;
    private final UserService userService;

    public NotificationController(NotificationRepository repo, UserService userService) {
        this.repo = repo;
        this.userService = userService;
    }

    /** Most recent N notifications for the calling user (default 30, max 100). */
    @GetMapping
    public List<NotificationDto> list(@RequestParam(defaultValue = "30") int limit) {
        String userId = userService.getCurrentUserId();
        if (userId == null) return List.of();
        int n = (int) Math.clamp((long) limit, 1, 100);
        return repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, n))
                .stream().map(NotificationDto::from).toList();
    }

    /** Count of unread notifications — drives the bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        String userId = userService.getCurrentUserId();
        long count = userId == null ? 0 : repo.countByUserIdAndReadFalse(userId);
        return Map.of("count", count);
    }

    /** Mark a single notification as read. */
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        String userId = userService.getCurrentUserId();
        Notification n = repo.findById(id).orElse(null);
        if (n == null || !n.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            repo.save(n);
        }
        return ResponseEntity.noContent().build();
    }

    /** Mark every unread notification for the user as read. */
    @PostMapping("/mark-all-read")
    @Transactional
    public Map<String, Integer> markAllRead() {
        String userId = userService.getCurrentUserId();
        if (userId == null) return Map.of("updated", 0);
        int updated = repo.markAllAsRead(userId, LocalDateTime.now());
        return Map.of("updated", updated);
    }
}
