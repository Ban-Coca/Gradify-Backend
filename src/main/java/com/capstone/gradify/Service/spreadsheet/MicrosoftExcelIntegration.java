package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Entity.enums.SubscriptionStatus;
import com.capstone.gradify.Entity.enums.SyncStatus;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.subscription.OneDriveSubscription;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.subscription.OneDriveSubscriptionRepository;
import com.capstone.gradify.Repository.subscription.TrackedFileRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.Service.subscription.TrackedFilesService;
import com.capstone.gradify.dto.response.ChangeNotification;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.capstone.gradify.dto.response.TokenResponse;
import com.capstone.gradify.mapper.DriveItemMapper;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.azure.core.credential.TokenCredential;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MicrosoftExcelIntegration {
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftExcelIntegration.class);
    private final ClassSpreadsheetService classSpreadsheetService;
    private final UserTokenRepository userTokenRepository;
    private final MicrosoftGraphTokenService microsoftGraphTokenService;
    private final DriveItemMapper driveItemMapper;
    private final WebClient webClient;
    private final TeacherRepository teacherRepository;
    private final ClassRepository classRepository;
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final TrackedFilesService trackedFileService;
    private final OneDriveSubscriptionRepository subscriptionRepository;
    private final TrackedFileRepository trackedFileRepository;
    @Value("${app.webhook.url}")
    private String webhookUrl;


    public String getUserDriveIds(int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        Drive drive = client.me().drive().get();

        // Get the root item to get its ID
        return drive != null ? drive.getId() : null;
    }
    public String getRootDriveItemId(int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItem driveItemId = client.drives().byDriveId(getUserDriveIds(userId)).root().get();

        return driveItemId != null ? driveItemId.getId() : null;
    }

    public List<DriveItemResponse> getRootFiles(int userId){
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItemCollectionResponse response = client.drives().byDriveId(getUserDriveIds(userId)).items().byDriveItemId(getRootDriveItemId(userId)).children().get(
                requestConfiguration -> {
                    if (requestConfiguration.queryParameters != null) {
                        requestConfiguration.queryParameters.select = new String []{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl"};
                    }
                }
        );
        return driveItemMapper.toDTO(response);
    }

    public List<DriveItemResponse> getFolderFiles(int userId, String folderId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItemCollectionResponse response = client.drives().byDriveId(getUserDriveIds(userId))
                .items().byDriveItemId(folderId).children().get(
                requestConfiguration -> {
                    if (requestConfiguration.queryParameters != null) {
                        requestConfiguration.queryParameters.select = new String[]{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl", "@microsoft.graph.downloadUrl"};
                    }
                }
        );
        return driveItemMapper.toDTO(response);
    }

    /**
     * Extracts the used range from an Excel file in OneDrive.
     *
     * @param folderName The folder path in OneDrive.
     * @param fileName   The name of the Excel file.
     * @param userId     The ID of the user whose token is used for authentication.
     * @return An ExtractedExcelResponse containing the used range data.
     */
    public ExtractedExcelResponse getUsedRange(String folderName, String fileName, int userId) {
        UserToken userToken = getUserToken(userId);
        String uriPath;

        if(folderName == null || folderName.equals("root")){
            uriPath = "/me/drive/root:/{file}:/workbook/worksheets/Sheet1/usedRange(valuesOnly=true)?$select=address,addressLocal,values";
            logger.debug("Using root folder for file: {}", fileName);
            return webClient.get()
                    .uri(uriPath, URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken.getAccessToken())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .bodyToMono(ExtractedExcelResponse.class)
                    .block();
        }

        // NO NEED FOR ELSE SINCE RETURN IN IF
        String encodedFolder = Arrays.stream(folderName.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        String encodedFile = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        logger.debug("Encoded Folder: {}, Encoded File: {}", encodedFolder, encodedFile);
        return webClient.get()
                .uri("/me/drive/root:/{folder}/{file}:/workbook/worksheets/Sheet1/usedRange(valuesOnly=true)?$select=address,addressLocal,values",
                        encodedFolder, encodedFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken.getAccessToken())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .bodyToMono(ExtractedExcelResponse.class)
                .block();
    }

    public void saveExtractedExcelResponse(
            ExtractedExcelResponse response,
            String fileName,
            TeacherEntity teacher,
            String folderName,
            String folderId,
            String itemId
    ) {
        ClassSpreadsheet existing = classSpreadsheetRepository.findByItemId(itemId).orElse(null);
        List<List<Object>> values = response.getValues();

        if(existing != null){
            logger.info("Spreadsheet '{}' already exists, updating folder from '{}' to '{}'",
                    fileName, existing.getFolderName(), folderName);
            throw new IllegalArgumentException("Spreadsheet already exist: " + existing.getFileName());
        }

        List<String> headers = values.get(0).stream()
                .map(Object::toString)
                .toList();


        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        List<Object> maxRow = values.get(1);
        List<String> maxRowValues = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            Object val = i < maxRow.size() ? maxRow.get(i) : null;
            if (val instanceof Number) {
                maxRowValues.add(val.toString());
                maxAssessmentValues.put(headers.get(i), ((Number) val).intValue());
            } else if (val != null) {
                try {
                    maxRowValues.add(val.toString());
                    maxAssessmentValues.put(headers.get(i), Integer.parseInt(val.toString()));
                } catch (NumberFormatException ignored) {}
            }
        }
        classSpreadsheetService.validateHeadersAndMaxValues(headers, maxRowValues);

        List<Map<String, String>> records = new ArrayList<>();
        for (int i = 2; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> record = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Object cell = j < row.size() ? row.get(j) : "";
                record.put(headers.get(j), cell != null ? cell.toString() : "");
            }
            records.add(record);
        }
        // THROWS ERROR IF INVALID DATA
        classSpreadsheetService.preValidateAllRecords(records, maxAssessmentValues);

        try {
            ClassEntity classEntity = new ClassEntity();
            classEntity.setTeacher(teacher);
            String className = cleanSpreadsheetName(fileName);
            String[] parts = className.split("-");

            if (parts.length >= 2) {
                classEntity.setClassName(parts[0].trim());
                classEntity.setSection(parts[1].trim());
            } else {
                classEntity.setClassName(className);
            }
            classEntity.setClassCode(classSpreadsheetService.generateRandomClassCode());
            Date now = new Date();
            classEntity.setCreatedAt(now);
            classEntity.setUpdatedAt(now);
            classEntity.setSemester(classSpreadsheetService.determineCurrentSemester());
            classEntity.setSchoolYear(classSpreadsheetService.determineCurrentSchoolYear());
            classRepository.save(classEntity);


            ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(
                    fileName,
                    itemId,
                    folderName,
                    folderId,
                    teacher,
                    records,
                    classEntity,
                    maxAssessmentValues
            );


            Set<StudentEntity> students = new HashSet<>();
            savedSpreadsheet.getGradeRecords().forEach(record -> {
                if (record.getStudent() != null) {
                    students.add(record.getStudent());
                }
            });
            classEntity.setStudents(students);
            classRepository.save(classEntity);

        } catch (Exception e) {
            logger.error("Failed to save extracted Excel response", e);
        }
    }

    public Subscription createSubscriptionToFolder(int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());
        String driveId = getUserDriveIds(userId);

        // Check if user already has an active subscription
        Optional<OneDriveSubscription> existingSubscription = subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        if (existingSubscription.isPresent()) {
            logger.info("User {} already has active subscription: {}", userId, existingSubscription.get().getSubscriptionId());
            return null; // or renew existing
        }

        Subscription subscription = new Subscription();
        subscription.setChangeType("updated");
        subscription.setNotificationUrl(webhookUrl + "/api/graph/notification");
        subscription.setResource(String.format("/drives/%s/root", driveId));
        subscription.setExpirationDateTime(OffsetDateTime.now().plusDays(2));
        subscription.setClientState("OneDriveSync-" + userId);

        try {
            Subscription createdSubscription = client.subscriptions().post(subscription);
            OneDriveSubscription dbSubscription = new OneDriveSubscription();
            dbSubscription.setUserId(userId);
            dbSubscription.setSubscriptionId(Objects.requireNonNull(createdSubscription).getId());
            dbSubscription.setStatus(SubscriptionStatus.ACTIVE);
            dbSubscription.setExpirationDateTime(createdSubscription.getExpirationDateTime());
            dbSubscription.setDriveId(driveId);
            dbSubscription.setResource(createdSubscription.getResource());
            dbSubscription.setClientState(createdSubscription.getClientState());
            dbSubscription.setCreatedAt(OffsetDateTime.now());

            OneDriveSubscription savedSubscription = subscriptionRepository.save(dbSubscription);

            // Link existing spreadsheets to this subscription
            trackedFileService.linkExistingSpreadsheetsToSubscription(
                    savedSubscription.getId(), userId);

            logger.info("Created subscription {} for user {} and linked existing files",
                    createdSubscription.getId(), userId);

            return createdSubscription;
        } catch (Exception e) {
            logger.error("Error creating subscription for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    public Subscription renewSubscription(String subscriptionId, int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        // Update expiration in Graph API
        Subscription subscriptionUpdate = new Subscription();
        subscriptionUpdate.setExpirationDateTime(OffsetDateTime.now().plusDays(2));

        Subscription renewed = client.subscriptions().bySubscriptionId(subscriptionId).patch(subscriptionUpdate);

        // Update expiration in database
        subscriptionRepository.findBySubscriptionId(subscriptionId)
                .ifPresent(dbSub -> {
                    dbSub.setExpirationDateTime(Objects.requireNonNull(renewed).getExpirationDateTime());
                    subscriptionRepository.save(dbSub);
                });

        return renewed;
    }

    @Async
    public void processNotificationAsync(ChangeNotification notification) {
        try {
            logger.info("Processing notification: {} for resource: {}",
                    notification.getChangeType(), notification.getResource());

            // Find subscription in database
            OneDriveSubscription subscription = subscriptionRepository
                    .findBySubscriptionId(notification.getSubscriptionId())
                    .orElseThrow(() -> new RuntimeException("Subscription not found: " + notification.getSubscriptionId()));

            // Find the tracked file associated with this notification
            List<TrackedFiles> trackedFile = trackedFileService.getTrackedFilesForSubscription(subscription.getId());
            for(TrackedFiles tf : trackedFile){
                trackedFile.forEach(trackedfiles -> processModifiedFile(tf, subscription.getUserId()));
            }

        } catch (Exception e) {
            logger.error("Error processing notification: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void processModifiedFile(TrackedFiles trackedFile, int userId) {
        try {
            ClassSpreadsheet spreadsheet = classSpreadsheetRepository
                    .findById(trackedFile.getSpreadsheet().getId())
                    .orElseThrow(() -> new RuntimeException("Spreadsheet not found"));

            UserToken userToken = getUserToken(userId);
            GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());
            String driveId = getUserDriveIds(userId);

            DriveItem changedItem = client.drives().byDriveId(driveId)
                    .items().byDriveItemId(trackedFile.getItemId()).get();

            processChangedSpreadsheet(userId, spreadsheet, changedItem, "updated");

        } catch (Exception e) {
            logger.error("Error processing modified file {}: {}", trackedFile.getFilePath(), e.getMessage());
        }
    }

    private void processSpreadsheetChange(int userId, ClassSpreadsheet spreadsheet, ChangeNotification notification) {
        try{
            UserToken userToken = getUserToken(userId);
            GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());
            String driveId = getUserDriveIds(userId);

            DriveItem changedItem = client.drives().byDriveId(driveId).items().byDriveItemId(spreadsheet.getItemId()).get();

            processChangedSpreadsheet(userId, spreadsheet, changedItem, notification.getChangeType());
        } catch (Exception e) {
            logger.error("Error processing spreadsheet change for user {}: {}", userId, e.getMessage(), e);
        }
    }

    // Scheduled full sync (backup method)
    @Scheduled(fixedRate = 3600000) // Every hour
    public void scheduledBackupSync() {
        logger.info("Starting backup sync for files that may have missed webhook notifications");

        // Only sync files that haven't been updated recently via webhooks
        List<OneDriveSubscription> activeSubscriptions = subscriptionRepository
                .findByStatus(SubscriptionStatus.ACTIVE);

        for (OneDriveSubscription subscription : activeSubscriptions) {
            List<TrackedFiles> trackedFile = trackedFileService.getTrackedFilesForSubscription(subscription.getId());
            for(TrackedFiles tf : trackedFile){
                trackedFile.forEach(trackedfiles -> processModifiedFile(tf, subscription.getUserId()));
            }
        }

        for (OneDriveSubscription subscription : activeSubscriptions) {
            try {
                syncSubscriptionFiles(subscription);
            } catch (Exception e) {
                logger.error("Error in backup sync for subscription {}: {}",
                        subscription.getSubscriptionId(), e.getMessage());
            }
        }
    }
    @Transactional
    public void triggerManualUpdate(int userId, ClassSpreadsheet spreadsheet){
        if(spreadsheet == null){
            throw new RuntimeException("Spreadsheet does not exists.");
        }

        processUpdatedSpreadsheet(userId, spreadsheet, null);

    }
    private void syncSubscriptionFiles(OneDriveSubscription subscription) throws Exception {
        List<TrackedFiles> trackedFiles = trackedFileService
                .getTrackedFilesForSubscription(subscription.getId());

        // Only sync files that haven't been updated in the last hour (missed webhooks)
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);

        List<TrackedFiles> filesToSync = trackedFiles.stream()
                .filter(tf -> tf.getLastModifiedDateTime().isBefore(oneHourAgo))
                .collect(Collectors.toList());

        if (!filesToSync.isEmpty()) {
            logger.info("Backup syncing {} files for user {}", filesToSync.size(), subscription.getUserId());
            syncSpecificFiles(subscription.getUserId(), filesToSync);
        }
    }

    private void syncSpecificFiles(int userId, List<TrackedFiles> filesToSync) throws Exception {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(
                userToken.getAccessToken(),
                userToken.getExpiresAt()
        );
        String driveId = getUserDriveIds(userId);

        for (TrackedFiles trackedFile : filesToSync) {
            try {
                ClassSpreadsheet spreadsheet = trackedFile.getSpreadsheet();

                // Get current file info from OneDrive
                DriveItem fileItem = client.drives().byDriveId(driveId)
                        .items().byDriveItemId(trackedFile.getItemId()).get();

                if (fileItem == null) {
                    logger.warn("File not found in OneDrive: {} (itemId: {})",
                            trackedFile.getFilePath(), trackedFile.getItemId());
                    continue;
                }

                // Check if file was actually modified since last sync
                OffsetDateTime fileLastModified = fileItem.getLastModifiedDateTime();
                OffsetDateTime trackedLastModified = trackedFile.getLastModifiedDateTime();

                if (fileLastModified != null && (trackedLastModified == null ||
                        fileLastModified.isAfter(trackedLastModified))) {

                    logger.info("File '{}' was modified, syncing changes for user {}",
                            spreadsheet.getFileName(), userId);

                    // Process the changed file
                    processChangedSpreadsheet(userId, spreadsheet, fileItem, "updated");

                    // Update tracked file timestamp
                    trackedFile.setLastModifiedDateTime(fileLastModified);
                    trackedFile.setSyncStatus(SyncStatus.SYNCED);
                    trackedFileRepository.save(trackedFile);

                } else {
                    logger.debug("File '{}' unchanged for user {}", spreadsheet.getFileName(), userId);
                }

            } catch (Exception e) {
                logger.error("Error syncing file '{}' for user {}: {}",
                        trackedFile.getFilePath(), userId, e.getMessage());

                // Mark as failed for monitoring
                trackedFile.setSyncStatus(SyncStatus.FAILED);
                trackedFileRepository.save(trackedFile);
            }
        }
    }

    private void processChangedSpreadsheet(int userId, ClassSpreadsheet spreadsheet, DriveItem item, String changeType) {
        try {
            logger.info("Processing {} spreadsheet: '{}' (class: {}) for user {}",
                    changeType, spreadsheet.getFileName(), spreadsheet.getClassName(), userId);

            // Your business logic here
            switch (changeType.toLowerCase()) {
                case "updated":
                    handleSpreadsheetUpdated(userId, spreadsheet, item);
                    break;
                case "deleted":
                    logger.warn("Spreadsheet '{}' has been deleted, please re-upload and re-link.",
                            spreadsheet.getFileName());
                    break;
                default:
                    logger.warn("Unknown change type: {}", changeType);
            }

        } catch (Exception e) {
            logger.error("Error processing changed spreadsheet: {}", e.getMessage(), e);
        }
    }

    private void handleSpreadsheetUpdated(int userId, ClassSpreadsheet spreadsheet, DriveItem item) {
        logger.info("Spreadsheet updated: '{}' for class '{}' by user {}",
                spreadsheet.getFileName(), spreadsheet.getClassName(), userId);
        processUpdatedSpreadsheet(userId, spreadsheet, item);
    }

    private void processUpdatedSpreadsheet(int userId, ClassSpreadsheet spreadsheet, DriveItem item) {
        try{
            logger.info("Processing updated spreadsheet data for: '{}' (class: '{}')", spreadsheet.getFileName(), spreadsheet.getClassName());

            ExtractedExcelResponse response = getUsedRange(
                    spreadsheet.getFolderName(),
                    spreadsheet.getFileName(),
                    userId
            );

            if (response != null && response.getValues() != null) {
                // Update the existing spreadsheet record instead of creating new one
                updateExistingSpreadsheetData(response, spreadsheet, userId);
            } else {
                logger.warn("No data received for spreadsheet: '{}'", spreadsheet.getFileName());
            }

        } catch (Exception e) {
            logger.error("Error processing updated spreadsheet '{}' for user {}: {}",
                    spreadsheet.getFileName(), userId, e.getMessage(), e);
        }
    }

    private void updateExistingSpreadsheetData(ExtractedExcelResponse response, ClassSpreadsheet spreadsheet, int userId) {
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() < 3) {
            logger.warn("Not enough data in Excel response for: '{}'", spreadsheet.getFileName());
            return;
        }

        List<String> headers = values.get(0).stream()
                .map(Object::toString)
                .toList();

        // Extract max assessment values from row 2
        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        List<Object> maxRow = values.get(1);
        for (int i = 0; i < headers.size(); i++) {
            Object val = i < maxRow.size() ? maxRow.get(i) : null;
            if (val instanceof Number) {
                maxAssessmentValues.put(headers.get(i), ((Number) val).intValue());
            } else if (val != null) {
                try {
                    maxAssessmentValues.put(headers.get(i), Integer.parseInt(val.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Convert data rows to records
        List<Map<String, String>> records = new ArrayList<>();
        for (int i = 2; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> record = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Object cell = j < row.size() ? row.get(j) : "";
                record.put(headers.get(j), cell != null ? cell.toString() : "");
            }
            records.add(record);
        }

        try {
            // Update the existing spreadsheet record
            logger.info("Updating existing spreadsheet record for: '{}'", spreadsheet.getFileName());

            // Update assessment max values
            spreadsheet.setAssessmentMaxValues(maxAssessmentValues);

            // Update the spreadsheet using your existing service method
            ClassSpreadsheet updatedSpreadsheet = classSpreadsheetService.updateSpreadsheet(
                    spreadsheet,
                    records,
                    maxAssessmentValues
            );

            // Update class entity's students if needed
            if (updatedSpreadsheet.getClassEntity() != null) {
                Set<StudentEntity> students = new HashSet<>();
                updatedSpreadsheet.getGradeRecords().forEach(record -> {
                    if (record.getStudent() != null) {
                        students.add(record.getStudent());
                    }
                });

                ClassEntity classEntity = updatedSpreadsheet.getClassEntity();
                classEntity.setStudents(students);
                classEntity.setUpdatedAt(new Date());
                classRepository.save(classEntity);
            }

            logger.info("Successfully updated spreadsheet data for: '{}'", spreadsheet.getFileName());

        } catch (Exception e) {
            logger.error("Failed to update spreadsheet data for: '{}': {}", spreadsheet.getFileName(), e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 86400000) // Daily check
    public void renewExpiringSubscriptions() {

    }
    private void renewSubscriptionIfNeeded(int userId, String subscriptionId) throws Exception {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(
                userToken.getAccessToken(),
                userToken.getExpiresAt()
        );

        // Get subscription details
        Subscription subscription = client.subscriptions().bySubscriptionId(subscriptionId).get();

        // Check if expiring within 24 hours
        if (Objects.requireNonNull(Objects.requireNonNull(subscription).getExpirationDateTime()).isBefore(OffsetDateTime.now().plusHours(24))) {
            // Renew subscription
            subscription.setExpirationDateTime(OffsetDateTime.now().plusDays(2));
            client.subscriptions().bySubscriptionId(subscriptionId).patch(subscription);

            logger.info("Renewed subscription {} for user {}", subscriptionId, userId);
        }
    }

    private boolean wasFileRecentlyModified(TrackedFiles trackedFile) {
        try {
            // Get the current file's last modified time from OneDrive
            UserToken userToken = getUserToken(trackedFile.getSubscription().getUserId());
            GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

            String driveId = trackedFile.getSubscription().getDriveId();
            DriveItem currentItem = client.drives().byDriveId(driveId)
                    .items().byDriveItemId(trackedFile.getItemId()).get();

            if (currentItem == null || currentItem.getLastModifiedDateTime() == null) {
                return false;
            }

            OffsetDateTime oneDriveModified = currentItem.getLastModifiedDateTime();
            OffsetDateTime trackedModified = trackedFile.getLastModifiedDateTime();

            // Consider file recently modified if OneDrive timestamp is newer than our tracked timestamp
            return oneDriveModified.isAfter(trackedModified);

        } catch (Exception e) {
            logger.warn("Error checking if file {} was recently modified: {}",
                    trackedFile.getFilePath(), e.getMessage());
            return false; // If we can't check, assume not modified to avoid unnecessary processing
        }
    }

    public UserToken getUserToken(int userId) {
        UserToken userToken = userTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not authenticated with Microsoft Graph"));

        // Check if token is expired and refresh if needed
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) { // Add 5min buffer
            if (userToken.getRefreshToken() == null || userToken.getRefreshToken().isEmpty()) {
                throw new RuntimeException("Token expired and no refresh token available, please re-authenticate");
            }

            try {
                // Use the correct refresh method
                TokenResponse refreshed = microsoftGraphTokenService.refreshAccessToken(userToken.getRefreshToken());

                // Update the token
                userToken.setAccessToken(refreshed.getAccessToken());
                if (refreshed.getRefreshToken() != null && !refreshed.getRefreshToken().isEmpty()) {
                    userToken.setRefreshToken(refreshed.getRefreshToken());
                }
                userToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshed.getExpiresIn()));
                userTokenRepository.save(userToken);

                // Return the refreshed token - don't throw exception!
                return userToken;

            } catch (Exception e) {
                throw new RuntimeException("Failed to refresh token, please re-authenticate", e);
            }
        }

        return userToken;
    }

    public GraphServiceClient createGraphClient(String accessToken, LocalDateTime expiresAt) {
        TokenCredential credential = new TokenCredential() {
            @Override
            public Mono<AccessToken> getToken(TokenRequestContext request) {
                return Mono.just(new AccessToken(
                        accessToken,
                        expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                ));
            }
        };
        return new GraphServiceClient(credential);
    }

    private String cleanSpreadsheetName(String name) {
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }
}