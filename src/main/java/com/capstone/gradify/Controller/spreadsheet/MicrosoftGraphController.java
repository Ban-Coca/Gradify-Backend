package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Service.spreadsheet.MicrosoftExcelIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class MicrosoftGraphController {
    private final MicrosoftExcelIntegration microsoftExcelIntegration;

    @GetMapping("/drive")
    public ResponseEntity<String> getUserDriveId(@RequestParam int userId){
        try{
            String driveId = microsoftExcelIntegration.getUserDriveIds(userId);
            return ResponseEntity.ok(driveId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving drive ID: " + e.getMessage());
        }
    }
}
