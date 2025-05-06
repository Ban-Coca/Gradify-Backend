package com.capstone.gradify.Controller.records;

import java.util.Date;
import java.util.List;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
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

    @PostMapping("/createclass")
    public ResponseEntity<Object> createClass(@RequestBody ClassEntity classEntity) {
        // Set createdAt and updatedAt to the current date
        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);

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

    @GetMapping("/getspreadsheetbyclassid/{classId}")
    public ResponseEntity<Object> getSpreadsheetByClassId(@PathVariable int classId) {
        List<ClassSpreadsheet> spreadsheets = classService.getSpreadsheetsByClassId(classId);
        if (spreadsheets.isEmpty()) {
            return ResponseEntity.status(404).body("Class not found");
        } else {
            return ResponseEntity.status(200).body(spreadsheets);
        }
    }

    @GetMapping("/getclassbyteacherid/{teacherId}")
    public ResponseEntity<Object> getClassByTeacherId(@PathVariable int teacherId) {
        List<ClassEntity> classes = classService.getClassesByTeacherId(teacherId);
        if (classes.isEmpty()) {
            return ResponseEntity.status(404).body("No classes found for this teacher");
        } else {
            return ResponseEntity.status(200).body(classes);
        }
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