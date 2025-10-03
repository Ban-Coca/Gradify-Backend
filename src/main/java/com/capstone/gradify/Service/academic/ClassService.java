package com.capstone.gradify.Service.academic;

import java.util.Date;
import java.util.List;
import java.util.*;
import javax.naming.NameNotFoundException;

import com.capstone.gradify.Entity.report.ReportEntity;
import com.capstone.gradify.Entity.records.*;
import com.capstone.gradify.Entity.subscription.TrackedFiles;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Repository.report.ReportRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.subscription.TrackedFileRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.dto.request.UpdateClassDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository classRepository;
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final TeacherRepository teacherRepository;
    private final ReportRepository reportRepository;
    private final GradingSchemeRepository gradingSchemeRepository;
    private final TrackedFileRepository trackedFileRepository;

    public ClassEntity createClass(ClassEntity classEntity) {
        return classRepository.save(classEntity);
    }
    public ClassEntity getClassById(int id) {
        return classRepository.findById(id).orElse(null);
    }
    public List<ClassEntity> getAllClasses() {
        return classRepository.findAll();
    }
    public ClassEntity updateClass(int classId, UpdateClassDetails updateRequest) throws Exception {
        ClassEntity existingClass = classRepository.findById(classId)
                .orElseThrow(() -> new NameNotFoundException("Class " + classId + " not found"));

        // Only update non-null/non-empty fields
        if (updateRequest.getClassName() != null && !updateRequest.getClassName().trim().isEmpty()) {
            existingClass.setClassName(updateRequest.getClassName().trim());
        }
        if (updateRequest.getClassCode() != null && !updateRequest.getClassCode().trim().isEmpty()) {
            existingClass.setClassCode(updateRequest.getClassCode().trim());
        }
        if (updateRequest.getSemester() != null && !updateRequest.getSemester().trim().isEmpty()) {
            existingClass.setSemester(updateRequest.getSemester().trim());
        }
        if (updateRequest.getSchoolYear() != null && !updateRequest.getSchoolYear().trim().isEmpty()) {
            existingClass.setSchoolYear(updateRequest.getSchoolYear().trim());
        }
        if (updateRequest.getSection() != null && !updateRequest.getSection().trim().isEmpty()) {
            existingClass.setSection(updateRequest.getSection().trim());
        }
        if (updateRequest.getSchedule() != null && !updateRequest.getSchedule().trim().isEmpty()) {
            existingClass.setSchedule(updateRequest.getSchedule().trim());
        }
        if (updateRequest.getRoom() != null && !updateRequest.getRoom().trim().isEmpty()) {
            existingClass.setRoom(updateRequest.getRoom().trim());
        }

        existingClass.setUpdatedAt(new Date());
        return classRepository.save(existingClass);
    }
    @Transactional
    public String deleteClass(int classId) {
        if (classRepository.findById(classId).isEmpty()) {
            return "Class ID " + classId + " NOT FOUND!";
        }

        try{
            reportRepository.deleteByRelatedClass_ClassId(classId);
            ClassEntity existingClass = classRepository.findById(classId).orElse(null);

            List<ClassSpreadsheet> spreadsheets = classSpreadsheetRepository.findByClassEntity(existingClass);
            if(!spreadsheets.isEmpty()){
                List<TrackedFiles> tracked = trackedFileRepository.findBySpreadsheetIn(spreadsheets);
                if(!tracked.isEmpty()){
                    trackedFileRepository.deleteAll(tracked);
                }

                classSpreadsheetRepository.deleteAll(spreadsheets);
            }

            GradingSchemes gradingSchemes = gradingSchemeRepository.findByClassEntity_ClassId(classId);
            if(gradingSchemes != null){
                gradingSchemeRepository.delete(gradingSchemes);
            }

            classRepository.deleteById(classId);
            return "Class record successfully deleted!";
        }catch (Exception e){
            return "Error deleting class record: " + e.getMessage();
        }
    }

    public List<ClassSpreadsheet> getSpreadsheetsByClassId(int classId) {
        ClassEntity classEntity = getClassById(classId);
        if (classEntity != null) {
            return classSpreadsheetRepository.findByClassEntity(classEntity);
        }
        return new ArrayList<>();
    }

    public Set<StudentEntity> getStudentsByClassId(int classId) {
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Class not found with id: " + classId));

        return classEntity.getStudents();
    }

    public List<ClassEntity> getClassesByTeacherId(int teacherId) {
        TeacherEntity teacherEntity = teacherRepository.findById(teacherId).orElse(null);
        return classRepository.findByTeacher(teacherEntity);
    }
}
