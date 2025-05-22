package com.capstone.gradify.Entity;

import com.capstone.gradify.Entity.user.UserEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int notificationId;
    private String notificationType;
    private String subject;
    private String message;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime date;

    @Column(nullable = false)
    private boolean read;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private UserEntity user;

    public NotificationEntity(String notificationType, String subject, String message, UserEntity user) {
        this.notificationType = notificationType;
        this.subject = subject;
        this.message = message;
        this.date = LocalDateTime.now();
        this.user = user;
        this.read = false;
    }
    public NotificationEntity() {
        // Default constructor
    }
}
