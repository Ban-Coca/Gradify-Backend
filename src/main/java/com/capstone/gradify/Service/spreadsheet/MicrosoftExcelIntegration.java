package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Repository.user.UserTokenRepository;
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
            TeacherEntity teacher
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

    public Subscription createSubscriptionToFolder(int userId, String folderPath) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());
        String driveId = getUserDriveIds(userId);

        Subscription subscription = new Subscription();
        subscription.setChangeType("updated");
        subscription.setNotificationUrl("https://winning-informally-loon.ngrok-free.app/api/graph/notification"); // Replace with your actual webhook URL
        subscription.setResource(String.format("me/drive/root"));
        subscription.setExpirationDateTime(OffsetDateTime.now().plusDays(2)); // Set appropriate expiration

        return client.subscriptions().post(subscription);
    }

    public Subscription renewSubscription(String subscriptionId, int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        Subscription subscriptionUpdate = new Subscription();
        subscriptionUpdate.setExpirationDateTime(OffsetDateTime.now().plusDays(2));

        return client.subscriptions().bySubscriptionId(subscriptionId).patch(subscriptionUpdate);
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