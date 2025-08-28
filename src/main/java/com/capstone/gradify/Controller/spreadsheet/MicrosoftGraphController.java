package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import com.capstone.gradify.dto.ChangeNotification;
import com.capstone.gradify.dto.NotificationPayload;
import com.capstone.gradify.dto.request.RegisterSpreadsheetRequest;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.graph.models.Subscription;
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
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class MicrosoftGraphController {
    private final MicrosoftExcelIntegration microsoftExcelIntegration;
    private final TeacherRepository teacherRepository;
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphController.class);
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final ObjectMapper objectMapper;
    @GetMapping("/drive/root")
    public ResponseEntity<?> getUserRootFiles(@RequestParam int userId){
        try{
            List<DriveItemResponse> rootFiles = microsoftExcelIntegration.getRootFiles(userId);
            return ResponseEntity.ok(rootFiles);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving drive files: "+ e.getMessage());
        }
    }

    @GetMapping("/drive/folder/{folderId}/files")
    public ResponseEntity<?> getFolderFiles(@RequestParam int userId, @PathVariable String folderId){
        try{
            List<DriveItemResponse> folderFiles = microsoftExcelIntegration.getFolderFiles(userId, folderId);
            return ResponseEntity.ok(folderFiles);
        }catch (Exception e){
            return ResponseEntity.status(500).body("Error retrieving drive files");
        }
    }

    @GetMapping("/extract/{folderName}/{fileName}")
    public ResponseEntity<?> extractExcelData(@RequestParam int userId, @PathVariable String folderName, @PathVariable String fileName) {
        try{
            ExtractedExcelResponse excelData = microsoftExcelIntegration.getUsedRange(folderName, fileName, userId);
            return ResponseEntity.ok(excelData);
        }catch (Exception e){
            return ResponseEntity.status(500).body("Error extracting Excel data: " + e.getMessage());
        }
    }

    @PostMapping("/save/{folderName}/{fileName}")
    public ResponseEntity<?> saveExcelData(@RequestParam int userId, @RequestParam String folderId, @RequestParam String itemId,  @PathVariable String folderName, @PathVariable String fileName) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error saving Excel data: " + e.getMessage());
        }
    }

    @PostMapping("/notification/subscribe/{userId}")
    public ResponseEntity<?> createNotificationSubscription(@PathVariable int userId){
        try {
            Subscription subscription = microsoftExcelIntegration.createSubscriptionToFolder(userId);
            return ResponseEntity.ok(Map.of(
                    "subscriptionId", Objects.requireNonNull(subscription.getId()),
                    "expiresAt", Objects.requireNonNull(subscription.getExpirationDateTime())
            ));
        } catch (Exception e) {
            logger.error("Error creating subscription for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sync/{userId}")
    public ResponseEntity<?> manualSync(@PathVariable int userId) {
        try {
            microsoftExcelIntegration.syncUserSpreadsheets(userId);
            return ResponseEntity.ok(Map.of("status", "Spreadsheet sync completed"));
        } catch (Exception e) {
            logger.error("Error in manual sync for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/register-spreadsheet")
    public ResponseEntity<?> registerSpreadsheet(@RequestBody RegisterSpreadsheetRequest request) {
        try {
            Optional<ClassSpreadsheet> spreadsheet = classSpreadsheetRepository.findById(request.getSpreadsheetId());
            if(spreadsheet.isEmpty()) {
                return ResponseEntity.badRequest().body("Spreadsheet not found");
            } else {
                microsoftExcelIntegration.registerSpreadsheetForMonitoring(spreadsheet.get(), request.getItemId());
            }

            return ResponseEntity.ok(Map.of("status", "Spreadsheet registered for monitoring"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/unregister-spreadsheet/{spreadsheetId}")
    public ResponseEntity<?> unregisterSpreadsheet(@PathVariable Long spreadsheetId) {
        try {
            microsoftExcelIntegration.unregisterSpreadsheetFromMonitoring(spreadsheetId);
            return ResponseEntity.ok(Map.of("status", "Spreadsheet unregistered from monitoring"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
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
