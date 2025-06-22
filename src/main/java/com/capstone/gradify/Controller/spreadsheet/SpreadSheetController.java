package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository; // Ensure this is imported
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.spreadsheet.ClassSpreadsheetService;
import com.capstone.gradify.Service.spreadsheet.CloudSpreadsheetManager;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors; // Required for stream operations

@RestController
@RequestMapping("api/spreadsheet")
public class SpreadSheetController {

    private final ClassSpreadsheetService classSpreadsheetService;
    private final RecordsService recordsService;

    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private CloudSpreadsheetManager cloudSpreadsheetManager;
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository; // Added for check-exists


    @Autowired
    public SpreadSheetController(ClassSpreadsheetService classSpreadsheetService, RecordsService recordsService) {
        this.classSpreadsheetService = classSpreadsheetService;
        this.recordsService = recordsService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadSpreadsheet(@RequestParam("file") MultipartFile file, @RequestParam("teacherId") Integer teacherId) {
        // Logic to handle spreadsheet upload
        try{
            List<Map<String, String >> records = classSpreadsheetService.parseClassRecord(file);
            Map<String, Integer> maxAssessmentValue = classSpreadsheetService.getMaxAssessmentValue(file);
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            ClassEntity classEntity = classSpreadsheetService.createClassEntityFromSpreadsheet(file, records, teacher);

            // Save the ClassEntity
            classEntity = classRepository.save(classEntity);
            ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(
                    file.getOriginalFilename(),
                    teacher,
                    records,
                    classEntity,
                    maxAssessmentValue);

            Set<StudentEntity> students = new HashSet<>();
            savedSpreadsheet.getGradeRecords().forEach(record -> {
                if (record.getStudent() != null) {
                    students.add(record.getStudent());
                }
            });


            classEntity.setStudents(students);
            classEntity = classRepository.save(classEntity);

            Map<String, Object> response = new HashMap<>();
            response.put("spreadsheet", savedSpreadsheet);
            response.put("class", classEntity);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @PutMapping("/update/{classId}")
    public ResponseEntity<?> updateSpreadsheet(
            @PathVariable("classId") Integer classId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("teacherId") Integer teacherId) {

        try {
            // Get the teacher
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));
            List<ClassSpreadsheet> spreadsheet = classSpreadsheetService.getClassSpreadSheetByClassId(classId);
            // Update the spreadsheet using the existing service method
            ClassSpreadsheet updatedSpreadsheet = classSpreadsheetService.updateSpreadsheet(
                    spreadsheet.get(0).getId(),
                    file,
                    teacher
            );

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("spreadsheet", updatedSpreadsheet);
            response.put("class", updatedSpreadsheet.getClassEntity());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating spreadsheet: " + e.getMessage());
        }
    }

    // DTO for receiving updated grade data
    @Data
    public static class UpdateGradesRequest {
        private Long gradeRecordId;
        private Map<String, String> grades;
    }

    @PutMapping("/update-grades")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<Object> updateGrades(@RequestBody List<UpdateGradesRequest> updatedRecords) {
        try {
            recordsService.updateGrades(updatedRecords);
            return ResponseEntity.ok().body("Grades updated successfully.");
        } catch (Exception e) {
            // Log the exception for debugging
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating grades: " + e.getMessage());
        }
    }

    @PostMapping("/process-url")
    public ResponseEntity<?> processSpreadsheetUrl(
            @RequestParam("url") String spreadsheetUrl,
            @RequestParam("teacherId") Integer teacherId) {
        try {
            // Validate the URL is supported
            if (!cloudSpreadsheetManager.canProcessLink(spreadsheetUrl)) {
                return ResponseEntity.badRequest().body(
                        "Unsupported spreadsheet URL. Please provide a valid Google Sheets or Microsoft Excel Online URL.");
            }

            // Get the teacher
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            // Process the spreadsheet
            ClassSpreadsheet spreadsheet = cloudSpreadsheetManager.processSharedSpreadsheet(spreadsheetUrl, teacher);

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("spreadsheet", spreadsheet);
            response.put("class", spreadsheet.getClassEntity());
            response.put("provider", cloudSpreadsheetManager.getServiceNameForLink(spreadsheetUrl));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing spreadsheet URL: " + e.getMessage());
        }
    }

    /**
     * Check if the provided URL is supported by our cloud spreadsheet integrations
     *
     * @param url URL to check
     * @return Support information
     */
    @GetMapping("/check-url-support")
    public ResponseEntity<?> checkUrlSupport(@RequestParam("url") String url) {
        boolean supported = cloudSpreadsheetManager.canProcessLink(url);
        String provider = supported ? cloudSpreadsheetManager.getServiceNameForLink(url) : "Unsupported";

        Map<String, Object> response = new HashMap<>();
        response.put("supported", supported);
        response.put("provider", provider);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getSpreadsheetById(@PathVariable("id") Long id) {
        try {
            // Validate the ID
            if (id == null) {
                return ResponseEntity.badRequest().body("Spreadsheet ID cannot be null");
            }

            Optional<ClassSpreadsheet> classSpreadsheetOpt = classSpreadsheetService.getClassSpreadsheetById(id);

            if (classSpreadsheetOpt.isPresent()) {
                // Return the spreadsheet if found
                ClassSpreadsheet spreadsheet = classSpreadsheetOpt.get();
                return ResponseEntity.ok(spreadsheet);
            } else {
                // Return 404 if not found
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Spreadsheet with ID " + id + " not found");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error retrieving spreadsheet: " + e.getMessage());
        }
    }

    @GetMapping("/check-google-sheets-config")
    public ResponseEntity<Map<String, Boolean>> checkGoogleSheetsConfig() {
        Resource resource = new ClassPathResource("credentials/google-sheets-credentials.json");
        boolean configured = resource.exists();

        Map<String, Boolean> response = new HashMap<>();
        response.put("configured", configured);

        return ResponseEntity.ok(response);
    }

    // New endpoint to check if a spreadsheet file name already exists for a teacher
    @GetMapping("/check-exists")
    public ResponseEntity<Boolean> checkSpreadsheetExists(
            @RequestParam("fileName") String fileName,
            @RequestParam("teacherId") Integer teacherId) {
        try {
            // Find teacher to ensure they exist
            teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found with ID: " + teacherId));

            // Check for existing spreadsheets by file name
            List<ClassSpreadsheet> existingSpreadsheets = classSpreadsheetRepository.findByFileName(fileName);

            // Filter by teacherId
            boolean exists = existingSpreadsheets.stream()
                    .anyMatch(s -> s.getUploadedBy() != null && s.getUploadedBy().getUserId() == teacherId);

            return ResponseEntity.ok(exists);
        } catch (RuntimeException e) {
            // Log error and return false, or a more specific HTTP error
            System.err.println("Error checking spreadsheet existence: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(false); // e.g., if teacher not found
        }
        catch (Exception e) {
            System.err.println("Unexpected error checking spreadsheet existence: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }
}