package com.capstone.gradify.Service.notification;

import com.capstone.gradify.Entity.NotificationEntity;
import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.NotificationRepository;
import com.capstone.gradify.Service.userservice.UserService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final FirebaseApp firebaseApp;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserService userService;
    public NotificationService(FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
    }

    @Transactional
    public void sendNotification(ReportEntity report) {
        try{
            UserEntity user = userService.findById(report.getStudent().getUserId());
            String subject = report.getSubject();
            String feedback = report.getMessage();
            String notificationType = report.getNotificationType();

            NotificationEntity notificationEntity = new NotificationEntity(
                    notificationType,
                    subject,
                    feedback,
                    user
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

    // Create a general notification (not related to tasks)
//    @Transactional
//    public NotificationEntity createNotification(String title, String message, UserEntity user,
//                                                 String notificationType) {
//        NotificationEntity notification = new NotificationEntity(
//                title, message, user, notificationType
//        );
//        return notificationRepository.save(notification);
//    }
}
