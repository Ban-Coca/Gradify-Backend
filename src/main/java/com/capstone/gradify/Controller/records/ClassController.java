package com.capstone.gradify.Controller.records;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.Service.academic.RecordsService;
import com.capstone.gradify.dto.request.UpdateClassDetails;
import com.capstone.gradify.dto.response.ClassResponse;
import com.capstone.gradify.dto.response.StudentResponse;
import com.capstone.gradify.mapper.ClassMapper;
import com.capstone.gradify.mapper.StudentMapper;
import com.capstone.gradify.dto.response.SpreadsheetResponse;
import com.capstone.gradify.mapper.SpreadsheetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Service.academic.ClassService;
import com.capstone.gradify.dto.response.StudentTableData;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.naming.NameNotFoundException;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;
    private final RecordsService recordsService;
    private final TeacherRepository teacherRepository;
    private final StudentMapper studentMapper;
    private final ClassMapper classMapper;
    private final SpreadsheetMapper spreadsheetMapper;

    @PostMapping()
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

    @GetMapping()
    public ResponseEntity<List<ClassResponse>> getAllClasses() {
        List<ClassEntity> classes = classService.getAllClasses();
        List<ClassResponse> classResponses = classMapper.toClassResponseList(classes);
        return ResponseEntity.status(200).body(classResponses);
    }

    @GetMapping("/{classId}")
    public ResponseEntity<Object> getClassById(@PathVariable int classId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity != null) {
            ClassResponse classResponse = classMapper.toClassResponse(classEntity);
            return ResponseEntity.status(200).body(classResponse);
        } else {
            return ResponseEntity.status(404).body("Class not found");
        }
    }

    @PutMapping("/{classId}")
    public ResponseEntity<Object> updateClasses(@PathVariable int classId, @RequestBody UpdateClassDetails classEntity) {
        try {
            ClassEntity updatedClass = classService.updateClass(classId, classEntity);
            ClassResponse response = classMapper.toClassResponse(updatedClass);
            return ResponseEntity.ok(response);
        } catch (NameNotFoundException e) {
            return ResponseEntity.status(404).body("Class not found: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error updating class: " + e.getMessage());
        }
    }

    @DeleteMapping("/{classId}")
    public ResponseEntity<Object> deleteClass(@PathVariable int classId) {
        String msg = classService.deleteClass(classId);
        return ResponseEntity.status(200).body(msg);
    }

    @GetMapping("/{classId}/spreadsheets")
    public ResponseEntity<Object> getSpreadsheetByClassId(@PathVariable int classId) {
        ClassEntity classEntity = classService.getClassById(classId);
        if (classEntity == null) {
            return ResponseEntity.status(404).body("Class not found");
        }
        List<ClassSpreadsheet> spreadsheets = classService.getSpreadsheetsByClassId(classId);
        if (spreadsheets == null || spreadsheets.isEmpty()) {
            return ResponseEntity.status(404).body("No spreadsheets found for this class");
        }
        SpreadsheetResponse response = spreadsheetMapper.toSpreadsheetResponse(spreadsheets.get(0));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<Object> getClassByTeacherId(@PathVariable int teacherId) {
        boolean teacherExists = teacherRepository.existsById(teacherId);
        if (!teacherExists) {
            return ResponseEntity.status(404).body("Teacher not found");
        }
        List<ClassEntity> classes = classService.getClassesByTeacherId(teacherId);
        List<ClassResponse> classResponses = classMapper.toClassResponseList(classes);
        return ResponseEntity.ok(classResponses);
    }

    @GetMapping("/{classId}/students")
    public ResponseEntity<List<StudentTableData>> getClassRoster(@PathVariable int classId) {
        List<StudentTableData> rosterData = recordsService.getClassRosterTableData(classId);
        return ResponseEntity.ok(rosterData);
    }

    @GetMapping("/{classId}/students/{studentId}/grade")
    public ResponseEntity<Double> getStudentGrade(
            @PathVariable int classId,
            @PathVariable int studentId) {
        double grade = recordsService.calculateStudentGrade(studentId, classId);
        return ResponseEntity.ok(grade);
    }

    @GetMapping("/{classId}/grade/average")
    public ResponseEntity<Double> getAvgClassGrade(@PathVariable int classId){
        double avgClassGrade = recordsService.calculateClassAverageGrade(classId);
        return ResponseEntity.ok(avgClassGrade);
    }

    @GetMapping("/{classId}/students/count")
    public ResponseEntity<Integer> getClassCount(@PathVariable int classId) {
        int classCount = recordsService.getStudentCount(classId);
        return ResponseEntity.ok(classCount);
    }

    @GetMapping("/{classId}/students/details")
    public ResponseEntity<List<StudentResponse>> getStudentsByClassId(@PathVariable int classId) {
        Set<StudentEntity> students = classService.getStudentsByClassId(classId);
        List<StudentResponse> studentDTOs = studentMapper.toDtoList(students);
        return ResponseEntity.ok(studentDTOs);
    }
}