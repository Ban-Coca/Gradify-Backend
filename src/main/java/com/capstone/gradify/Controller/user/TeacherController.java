package com.capstone.gradify.Controller.user;

import com.capstone.gradify.Service.ClassSpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final ClassSpreadsheetService classSpreadsheetService;

    @Autowired
    public TeacherController(ClassSpreadsheetService classSpreadsheetService) {
        this.classSpreadsheetService = classSpreadsheetService;
    }
    @PostMapping("/upload-spreadsheet")
    public ResponseEntity<?> uploadSpreadsheet(@RequestParam("file") MultipartFile file) {
        // Logic to handle spreadsheet upload
        try{
            List<Map<String, String >> records = classSpreadsheetService.parseClassRecord(file);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }
}
