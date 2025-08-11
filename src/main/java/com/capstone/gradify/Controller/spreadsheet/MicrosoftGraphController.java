package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class MicrosoftGraphController {
    private final MicrosoftExcelIntegration microsoftExcelIntegration;
    private final TeacherRepository teacherRepository;

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
}
