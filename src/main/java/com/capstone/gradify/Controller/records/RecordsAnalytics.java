package com.capstone.gradify.Controller.records;

import com.capstone.gradify.Service.RecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/records")
public class RecordsAnalytics {

    @Autowired
    private RecordsService recordsService;

    @GetMapping("/calculate/student")
    public ResponseEntity<?> calculateStudentGrade(
            @RequestParam int studentId,
            @RequestParam int classId) {
        try {
            double grade = recordsService.calculateStudentGrade(studentId, classId);
            return ResponseEntity.ok(Map.of("grade", grade));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error calculating grade: " + e.getMessage());
        }
    }

    @GetMapping("/calculate/class")
    public ResponseEntity<?> calculateClassGrades(@RequestParam int classId) {
        try {
            Map<String, Double> grades = recordsService.calculateClassGrades(classId);
            return ResponseEntity.ok(grades);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error calculating class grades: " + e.getMessage());
        }
    }
}