package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import com.capstone.gradify.dto.response.DriveItemResponse;
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
}
