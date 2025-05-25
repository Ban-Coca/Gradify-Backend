package com.capstone.gradify.Controller.records;

import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Service.GradingSchemeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/grading")
public class GradingSchemeController {

    @Autowired
    private GradingSchemeService gradingSchemeService;


    @PostMapping("/savescheme")
    public ResponseEntity<?> saveGradingScheme(@RequestBody Map<String, Object> requestBody,
                                               @RequestParam int classId,
                                               @RequestParam int teacherId) {
        try {
            // Create a new GradingSchemes object
            GradingSchemes gradingScheme = new GradingSchemes();

            // Convert the schemes array to a JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String schemesJson = objectMapper.writeValueAsString(requestBody.get("schemes"));

            // Set the JSON string to the gradingScheme property
            gradingScheme.setGradingScheme(schemesJson);

            // Save using your service
            GradingSchemes savedScheme = gradingSchemeService.saveGradingScheme(gradingScheme, classId, teacherId);
            return ResponseEntity.ok(savedScheme);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error saving grading scheme: " + e.getMessage());
        }
    }
    @GetMapping("/getscheme")
    public ResponseEntity<?> getGradingScheme(@RequestParam int classId) {
        try {
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);
            Map<String, Object> response = Map.of(
                    "id", gradingScheme.getId(),
                    "gradingScheme", gradingScheme.getGradingScheme()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving grading scheme: " + e.getMessage());
        }
    }

    @PutMapping("/updatescheme/{classId}/teacher/{teacherId}")
    public ResponseEntity<?> updateGradingScheme(@PathVariable Integer classId,
                                                 @PathVariable Integer teacherId,
                                                 @RequestBody Map<String, Object> requestBody) {
        try {
            // Convert the schemes array to a JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String schemesJson = objectMapper.writeValueAsString(requestBody.get("schemes"));

            // Create a GradingSchemes object to pass to the service
            GradingSchemes gradingScheme = new GradingSchemes();
            gradingScheme.setGradingScheme(schemesJson);

            // Update the grading scheme using your service
            GradingSchemes updatedScheme = gradingSchemeService.updateGradingScheme(gradingScheme, classId, teacherId);

            return ResponseEntity.ok(updatedScheme);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error updating grading scheme: " + e.getMessage());
        }
    }
}
