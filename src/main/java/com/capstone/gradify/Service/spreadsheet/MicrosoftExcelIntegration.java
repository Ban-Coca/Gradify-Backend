package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.dto.ChangeNotification;
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
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    @Value("${app.webhook.url}")
    private String webhookUrl;

    private final Map<Integer, String> userSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> fileLastModifiedTimes = new ConcurrentHashMap<>();

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
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() < 3) {
            throw new IllegalArgumentException("Not enough data in Excel response");
        }


        List<String> headers = values.get(0).stream()
                .map(Object::toString)
                .toList();


        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        List<Object> maxRow = values.get(1);
        for (int i = 0; i < headers.size(); i++) {
            Object val = i < maxRow.size() ? maxRow.get(i) : null;
            if (val instanceof Number) {
                maxAssessmentValues.put(headers.get(i), ((Number) val).intValue());
            } else if (val != null) {
                try {
                    maxAssessmentValues.put(headers.get(i), Integer.parseInt(val.toString()));
                } catch (NumberFormatException ignored) {}
            }
        }


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

        Subscription subscription = new Subscription();
        subscription.setChangeType("updated");
        subscription.setNotificationUrl(webhookUrl + "/api/graph/notification"); // Replace with your actual webhook URL
        subscription.setResource(String.format("/drives/%s/root", driveId));
        subscription.setExpirationDateTime(OffsetDateTime.now().plusDays(2)); // Set appropriate expiration
        subscription.setClientState("OneDriveSync-" + userId);

        try {
            Subscription createdSubscription = client.subscriptions().post(subscription);
            userSubscriptions.put(userId, Objects.requireNonNull(createdSubscription).getId());
            logger.info("Created subscription {} for user {}", createdSubscription.getId(), userId);
            return createdSubscription;
        } catch (Exception e) {
            logger.error("Error creating subscription for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    public Subscription renewSubscription(String subscriptionId, int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        Subscription subscriptionUpdate = new Subscription();
        subscriptionUpdate.setExpirationDateTime(OffsetDateTime.now().plusDays(2));

        return client.subscriptions().bySubscriptionId(subscriptionId).patch(subscriptionUpdate);
    }

    @Async
    public void processNotificationAsync(ChangeNotification notification) {
        try{
            logger.info("Processing notification: {} for resource: {}", notification.getChangeType(), notification.getResource());
            String clientState = notification.getClientState();

            if(clientState == null || !clientState.startsWith("OneDriveSync-")) {
                logger.warn("Invalid client state: {}", clientState);
                return;
            }

            int userId = Integer.parseInt(clientState.substring("OneDriveSync-".length()));

            String changedItemId = notification.getResourceData().getId();
            Optional<ClassSpreadsheet> spreadsheet = classSpreadsheetRepository.findByItemId(changedItemId);
            if(spreadsheet.isPresent() && spreadsheet.get().getUploadedBy().getUserId() == userId){
                logger.info("Change detected in tracked spreadsheet: {} ({})", spreadsheet.get().getFileName(), notification.getChangeType());
                processSpreadsheetChange(userId, spreadsheet.get(), notification);
            } else {
                logger.debug("Change not in tracked files for user {}: {}", userId, changedItemId);
            }
        } catch (Exception e){
            logger.error("Error processing notification: {}", e.getMessage(), e);
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
    public void scheduledFullSync() {
        logger.info("Starting scheduled full sync for all tracked spreadsheets");

        // Get all spreadsheets with itemId (tracked files)
        List<ClassSpreadsheet> trackedSpreadsheets = classSpreadsheetRepository.findByItemIdIsNotNull();

        // Group by user for efficient processing
        Map<Integer, List<ClassSpreadsheet>> spreadsheetsByUser = trackedSpreadsheets.stream().collect(Collectors.groupingBy(s -> s.getUploadedBy().getUserId()));

        for (Map.Entry<Integer, List<ClassSpreadsheet>> entry : spreadsheetsByUser.entrySet()) {
            Integer userId = entry.getKey();
            List<ClassSpreadsheet> userSpreadsheets = entry.getValue();

            try {
                syncUserSpreadsheets(userId, userSpreadsheets);
            } catch (Exception e) {
                logger.error("Error in scheduled sync for user {}: {}", userId, e.getMessage());
            }
        }
    }

    public void syncUserSpreadsheets(int userId) throws Exception {
        List<ClassSpreadsheet> userSpreadsheets = classSpreadsheetRepository.findByUploadedBy_UserIdAndItemIdIsNotNull(userId);

        if(userSpreadsheets.isEmpty()){
            logger.info("No tracked spreadsheets for user {}", userId);
            return;
        }
        syncUserSpreadsheets(userId, userSpreadsheets);
    }

    public void syncUserSpreadsheets(int userId, List<ClassSpreadsheet> spreadsheets) throws Exception {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(
                userToken.getAccessToken(),
                userToken.getExpiresAt()
        );
        String driveId = getUserDriveIds(userId);

        for(ClassSpreadsheet spreadsheet : spreadsheets){
            try{
                DriveItem fileItem = client.drives().byDriveId(driveId).items().byDriveItemId(spreadsheet.getItemId()).get();

                String fileKey = userId + ":" + spreadsheet.getItemId();
                OffsetDateTime lastModified = Objects.requireNonNull(fileItem).getLastModifiedDateTime();
                OffsetDateTime storedLastModified = fileLastModifiedTimes.get(fileKey);

                if (storedLastModified == null || lastModified.isAfter(storedLastModified)) {
                    logger.info("Spreadsheet '{}' has been modified, processing changes for user {}",
                            spreadsheet.getFileName(), userId);

                    processChangedSpreadsheet(userId, spreadsheet, fileItem, "updated");

                    // Store the new last modified time
                    fileLastModifiedTimes.put(fileKey, lastModified);
                } else {
                    logger.debug("Spreadsheet '{}' unchanged for user {}", spreadsheet.getFileName(), userId);
                }
            } catch (Exception e){
                logger.error("Error syncing spreadsheet '{}' for user {}: {}",
                        spreadsheet.getFileName(), userId, e.getMessage());
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
    public void registerSpreadsheetForMonitoring(ClassSpreadsheet spreadsheet, String itemId) {
        spreadsheet.setItemId(itemId);
        classSpreadsheetRepository.save(spreadsheet);

        logger.info("Registered spreadsheet '{}' (class: '{}') for monitoring with itemId: {}",
                spreadsheet.getFileName(), spreadsheet.getClassName(), itemId);
    }

    public void unregisterSpreadsheetFromMonitoring(Long spreadsheetId) {
        Optional<ClassSpreadsheet> spreadsheet = classSpreadsheetRepository.findById(spreadsheetId);
        if (spreadsheet.isPresent()) {
            ClassSpreadsheet sheet = spreadsheet.get();
            String fileKey = sheet.getUploadedBy().getUserId() + ":" + sheet.getItemId();

            sheet.setItemId(null);
            classSpreadsheetRepository.save(sheet);
            fileLastModifiedTimes.remove(fileKey);

            logger.info("Unregistered spreadsheet '{}' from monitoring", sheet.getFileName());
        }
    }

    @Scheduled(fixedRate = 86400000) // Daily check
    public void renewExpiringSubscriptions() {
        for (Map.Entry<Integer, String> entry : userSubscriptions.entrySet()) {
            int userId = entry.getKey();
            String subscriptionId = entry.getValue();

            try {
                renewSubscriptionIfNeeded(userId, subscriptionId);
            } catch (Exception e) {
                logger.error("Error renewing subscription for user {}: {}", userId, e.getMessage());
            }
        }
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

    private UserToken getUserToken(int userId) {
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

    private GraphServiceClient createGraphClient(String accessToken, LocalDateTime expiresAt) {
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