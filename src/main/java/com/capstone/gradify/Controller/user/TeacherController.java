package com.capstone.gradify.Controller.user;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.AiServices.AiAnalysisService;
import com.capstone.gradify.Service.ClassService;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.spreadsheet.ClassSpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final ClassSpreadsheetService classSpreadsheetService;

    
    @Autowired
    private ClassService classService;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private RecordsService recordsService;
    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    public TeacherController(ClassSpreadsheetService classSpreadsheetService) {
        this.classSpreadsheetService = classSpreadsheetService;
    }

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

    @GetMapping("/get")
    public ResponseEntity<?> getSpreadsheetById(@RequestParam("id") Long id) {
        // Logic to handle spreadsheet upload
        try{
            Optional<ClassSpreadsheet> classSpreadsheet = classSpreadsheetService.getClassSpreadsheetById(id);
            return ResponseEntity.ok(classSpreadsheet);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @PostMapping("/createclass")
    public ResponseEntity<Object> createClass(@RequestBody ClassEntity classEntity) {
        // Set createdAt and updatedAt to the current date
        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);

        // Save the class entity
        ClassEntity createdClass = classService.createClass(classEntity);
        return ResponseEntity.status(201).body(createdClass);
    }

    @GetMapping("/getallclasses")
    public ResponseEntity<Object> getAllClasses() {
        return ResponseEntity.status(200).body(classService.getAllClasses());
    }

    @GetMapping("/getclassbyid/{classId}")
    public ResponseEntity<Object> getClassById(@PathVariable int classId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity != null) {
            return ResponseEntity.status(200).body(classEntity);
        } else {
            return ResponseEntity.status(404).body("Class not found");
        }
    }
    
    @PutMapping("putclasses/{classId}")
    public ResponseEntity<Object> updateClasses(@PathVariable int classId, @RequestBody ClassEntity classEntity) {
        try{
            ClassEntity updatedClass = classService.updateClass(classId, classEntity);
            return ResponseEntity.status(200).body(updatedClass);
        }catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while updating the task: " + e.getMessage()); // Return 500 for unexpected errors
        }
    }

    @DeleteMapping("/deleteclass/{classId}")
    public ResponseEntity<Object> deleteClass(@PathVariable int classId) {
        String msg = classService.deleteClass(classId);
        return ResponseEntity.status(200).body(msg);
    }

    @GetMapping("/getstudentcount/{teacherId}")
    public int getStudentCountByTeacherId(@PathVariable int teacherId) {
        return recordsService.getStudentCountByTeacher(teacherId);
    }
    @GetMapping("/getatriskstudents/{teacherId}")
    public int getAtRiskStudentsByTeacherId(@PathVariable int teacherId) {
        return recordsService.countAtRiskStudents(teacherId);
    }
    @GetMapping("/gettopstudents/{teacherId}")
    public int getTopStudentsByTeacherId(@PathVariable int teacherId) {
        return recordsService.countTopPerformingStudents(teacherId);
    }

    @GetMapping("/teacher-grade-distribution/{teacherId}")
    public ResponseEntity<Map<String, Integer>> getTeacherGradeDistribution(@PathVariable int teacherId) {
        try {
            Map<String, Integer> distribution = recordsService.getTeacherGradeDistribution(teacherId);
            return ResponseEntity.ok(distribution);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @GetMapping("/class-performance/{teacherId}")
    public ResponseEntity<List<RecordsService.TeacherAssessmentPerformance>> getClassPerformance(@PathVariable int teacherId) {
        try {
            List<RecordsService.TeacherAssessmentPerformance> performanceData = recordsService.getClassPerformanceData(teacherId);
            return ResponseEntity.ok(performanceData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @GetMapping("/class-ai-analytics/{classId}")
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
}
