package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.ClassSpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/spreadsheet")
public class SpreadSheetController {

    private final ClassSpreadsheetService classSpreadsheetService;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    public SpreadSheetController(ClassSpreadsheetService classSpreadsheetService) {
        this.classSpreadsheetService = classSpreadsheetService;
    }
    @PostMapping("/upload")
    public ResponseEntity<?> uploadSpreadsheet(@RequestParam("file") MultipartFile file, @RequestParam("teacherId") Integer teacherId) {
        // Logic to handle spreadsheet upload
        try{
            List<Map<String, String >> records = classSpreadsheetService.parseClassRecord(file);
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            ClassEntity classEntity = classSpreadsheetService.createClassEntityFromSpreadsheet(file, records, teacher);

            // Save the ClassEntity
            classEntity = classRepository.save(classEntity);
            ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(
                    file.getOriginalFilename(),
                    teacher,
                    records,
                    classEntity);

            Map<String, Object> response = new HashMap<>();
            response.put("spreadsheet", savedSpreadsheet);
            response.put("class", classEntity);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getSpreadsheetById(@PathVariable("id") Long id) {
        // Logic to handle spreadsheet upload
        try{
            Optional<ClassSpreadsheet> classSpreadsheet = classSpreadsheetService.getClassSpreadsheetById(id);
            return ResponseEntity.ok(classSpreadsheet);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }
}
