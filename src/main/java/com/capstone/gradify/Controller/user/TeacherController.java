package com.capstone.gradify.Controller.user;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.AiServices.AiAnalysisService;
import com.capstone.gradify.Service.ClassService;
import com.capstone.gradify.Service.GradeService;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.spreadsheet.ClassSpreadsheetService;
import com.capstone.gradify.dto.response.TeacherAssessmentPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {
    private final ClassSpreadsheetService classSpreadsheetService;
    private final  ClassService classService;
    private final TeacherRepository teacherRepository;
    private final RecordsService recordsService;
    private final AiAnalysisService aiAnalysisService;
    private final GradeService gradeService;

//    @PostMapping("/upload")
//    public ResponseEntity<?> uploadSpreadsheet(@RequestParam("file") MultipartFile file, @RequestParam("teacherId") Integer teacherId) {
//        // Logic to handle spreadsheet upload
//        try{
//            List<Map<String, String >> records = classSpreadsheetService.parseClassRecord(file);
//            TeacherEntity teacher = teacherRepository.findById(teacherId)
//                    .orElseThrow(() -> new RuntimeException("Teacher not found"));
//
//            ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(file.getOriginalFilename(), teacher, records);
//
//            return ResponseEntity.ok(savedSpreadsheet);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
//        }
//    }

//    @GetMapping("/get")
//    public ResponseEntity<?> getSpreadsheetById(@RequestParam("id") Long id) {
//        // Logic to handle spreadsheet upload
//        try{
//            Optional<ClassSpreadsheet> classSpreadsheet = classSpreadsheetService.getClassSpreadsheetById(id);
//            return ResponseEntity.ok(classSpreadsheet);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
//        }
//    }

    @PostMapping
    public ResponseEntity<Object> createClass(@RequestBody ClassEntity classEntity) {
        // Set createdAt and updatedAt to the current date
        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);

        // Save the class entity
        ClassEntity createdClass = classService.createClass(classEntity);
        return ResponseEntity.status(201).body(createdClass);
    }

    @GetMapping
    public ResponseEntity<Object> getAllClasses() {
        return ResponseEntity.status(200).body(classService.getAllClasses());
    }

    @GetMapping("/{classId}")
    public ResponseEntity<Object> getClassById(@PathVariable int classId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity != null) {
            return ResponseEntity.status(200).body(classEntity);
        } else {
            return ResponseEntity.status(404).body("Class not found");
        }
    }
    
    @PutMapping("/{classId}")
    public ResponseEntity<Object> updateClasses(@PathVariable int classId, @RequestBody ClassEntity classEntity) {
        try{
            ClassEntity updatedClass = classService.updateClass(classId, classEntity);
            return ResponseEntity.status(200).body(updatedClass);
        }catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while updating the task: " + e.getMessage()); // Return 500 for unexpected errors
        }
    }

    @DeleteMapping("/{classId}")
    public ResponseEntity<Object> deleteClass(@PathVariable int classId) {
        String msg = classService.deleteClass(classId);
        return ResponseEntity.status(200).body(msg);
    }

    @GetMapping("/{teacherId}/students/count")
    public int getStudentCountByTeacherId(@PathVariable int teacherId) {
        return recordsService.getStudentCountByTeacher(teacherId);
    }
    @GetMapping("/{teacherId}/risk/students/count")
    public int getAtRiskStudentsByTeacherId(@PathVariable int teacherId) {
        return recordsService.countAtRiskStudents(teacherId);
    }
    @GetMapping("/{teacherId}/top/students/count")
    public int getTopStudentsByTeacherId(@PathVariable int teacherId) {
        return recordsService.countTopPerformingStudents(teacherId);
    }

    @GetMapping("/{teacherId}/grade/distribution")
    public ResponseEntity<Map<String, Integer>> getTeacherGradeDistribution(@PathVariable int teacherId) {
        try {
            Map<String, Integer> distribution = recordsService.getTeacherGradeDistribution(teacherId);
            return ResponseEntity.ok(distribution);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @GetMapping("/{teacherId}/class/performance")
    public ResponseEntity<List<TeacherAssessmentPerformance>> getClassPerformance(@PathVariable int teacherId) {
        try {
            List<TeacherAssessmentPerformance> performanceData = recordsService.getClassPerformanceData(teacherId);
            return ResponseEntity.ok(performanceData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @GetMapping("/{classId}/ai/analytics")
    public ResponseEntity<?> getClassAIAnalytics(@PathVariable int classId) {
        try {
            ClassEntity classEntity = classService.getClassById(classId);
            if (classEntity == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Class not found");
            }
            String analytics = aiAnalysisService.analyzeClass(classId);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving AI analytics: " + e.getMessage());
        }
    }

    @GetMapping("/class-spreadsheet/{classId}/assessments/available")
    public ResponseEntity<Set<String>> getAvailableAssessments(@PathVariable int classId) {
        try {
            Set<String> assessments = gradeService.getAvailableAssessments(classId);
            return ResponseEntity.ok(assessments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/class-spreadsheet/{classId}/assessments/visible")
    public ResponseEntity<Set<String>> getVisibleAssessments(@PathVariable int classId) {
        try {
            Set<String> assessments = gradeService.getVisibleAssessments(classId);
            return ResponseEntity.ok(assessments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/class-spreadsheet/{classSpreadsheetId}/assessments/visible")
    public ResponseEntity<Set<String>> updateAssessmentVisibility(@PathVariable Long classSpreadsheetId, @RequestBody Set<String> assessments) {
        try {
            gradeService.updateAssessmentVisibility(classSpreadsheetId, assessments);
            return ResponseEntity.ok(assessments);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/{classSpreadsheetId}/assessments/{assessmentName}/toggle")
    public ResponseEntity<Void> toggleAssessmentVisibility(
            @PathVariable Long classSpreadsheetId,
            @PathVariable String assessmentName) {

        gradeService.toggleAssessmentVisibility(classSpreadsheetId, assessmentName);
        return ResponseEntity.ok().build();
    }

    /**
     * Get assessment visibility status (for UI display)
     */
    @GetMapping("/{classSpreadsheetId}/assessments/status")
    public ResponseEntity<Map<String, Boolean>> getAssessmentVisibilityStatus(
            @PathVariable int classSpreadsheetId) {

        Set<String> available = gradeService.getAvailableAssessments(classSpreadsheetId);
        Set<String> visible = gradeService.getVisibleAssessments(classSpreadsheetId);

        Map<String, Boolean> status = available.stream()
                .collect(Collectors.toMap(
                        assessment -> assessment,
                        assessment -> visible.contains(assessment)
                ));
        return ResponseEntity.ok(status);
    }
}
