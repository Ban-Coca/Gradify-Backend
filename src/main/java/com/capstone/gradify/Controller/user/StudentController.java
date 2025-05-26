package com.capstone.gradify.Controller.user;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.ClassService;
import com.capstone.gradify.Service.GradingSchemeService;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.ReportService;
import com.capstone.gradify.Service.spreadsheet.ClassSpreadsheetService;
import com.capstone.gradify.Service.userservice.TeacherService;
import com.capstone.gradify.dto.report.ReportResponseDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;


@RestController
@RequestMapping("/api/student")
public class StudentController {
    
    @Autowired
    private RecordsService recordsService;
    @Autowired
    private GradingSchemeService gradingSchemeService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ClassSpreadsheetService classSpreadsheetService;

    @GetMapping("/{studentId}/classes")
    public ResponseEntity<?> getStudentClasses(@PathVariable int studentId) {
        // Find all grade records for this student
        List<GradeRecordsEntity> gradeRecords = recordsService.getGradeRecordsByStudentId(studentId);

        // Collect unique classes
        Set<ClassEntity> classes = new HashSet<>();
        for (GradeRecordsEntity record : gradeRecords) {
            if (record.getClassRecord() != null && record.getClassRecord().getClassEntity() != null) {
                classes.add(record.getClassRecord().getClassEntity());
            }
        }
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/{studentId}/classes/{classId}/grades")
    public ResponseEntity<?> getStudentGradesForClass(
            @PathVariable int studentId,
            @PathVariable int classId) {
        Map<String, Object> grades = recordsService.getStudentCourseGrades(studentId, classId);
        return ResponseEntity.ok(grades);
    }

    @GetMapping("/classes/{classId}/gradingscheme")
    public ResponseEntity<?> getGradingSchemeByClassEntityId(@PathVariable int classId) {
        try {
            String gradingScheme = gradingSchemeService.getGradeSchemeByClassEntityId(classId);
            return ResponseEntity.ok(gradingScheme);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @GetMapping("/classes/{classId}/teacher")
    public ResponseEntity<?> getTeacherFullNameByClassId(@PathVariable int classId) {
        String fullName = teacherService.getTeacherFullNameByClassId(classId);
        if (fullName != null) {
            return ResponseEntity.ok(fullName);
        } else {
            return ResponseEntity.status(404).body("Teacher not found for class ID: " + classId);
        }
    }

    @GetMapping("/{studentId}/reports")
    public ResponseEntity<?> getReportsByStudentId(@PathVariable int studentId) {
        List<ReportResponseDTO> reports = reportService.getReportsByStudentId(studentId);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{studentId}/classes/{classId}/calculated-grade")
    public ResponseEntity<?> getCalculatedGrade(
            @PathVariable int studentId,
            @PathVariable int classId) {
        try {
            List<ClassSpreadsheet> classRecord = classSpreadsheetService.getClassSpreadSheetByClassId(classId);
            // Get the grade record for this student and class
            List<GradeRecordsEntity> gradeRecords = recordsService
                    .getGradeRecordsByStudentIdAndClassId(studentId, classId);

            if (gradeRecords.isEmpty()) {
                return ResponseEntity.status(404).body("Grade record not found for student and class.");
            }

            GradeRecordsEntity record = gradeRecords.get(0);

            // Get grading scheme for this class
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            Map<String, Integer> maxAssessmentValues = new HashMap<>();
            for (ClassSpreadsheet spreadsheet : classRecord) {
                // Use the assessmentMaxValues field directly
                if (spreadsheet.getAssessmentMaxValues() != null) {
                    maxAssessmentValues.putAll(spreadsheet.getAssessmentMaxValues());
                }
            }
            // Calculate the grade
            double calculatedGrade = recordsService.calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), maxAssessmentValues);

            return ResponseEntity.ok(calculatedGrade);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error calculating grade: " + e.getMessage());
        }
    }
    
    @GetMapping("/{studentId}/average-percentage")
    public ResponseEntity<Double> getStudentAveragePercentage(@PathVariable int studentId) {
        try {
            double average = recordsService.getStudentAveragePercentage(studentId);
            return ResponseEntity.ok(average);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/{studentId}/grade-records")
    public ResponseEntity<List<GradeRecordsEntity>> getGradeRecordsByStudentId(
            @PathVariable int studentId) {
        try {
            List<GradeRecordsEntity> records = recordsService.getGradeRecordsByStudentId(studentId);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/{studentId}/all-grades")
    public ResponseEntity<Map<Integer, Double>> getAllGradesByStudentId(@PathVariable int studentId) {
        try {
            Map<Integer, Double> grades = recordsService.getAllGradesByStudentId(studentId);
            return ResponseEntity.ok(grades);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
    
    @GetMapping("/{studentId}/class-averages")
    public ResponseEntity<List<Map<String, Object>>> getClassAveragesForStudent(@PathVariable int studentId) {
        try {
            List<Map<String, Object>> averages = recordsService.getClassAveragesForStudent(studentId);
            return ResponseEntity.ok(averages);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}