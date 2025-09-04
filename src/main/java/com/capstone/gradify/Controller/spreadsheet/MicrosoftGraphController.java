package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.enums.SubscriptionStatus;
import com.capstone.gradify.Entity.subscription.OneDriveSubscription;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.subscription.OneDriveSubscriptionRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import com.capstone.gradify.Service.subscription.TrackedFilesService;
import com.capstone.gradify.dto.ChangeNotification;
import com.capstone.gradify.dto.NotificationPayload;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class MicrosoftGraphController {
    private final MicrosoftExcelIntegration microsoftExcelIntegration;
    private final TeacherRepository teacherRepository;
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphController.class);
    private final OneDriveSubscriptionRepository oneDriveSubscriptionRepository;
    private final TrackedFilesService trackedFilesService;
    private final ObjectMapper objectMapper;

    @GetMapping("/drive/root")
    public ResponseEntity<?> getUserRootFiles(@RequestParam int userId) {
        try {
            List<DriveItemResponse> rootFiles = microsoftExcelIntegration.getRootFiles(userId);
            return ResponseEntity.ok(rootFiles);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving drive files: " + e.getMessage());
        }
    }

    @GetMapping("/drive/folder/{folderId}/files")
    public ResponseEntity<?> getFolderFiles(@RequestParam int userId, @PathVariable String folderId) {
        try {
            List<DriveItemResponse> folderFiles = microsoftExcelIntegration.getFolderFiles(userId, folderId);
            return ResponseEntity.ok(folderFiles);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving drive files");
        }
    }

    @GetMapping("/extract/{folderName}/{fileName}")
    public ResponseEntity<?> extractExcelData(@RequestParam int userId, @PathVariable String folderName, @PathVariable String fileName) {
        try {
            ExtractedExcelResponse excelData = microsoftExcelIntegration.getUsedRange(folderName, fileName, userId);
            return ResponseEntity.ok(excelData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error extracting Excel data: " + e.getMessage());
        }
    }

    @PostMapping("/save/{folderName}/{fileName}")
    public ResponseEntity<?> saveExcelData(@RequestParam int userId, @RequestParam String folderId, @RequestParam String itemId, @PathVariable String folderName, @PathVariable String fileName) {
        ExtractedExcelResponse excelData = microsoftExcelIntegration.getUsedRange(folderName, fileName, userId);
        if (excelData == null || excelData.getValues() == null || excelData.getValues().isEmpty()) {
            return ResponseEntity.badRequest().body("No data to save");
        }
        TeacherEntity teacher = teacherRepository.findByUserId(userId);
        if (teacher == null) {
            return ResponseEntity.badRequest().body("Teacher not found for user ID: " + userId);
        }
        microsoftExcelIntegration.saveExtractedExcelResponse(excelData, fileName, teacher, folderName, folderId, itemId);
        return ResponseEntity.ok("Data saved successfully");
    }

    @PostMapping("/notification/subscribe/{userId}")
    public ResponseEntity<?> createNotificationSubscription(@PathVariable int userId) {
        try {
            // Check if user already has active subscription
            Optional<OneDriveSubscription> existing = oneDriveSubscriptionRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

            if (existing.isPresent()) {
                return ResponseEntity.badRequest()
                        .body("User already has an active subscription: " + existing.get().getSubscriptionId());
            }

            Subscription graphSubscription = microsoftExcelIntegration.createSubscriptionToFolder(userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Subscription created successfully",
                    "subscriptionId", Objects.requireNonNull(graphSubscription.getId()),
                    "expirationDateTime", Objects.requireNonNull(graphSubscription.getExpirationDateTime())
            ));

        } catch (Exception e) {
            logger.error("Failed to create subscription for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create subscription: " + e.getMessage());
        }
    }

    @GetMapping("/subscription/status")
    public ResponseEntity<?> getSubscriptionStatus(@RequestParam Integer userId) {
        try {
            Optional<OneDriveSubscription> subscription = oneDriveSubscriptionRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

            if (subscription.isPresent()) {
                OneDriveSubscription sub = subscription.get();
                return ResponseEntity.ok(Map.of(
                        "hasActiveSubscription", true,
                        "subscriptionId", sub.getSubscriptionId(),
                        "expirationDateTime", sub.getExpirationDateTime(),
                        "trackedFilesCount", sub.getTrackedFiles().size()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "hasActiveSubscription", false
                ));
            }

        } catch (Exception e) {
            logger.error("Failed to get subscription status for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get subscription status: " + e.getMessage());
        }
    }

    @PostMapping("/subscription/renew")
    public ResponseEntity<?> renewSubscription(@RequestParam Integer userId) {
        try {
            OneDriveSubscription dbSubscription = oneDriveSubscriptionRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + userId));

            Subscription renewed = microsoftExcelIntegration.renewSubscription(
                    dbSubscription.getSubscriptionId(), userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Subscription renewed successfully",
                    "subscriptionId", Objects.requireNonNull(renewed.getId()),
                    "newExpirationDateTime", Objects.requireNonNull(renewed.getExpirationDateTime())
            ));

        } catch (Exception e) {
            logger.error("Failed to renew subscription for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to renew subscription: " + e.getMessage());
        }
    }

    @DeleteMapping("/subscription")
    public ResponseEntity<?> cancelSubscription(@RequestParam Integer userId) {
        try {
            OneDriveSubscription dbSubscription = oneDriveSubscriptionRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + userId));

            // Cancel in Microsoft Graph
            UserToken userToken = microsoftExcelIntegration.getUserToken(userId);
            GraphServiceClient client = microsoftExcelIntegration.createGraphClient(
                    userToken.getAccessToken(), userToken.getExpiresAt());

            client.subscriptions().bySubscriptionId(dbSubscription.getSubscriptionId()).delete();

            // Update status in database
            dbSubscription.setStatus(SubscriptionStatus.CANCELLED);
            oneDriveSubscriptionRepository.save(dbSubscription);

            logger.info("Cancelled subscription {} for user {}",
                    dbSubscription.getSubscriptionId(), userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Subscription cancelled successfully"
            ));

        } catch (Exception e) {
            logger.error("Failed to cancel subscription for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to cancel subscription: " + e.getMessage());
        }
    }

    @GetMapping("/tracked-files")
    public ResponseEntity<?> getTrackedFiles(@RequestParam Integer userId) {
        try {
            OneDriveSubscription subscription = oneDriveSubscriptionRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + userId));

            List<TrackedFiles> trackedFiles = trackedFilesService
                    .getTrackedFilesForSubscription(subscription.getId());

            List<Map<String, Object>> fileInfo = trackedFiles.stream()
                    .map(tf -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("fileName", tf.getSpreadsheet().getFileName());
                        map.put("filePath", tf.getFilePath());
                        map.put("syncStatus", tf.getSyncStatus());
                        map.put("lastModified", tf.getLastModifiedDateTime());
                        map.put("itemId", tf.getItemId());
                        return map;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "trackedFiles", fileInfo,
                    "totalCount", fileInfo.size()
            ));

        } catch (Exception e) {
            logger.error("Failed to get tracked files for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get tracked files: " + e.getMessage());
        }
    }

    @PostMapping("/notification")
    public ResponseEntity<String> handleNotification(
            HttpServletRequest request,
            @RequestParam(required = false) String validationToken) {

        if (validationToken != null) {
            logger.info("POST validation - token: {}", validationToken);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(validationToken);
        }

        try {
            StringBuilder buffer = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
            }

            String jsonPayload = buffer.toString();
            logger.info("=== CHANGE NOTIFICATION RECEIVED ===");
            logger.info("Raw JSON: {}", jsonPayload);

            // Use the injected, properly configured ObjectMapper
            NotificationPayload payload = objectMapper.readValue(jsonPayload, NotificationPayload.class);

            logger.info("Number of notifications: {}", payload.getValue().size());

            for (ChangeNotification notification : payload.getValue()) {
                logger.info("--- Individual Notification ---");
                logger.info("Subscription ID: {}", notification.getSubscriptionId());
                logger.info("Change Type: {}", notification.getChangeType());
                logger.info("Resource: {}", notification.getResource());
                logger.info("Expiration: {}", notification.getSubscriptionExpirationDateTime());

                microsoftExcelIntegration.processNotificationAsync(notification);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            logger.error("Error processing notification", e);
            return ResponseEntity.ok("Error processed"); // Still return 200 to prevent retries
        }
    }
}
