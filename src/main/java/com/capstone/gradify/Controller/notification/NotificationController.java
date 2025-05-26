package com.capstone.gradify.Controller.notification;

import com.capstone.gradify.Entity.NotificationEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.notification.NotificationService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UserService userService;

    @GetMapping("/get-notifications/{userId}")
    public ResponseEntity<NotificationResponse> getUserNotifications(
            @PathVariable(required = false) int userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UserEntity currentUser = userService.findById(userId);
        log.debug("Getting user notifications with username: {}", currentUser);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("date").descending());
        Page<NotificationEntity> notifications = notificationService.getUserNotifications(currentUser, pageRequest);

        return ResponseEntity.ok(NotificationResponse.fromPage(notifications));
    }

    @GetMapping("/unread/count/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable (required = false) int userId) {
        UserEntity currentUser = userService.findById(userId);
        long count = notificationService.getUnreadCount(currentUser);

        Map<String, Long> response = new HashMap<>();
        response.put("count", count);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread/{userId}")
    public ResponseEntity<List<NotificationEntity>> getUnreadNotifications(@PathVariable (required = false) int userId) {
        UserEntity currentUser = userService.findById(userId);
        List<NotificationEntity> notifications = notificationService.getUnreadNotifications(currentUser);

        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable int id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all/{userId}")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@PathVariable (required = false) int userId) {
        UserEntity currentUser = userService.findById(userId);
        int updatedCount = notificationService.markAllAsRead(currentUser);

        Map<String, Integer> response = new HashMap<>();
        response.put("markedAsRead", updatedCount);

        return ResponseEntity.ok(response);
    }
}
