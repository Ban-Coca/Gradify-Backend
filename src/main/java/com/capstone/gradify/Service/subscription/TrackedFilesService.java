package com.capstone.gradify.Service.subscription;

import com.capstone.gradify.Entity.enums.SubscriptionStatus;
import com.capstone.gradify.Entity.enums.SyncStatus;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.subscription.OneDriveSubscription;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.subscription.OneDriveSubscriptionRepository;
import com.capstone.gradify.Repository.subscription.TrackedFileRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TrackedFilesService {
    private final TrackedFileRepository trackedFileRepository;
    private final OneDriveSubscriptionRepository oneDriveSubscriptionRepository;
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final TeacherRepository teacherRepository;

    public TrackedFiles saveTrackedFile(Long subscriptionId, Long classSpreadsheetId, String itemId) {
        OneDriveSubscription subscription = oneDriveSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        ClassSpreadsheet spreadsheet = classSpreadsheetRepository.findById(classSpreadsheetId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSpreadsheet not found"));

        TrackedFiles trackedFile = new TrackedFiles();
        trackedFile.setSubscription(subscription);
        trackedFile.setSpreadsheet(spreadsheet);
        trackedFile.setItemId(itemId);
        trackedFile.setFilePath(spreadsheet.getFolderName() + "/" + spreadsheet.getFileName());
        trackedFile.setSyncStatus(SyncStatus.PENDING);
        trackedFile.setLastModifiedDateTime(OffsetDateTime.now());

        TrackedFiles saved = trackedFileRepository.save(trackedFile);

        log.info("Created tracked file record for item {} in subscription {}",
                itemId, subscriptionId);

        return saved;
    }

    public void linkExistingSpreadsheetsToSubscription(Long subscriptionId, Integer userId) {
        OneDriveSubscription subscription = oneDriveSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        List<ClassSpreadsheet> existingSpreadsheets = classSpreadsheetRepository
                .findByUploadedBy_UserIdAndItemIdIsNotNull(userId); // Fixed method name

        List<TrackedFiles> trackedFiles = new ArrayList<>();

        for (ClassSpreadsheet spreadsheet : existingSpreadsheets) {
            // Check if already tracked to avoid duplicates
            if (!trackedFileRepository.existsBySubscriptionIdAndSpreadsheetId(
                    subscriptionId, spreadsheet.getId())) {

                TrackedFiles trackedFile = new TrackedFiles();
                trackedFile.setSubscription(subscription);
                trackedFile.setSpreadsheet(spreadsheet);
                trackedFile.setItemId(spreadsheet.getItemId());
                trackedFile.setFilePath(spreadsheet.getFolderName() + "/" + spreadsheet.getFileName());
                trackedFile.setSyncStatus(SyncStatus.SYNCED);
                trackedFile.setLastModifiedDateTime(OffsetDateTime.now());

                trackedFiles.add(trackedFile);
            }
        }

        List<TrackedFiles> saved = trackedFileRepository.saveAll(trackedFiles);
        log.info("Linked {} existing spreadsheets to subscription {}", saved.size(), subscriptionId);
    }

    public TrackedFiles createTrackedFileForNewUpload(Integer userId, String itemId,
                                                     String fileName, String folderName) {

        // Find active subscription for this user
        OneDriveSubscription subscription = oneDriveSubscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + userId));
        TeacherEntity teacher = teacherRepository.findByUserId(userId);
        if(teacher == null) {
            log.info("No teacher found for user {}", userId);
            throw new RuntimeException("No teacher found for user: " + userId);
        }
        // Create ClassSpreadsheet record first
        ClassSpreadsheet spreadsheet = new ClassSpreadsheet();
        spreadsheet.setUploadedBy(teacher);
        spreadsheet.setItemId(itemId);
        spreadsheet.setFileName(fileName);
        spreadsheet.setFolderName(folderName);
        // Set other required fields...

        ClassSpreadsheet savedSpreadsheet = classSpreadsheetRepository.save(spreadsheet);

        // Now create tracked file
        TrackedFiles trackedFile = new TrackedFiles();
        trackedFile.setSubscription(subscription);
        trackedFile.setSpreadsheet(savedSpreadsheet);
        trackedFile.setItemId(itemId);
        trackedFile.setFilePath(folderName + "/" + fileName);
        trackedFile.setSyncStatus(SyncStatus.PENDING);
        trackedFile.setLastModifiedDateTime(OffsetDateTime.now());

        TrackedFiles saved = trackedFileRepository.save(trackedFile);

        log.info("Created tracked file for new upload: {} (itemId: {})", fileName, itemId);

        return saved;
    }

    public void updateTrackedFileAfterSync(String itemId, SyncStatus status) {
        List<TrackedFiles> trackedFiles = trackedFileRepository.findByItemId(itemId);

        for (TrackedFiles trackedFile : trackedFiles) {
            trackedFile.setSyncStatus(status);
            trackedFile.setLastModifiedDateTime(OffsetDateTime.now());
            trackedFileRepository.save(trackedFile);
        }

        log.info("Updated sync status for {} tracked files with itemId: {}",
                trackedFiles.size(), itemId);
    }

    public List<TrackedFiles> getTrackedFilesForSubscription(Long dbSubscriptionId) {
        return trackedFileRepository.findBySubscription_Id(dbSubscriptionId);
    }

    public void removeTrackedFile(Long classSpreadsheetId) {
        List<TrackedFiles> trackedFiles = trackedFileRepository
                .findBySpreadsheetId(classSpreadsheetId);

        trackedFileRepository.deleteAll(trackedFiles);

        log.info("Removed {} tracked file records for spreadsheet {}",
                trackedFiles.size(), classSpreadsheetId);
    }
}
