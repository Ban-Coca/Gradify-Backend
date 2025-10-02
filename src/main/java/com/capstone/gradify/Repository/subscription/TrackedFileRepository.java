package com.capstone.gradify.Repository.subscription;

import com.capstone.gradify.Entity.enums.SyncStatus;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackedFileRepository extends JpaRepository<TrackedFiles, Long> {

    List<TrackedFiles> findBySubscription_Id(Long subscriptionId);

    List<TrackedFiles> findByItemId(String itemId);

    List<TrackedFiles> findBySpreadsheetId(Long classSpreadsheetId);

    List<TrackedFiles> findBySubscriptionIdAndSyncStatus(Long subscriptionId, SyncStatus status);
    List<TrackedFiles> findBySpreadsheet(ClassSpreadsheet spreadsheet);
    List<TrackedFiles> findBySpreadsheetIn(List<ClassSpreadsheet> spreadsheets);
    boolean existsBySubscriptionIdAndSpreadsheetId(Long subscriptionId, Long classSpreadsheetId);

    void deleteBySpreadsheetId(Long classSpreadsheetId);
}
