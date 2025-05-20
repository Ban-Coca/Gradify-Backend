package com.capstone.gradify.Controller.records;

import java.util.Date;
import java.util.List;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.RecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
                // Assuming you add this field to your entity
                classEntity.setRoom(room);
            }
            
            if (schedule != null && !schedule.isEmpty()) {
                // Assuming you add this field to your entity
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
}