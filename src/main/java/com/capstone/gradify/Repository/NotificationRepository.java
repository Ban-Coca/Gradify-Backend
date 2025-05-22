package com.capstone.gradify.Repository;

import com.capstone.gradify.Entity.NotificationEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Integer> {

    List<NotificationEntity> findByUserOrderByDateDesc(UserEntity user);

    Page<NotificationEntity> findByUserOrderByDateDesc(UserEntity user, Pageable pageable);

    List<NotificationEntity> findByUserAndReadFalseOrderByDateDesc(UserEntity user);

    long countByUserAndReadFalse(UserEntity user);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.user = :user AND n.read = false")
    int markAllAsRead(UserEntity user);

    List<NotificationEntity> findByUserAndNotificationTypeOrderByDateDesc(UserEntity user, String notificationType);
}
