package com.capstone.gradify.Service.notification;

import com.capstone.gradify.Entity.notification.NotificationEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.report.ReportEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.notification.NotificationRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Service.userservice.UserService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TaskScheduler taskScheduler;
    private final GradeRecordRepository gradeRecordRepository;
    private final FirebaseApp firebaseApp;
    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final EmailService emailService;

    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final Duration debounceDelay = Duration.ofSeconds(5);

    @Transactional
    public void sendNotification(ReportEntity report) {
        try{
            UserEntity user = userService.findById(report.getStudent().getUserId());
            String subject = report.getSubject();
            String feedback = report.getMessage();
            String notificationType = report.getNotificationType();
            int reportId = report.getReportId();
            NotificationEntity notificationEntity = new NotificationEntity(
                    notificationType,
                    subject,
                    feedback,
                    user,
                    reportId
            );
            notificationRepository.save(notificationEntity);
            String fcmToken = user.getFCMToken();
            if (fcmToken == null || fcmToken.isEmpty()) {
                log.info("User doesn't have a registered device token. Skipping notification.");
                return;
            }
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                        .setTitle(subject)
                        .setBody(feedback)
                        .build())
                    .setToken(fcmToken)
                    .build();

            String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("Successfully sent notification: " + response);
        }catch (Exception e){
            log.error("Unexpected error in notification service: " + e.getMessage(), e);
        }
    }

    public Page<NotificationEntity> getUserNotifications(UserEntity user, Pageable pageable) {
        return notificationRepository.findByUserOrderByDateDesc(user, pageable);
    }

    // Get user's unread notifications
    public List<NotificationEntity> getUnreadNotifications(UserEntity user) {
        return notificationRepository.findByUserAndReadFalseOrderByDateDesc(user);
    }

    // Get unread notification count
    public long getUnreadCount(UserEntity user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    // Mark a notification as read
    @Transactional
    public void markAsRead(int notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }

    // Mark all notifications as read for a user
    @Transactional
    public int markAllAsRead(UserEntity user) {
        return notificationRepository.markAllAsRead(user);
    }

    public void scheduleVisibilityChange(int classSpreadsheetId, String assessmentName, String notificationType, String title, String body) {
        String key = buildKey(classSpreadsheetId, assessmentName);

        // cancel existing pending task if present
        ScheduledFuture<?> existing = pendingTasks.get(key);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        // schedule a new task
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                pendingTasks.remove(key);
                sendNotificationsForVisibilityChange(classSpreadsheetId, assessmentName, notificationType, title, body);
            } catch (Exception e) {
                log.error("Error processing visibility notifications for key {}: {}", key, e.getMessage(), e);
            }
        }, java.util.Date.from(java.time.Instant.now().plus(debounceDelay)));

        pendingTasks.put(key, future);
    }

    public void flushNow(int classSpreadsheetId, String assessmentName, String notificationType, String title, String body) {
        String key = buildKey(classSpreadsheetId, assessmentName);
        ScheduledFuture<?> existing = pendingTasks.remove(key);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
        // run immediately
        taskScheduler.schedule(() -> {
            try {
                sendNotificationsForVisibilityChange(classSpreadsheetId, assessmentName, notificationType, title, body);
            } catch (Exception e) {
                log.error("Error flushing notifications for key {}: {}", key, e.getMessage(), e);
            }
        }, java.util.Date.from(java.time.Instant.now()));
    }

    private void sendNotificationsForVisibilityChange(int classSpreadsheetId, String assessmentName, String notificationType, String title, String body) {
        log.info("Processing visibility notifications for classSpreadsheetId={} assessment={} ", classSpreadsheetId, assessmentName);

        // Fetch grade records for this class to find enrolled students (assumes repo method exists)
        List<GradeRecordsEntity> records = gradeRecordRepository.findByClassRecord_ClassEntity_ClassId(classSpreadsheetId);

        if (records == null || records.isEmpty()) {
            log.info("No student records found for classSpreadsheetId={}", classSpreadsheetId);
            return;
        }

        // Collect unique users and tokens
        List<UserEntity> users = records.stream()
                .map(GradeRecordsEntity::getStudent)
                .filter(u -> u != null)
                .distinct()
                .collect(Collectors.toList());

        List<Message> messages = new ArrayList<>();
        List<NotificationEntity> savedNotifications = new ArrayList<>();

        for (UserEntity user : users) {
            String token = user.getFCMToken();
            // persist notification record
            NotificationEntity notification = new NotificationEntity(notificationType, title, body, user, 0);
            savedNotifications.add(notification);

            if (token != null && !token.isBlank()) {
                Message msg = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putData("assessment", assessmentName)
                        .putData("classSpreadsheetId", String.valueOf(classSpreadsheetId))
                        .build();
                messages.add(msg);
            }

            // Send email notification if user has email
            try {
                String toEmail = null;
                // attempt to get email via getEmail() if available
                try {
                    toEmail = (String) UserEntity.class.getMethod("getEmail").invoke(user);
                } catch (Exception e) {
                    // reflection failed or method absent; fallback to null
                }

                if (toEmail != null && !toEmail.isBlank()) {
                    String reportDate = LocalDate.now().toString();
                    // grade param is assessmentName (may be null)
                    emailService.sendGradeUpdate(toEmail, assessmentName, null, null, null, reportDate);
                }
            } catch (MessagingException me) {
                log.error("Failed to send email to user: {}", me.getMessage(), me);
            } catch (Exception ex) {
                // guard against reflection or unexpected errors
                log.debug("Skipping email send due to error when resolving user email: {}", ex.getMessage());
            }
        }

        // Save all notification entities in a batch (transactional repository behavior assumed)
        if (!savedNotifications.isEmpty()) {
            notificationRepository.saveAll(savedNotifications);
        }

        // Send FCM messages in chunks of up to 500
        final int CHUNK = 500;
        for (int i = 0; i < messages.size(); i += CHUNK) {
            int end = Math.min(messages.size(), i + CHUNK);
            List<Message> chunk = messages.subList(i, end);
            try {
                var batchResponse = FirebaseMessaging.getInstance(firebaseApp).sendAll(chunk);
                log.info("FCM sendAll result: success={}, failure={}", batchResponse.getSuccessCount(), batchResponse.getFailureCount());
            } catch (Exception e) {
                log.error("FCM sendAll failed for chunk starting at {}: {}", i, e.getMessage(), e);
            }
        }
    }

    private String buildKey(int classSpreadsheetId, String assessmentName) {
        return classSpreadsheetId + ":" + (assessmentName == null ? "<all>" : assessmentName);
    }
}
