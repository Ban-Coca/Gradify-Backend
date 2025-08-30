package com.capstone.gradify.Entity.subscription;

import com.capstone.gradify.Entity.enums.SyncStatus;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tracked_files")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackedFiles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private OneDriveSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_spreadsheet_id")
    private ClassSpreadsheet spreadsheet;

    private String itemId;
    private String filePath;
    private OffsetDateTime lastModifiedDateTime;
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus;
}
