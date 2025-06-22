package com.capstone.gradify.Controller.records;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.ReportService;
import com.capstone.gradify.dto.student.StudentDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Service.ClassService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/class")
public class ClassController {

    @Autowired
    private ClassService classService;
    @Autowired
    private RecordsService recordsService;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ReportService reportService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;
    @Autowired
    private GradeRecordRepository gradeRecordRepository;

    @PostMapping("/createclass")
    public ResponseEntity<Object> createClass(
            @RequestParam("className") String className,
            @RequestParam("semester") String semester,
            @RequestParam("schoolYear") String schoolYear,
            @RequestParam("section") String section,
            @RequestParam("classCode") String classCode,
            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "schedule", required = false) String schedule,
            @RequestParam("teacher.userId") Integer teacherId) {

        try {
            // Find the teacher entity
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found with ID: " + teacherId));

            // Create and populate the class entity
            ClassEntity classEntity = new ClassEntity();
            classEntity.setClassName(className);
            classEntity.setSemester(semester);
            classEntity.setSchoolYear(schoolYear);
            classEntity.setSection(section);
            classEntity.setClassCode(classCode);

            // Set optional fields if provided
            if (room != null && !room.isEmpty()) {
                classEntity.setRoom(room);
            }

            if (schedule != null && !schedule.isEmpty()) {
                classEntity.setSchedule(schedule);
            }

            // Set the teacher and timestamps
            classEntity.setTeacher(teacher);
            Date now = new Date();
            classEntity.setCreatedAt(now);
            classEntity.setUpdatedAt(now);

            // Save the class
            ClassEntity createdClass = classService.createClass(classEntity);
            return ResponseEntity.status(201).body(createdClass);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating class: " + e.getMessage());
        }
    }

    @GetMapping("/getallstudents")
    public ResponseEntity<List<StudentDTO>> getAllStudents() {
        List<StudentEntity> students = studentRepository.findAll();
        List<StudentDTO> studentDTOs = students.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(studentDTOs);
    }

    @PostMapping("/{classId}/addstudent/{studentId}")
    @Transactional
    public ResponseEntity<Object> addStudentToClass(@PathVariable int classId, @PathVariable int studentId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Class not found");
        }
        StudentEntity studentEntity = studentRepository.findById(studentId).orElse(null);
        if (studentEntity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Student not found");
        }

        classService.addStudentToClass(classEntity, studentEntity);

        // Find the spreadsheet for this class to create a grade record
        List<ClassSpreadsheet> spreadsheets = classSpreadsheetRepository.findByClassEntity_ClassId(classId);
        if (!spreadsheets.isEmpty()) {
            ClassSpreadsheet spreadsheet = spreadsheets.get(0);

            boolean recordExists = spreadsheet.getGradeRecords().stream()
                    .anyMatch(record -> record.getStudent() != null && record.getStudent().getUserId() == studentId);

            if (!recordExists) {
                GradeRecordsEntity newGradeRecord = new GradeRecordsEntity();
                newGradeRecord.setStudent(studentEntity);
                newGradeRecord.setStudentNumber(studentEntity.getStudentNumber());
                newGradeRecord.setClassRecord(spreadsheet);

                Map<String, String> initialGrades = new HashMap<>();
                if (spreadsheet.getAssessmentMaxValues() != null) {
                    for (String assessment : spreadsheet.getAssessmentMaxValues().keySet()) {
                        initialGrades.put(assessment, ""); // Initialize with empty score
                    }
                }

                initialGrades.put("First Name", studentEntity.getFirstName());
                initialGrades.put("Last Name", studentEntity.getLastName());
                initialGrades.put("Student Number", studentEntity.getStudentNumber());

                newGradeRecord.setGrades(initialGrades);
                gradeRecordRepository.save(newGradeRecord);
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body("Student added to class successfully");
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

    @GetMapping("/getspreadsheetbyclassid/{classId}")
    public ResponseEntity<Object> getSpreadsheetByClassId(@PathVariable int classId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity == null) {
            return ResponseEntity.status(404).body("Class not found");
        }
        List<ClassSpreadsheet> spreadsheets = classService.getSpreadsheetsByClassId(classId);
        return ResponseEntity.ok(spreadsheets);
    }

    @GetMapping("/getclassbyteacherid/{teacherId}")
    public ResponseEntity<Object> getClassByTeacherId(@PathVariable int teacherId) {
        boolean teacherExists = teacherRepository.existsById(teacherId);
        if (!teacherExists) {
            return ResponseEntity.status(404).body("Teacher not found");
        }
        List<ClassEntity> classes = classService.getClassesByTeacherId(teacherId);
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/{classId}/roster")
    public ResponseEntity<List<RecordsService.StudentTableData>> getClassRoster(@PathVariable int classId) {
        List<RecordsService.StudentTableData> rosterData = recordsService.getClassRosterTableData(classId);
        return ResponseEntity.ok(rosterData);
    }

    @GetMapping("/{classId}/students/{studentId}/grade")
    public ResponseEntity<Double> getStudentGrade(
            @PathVariable int classId,
            @PathVariable int studentId) {
        double grade = recordsService.calculateStudentGrade(studentId, classId);
        return ResponseEntity.ok(grade);
    }

    @GetMapping("/{classId}/avgclassgrade")
    public ResponseEntity<Double> getAvgClassGrade(@PathVariable int classId){
        double avgClassGrade = recordsService.calculateClassAverageGrade(classId);
        return ResponseEntity.ok(avgClassGrade);
    }

    @GetMapping("/{classId}/studentcount")
    public ResponseEntity<Integer> getClassCount(@PathVariable int classId) {
        int classCount = recordsService.getStudentCount(classId);
        return ResponseEntity.ok(classCount);
    }

    @GetMapping("/{classId}/students")
    public ResponseEntity<List<StudentDTO>> getStudentsByClassId(@PathVariable int classId) {
        Set<StudentEntity> students = classService.getStudentsByClassId(classId);
        List<StudentDTO> studentDTOs = students.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(studentDTOs);
    }

    @PostMapping("/{classId}/send-grades")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<?> sendGradesForColumn(@PathVariable int classId, @RequestBody Map<String, String> payload) {
        String columnName = payload.get("columnName");
        if (columnName == null || columnName.isEmpty()) {
            return ResponseEntity.badRequest().body("Column name is required.");
        }
        try {
            recordsService.sendGradesForColumnToStudents(classId, columnName);
            return ResponseEntity.ok().body("Grades for column '" + columnName + "' sent successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error sending grades: " + e.getMessage());
        }
    }

    private StudentDTO convertToDTO(StudentEntity student) {
        StudentDTO dto = new StudentDTO();
        dto.setUserId(student.getUserId());
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setEmail(student.getEmail());
        dto.setStudentNumber(student.getStudentNumber());
        dto.setMajor(student.getMajor());
        dto.setYearLevel(student.getYearLevel());
        return dto;
    }
}