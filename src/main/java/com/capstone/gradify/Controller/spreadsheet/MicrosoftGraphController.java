package com.capstone.gradify.Controller.spreadsheet;

import com.azure.core.annotation.Post;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Subscription;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class MicrosoftGraphController {
    private final MicrosoftExcelIntegration microsoftExcelIntegration;
    private final TeacherRepository teacherRepository;
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphController.class);

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
    public ResponseEntity<?> saveExcelData(@RequestParam int userId, @PathVariable String folderName, @PathVariable String fileName) {
        try {
            ExtractedExcelResponse excelData = microsoftExcelIntegration.getUsedRange(folderName, fileName, userId);
            if (excelData == null || excelData.getValues() == null || excelData.getValues().isEmpty()) {
                return ResponseEntity.badRequest().body("No data to save");
            }
            TeacherEntity teacher = teacherRepository.findByUserId(userId);
            if (teacher == null) {
                return ResponseEntity.badRequest().body("Teacher not found for user ID: " + userId);
            }
            microsoftExcelIntegration.saveExtractedExcelResponse(excelData, fileName, teacher);
            return ResponseEntity.ok("Data saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error saving Excel data: " + e.getMessage());
        }
    }

    @PostMapping("/notification/subscription")
    public ResponseEntity<?> createNotificationSubscription(@RequestParam int userId, @RequestParam String folderId){
        try {
            Subscription subscription = microsoftExcelIntegration.createSubscriptionToFolder(userId, folderId);
            return ResponseEntity.ok("Subscription created successfully with ID: " + subscription.getId());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating notification subscription: " + e.getMessage());
        }
    }

    @PostMapping("/notification")
    public ResponseEntity<?> handleNotification(@RequestParam(value = "validationToken", required = false) String validationToken, @RequestBody Map<String, Object> body){
        if (validationToken != null) {
            // This is a validation request from Microsoft Graph
            logger.info("Received validation token: {}", validationToken);
            return ResponseEntity.ok(validationToken);
        }

        if(body!=null){
            logger.info("Received body: {}", body);

            List<Map<String, Object>> notifications = (List<Map<String, Object>>) body.get("value");
            for (Map<String, Object> notification : notifications) {
                String resource = (String) notification.get("resource"); // e.g. "me/drive/items/{itemId}"
                String changeType = (String) notification.get("changeType");

                logger.info("Resource changed: {} ({})", resource, changeType);

                // Extract itemId from resource string
                String[] parts = resource.split("/");
                String itemId = parts[parts.length - 1];

                // Compare against stored fileId(s) in your DB
                if (itemId.equals("01WEENPZ2HG2P73NO7SFFZHPRBG7KOOSCN")) {
                    // Call your existing Excel processing logic
                    // e.g. getUsedRange("MyFolder", "grades.xlsx", userId);
                    logger.info("Tracked item changed: {}", itemId);
                }
            }

        }
        return ResponseEntity.ok("Notification received successfully");
    }
}
