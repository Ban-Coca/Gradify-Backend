package com.capstone.gradify.Controller.notification;

import com.capstone.gradify.Entity.NotificationEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.notification.NotificationService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/get-notifications/{userId}")
    public ResponseEntity<NotificationResponse> getUserNotifications(
            @PathVariable int userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            UserEntity currentUser = userService.findById(userId);
            if (currentUser == null) {
                return ResponseEntity.ok(NotificationResponse.fromPage(Page.empty()));
            }
            log.debug("Getting user notifications for userId: {}", currentUser.getUserId());
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by("date").descending());
            Page<NotificationEntity> notifications = notificationService.getUserNotifications(currentUser, pageRequest);
            return ResponseEntity.ok(NotificationResponse.fromPage(notifications));
        } catch (Exception e) {
            log.error("Error fetching notifications", e);
            return ResponseEntity.status(500).body(NotificationResponse.fromPage(Page.empty()));
        }
    }

    @GetMapping("/unread/count/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable int userId) {
        Map<String, Long> response = new HashMap<>();
        try {
            UserEntity currentUser = userService.findById(userId);
            if (currentUser == null) {
                response.put("count", 0L);
                return ResponseEntity.ok(response);
            }
            long count = notificationService.getUnreadCount(currentUser);
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching unread count", e);
            response.put("count", 0L);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/unread/{userId}")
    public ResponseEntity<List<NotificationEntity>> getUnreadNotifications(@PathVariable int userId) {
        try {
            UserEntity currentUser = userService.findById(userId);
            if (currentUser == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<NotificationEntity> notifications = notificationService.getUnreadNotifications(currentUser);
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            log.error("Error fetching unread notifications", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable int id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking notification as read", e);
            return ResponseEntity.ok().build();
        }
    }

    @PutMapping("/read-all/{userId}")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@PathVariable int userId) {
        Map<String, Integer> response = new HashMap<>();
        try {
            UserEntity currentUser = userService.findById(userId);
            if (currentUser == null) {
                response.put("markedAsRead", 0);
                return ResponseEntity.ok(response);
            }
            int updatedCount = notificationService.markAllAsRead(currentUser);
            response.put("markedAsRead", updatedCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error marking all notifications as read", e);
            response.put("markedAsRead", 0);
            return ResponseEntity.ok(response);
        }
    }
}
