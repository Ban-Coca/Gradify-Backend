package com.capstone.gradify.Repository.subscription;

import com.capstone.gradify.Entity.enums.SubscriptionStatus;
import com.capstone.gradify.Entity.subscription.OneDriveSubscription;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OneDriveSubscriptionRepository extends JpaRepository<OneDriveSubscription, Long> {

    Optional<OneDriveSubscription> findBySubscriptionId(String subscriptionId);

    List<OneDriveSubscription> findByUserId(Integer userId);

    Optional<OneDriveSubscription> findByUserIdAndStatus(Integer userId, SubscriptionStatus status);
    List<OneDriveSubscription> findByStatus(SubscriptionStatus status);
    List<OneDriveSubscription> findByExpirationDateTimeBeforeAndStatus(
            OffsetDateTime dateTime, SubscriptionStatus status);
}
