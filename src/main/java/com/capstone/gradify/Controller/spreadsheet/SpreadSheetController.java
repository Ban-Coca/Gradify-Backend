package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Service.SpreadSheetService;
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
@RequestMapping("api/spreadsheet")
public class SpreadSheetController {

    private final SpreadSheetService spreadSheetService;

    @Autowired
    public SpreadSheetController(SpreadSheetService spreadSheetService) {
        this.spreadSheetService = spreadSheetService;
    }
    @PostMapping("/upload")
    public ResponseEntity<?> uploadSpreadsheet(@RequestParam("file") MultipartFile file) {
        // Logic to handle spreadsheet upload
        try{
            List<Map<String, String >> records = spreadSheetService.parseClassRecord(file);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }
}
