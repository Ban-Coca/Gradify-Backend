package com.capstone.gradify.Entity.subscription;

import com.capstone.gradify.Entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "onedrive_subscription")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OneDriveSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String subscriptionId;
    private Integer userId;
    private String driveId;
    private String resource;
    private String clientState;
    private OffsetDateTime expirationDateTime;
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL)
    private List<TrackedFiles> trackedFiles = new ArrayList<>();

}
