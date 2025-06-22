package com.capstone.gradify.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.*;
import javax.naming.NameNotFoundException;

import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.records.*;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Repository.ReportRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClassService {
    
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private GradingSchemeRepository gradingSchemeRepository;
    public ClassEntity createClass(ClassEntity classEntity) {
        return classRepository.save(classEntity);
    }
    public ClassEntity getClassById(int id) {
        return classRepository.findById(id).orElse(null);
    }
    public List<ClassEntity> getAllClasses() {
        return classRepository.findAll();
    }
    public ClassEntity updateClass(int classId, ClassEntity newclassEntity) {
        ClassEntity existingClass = classRepository.findById(classId).orElse(null);
        try{
            existingClass.setClassName(newclassEntity.getClassName());
            existingClass.setClassCode(newclassEntity.getClassCode());
            existingClass.setSemester(newclassEntity.getSemester());
            existingClass.setSchoolYear(newclassEntity.getSchoolYear());
            existingClass.setUpdatedAt(new Date());
            existingClass.setSection(newclassEntity.getSection());
            existingClass.setSchedule(newclassEntity.getSchedule());
            existingClass.setRoom(newclassEntity.getRoom());
        }catch(NoSuchElementException nex){
			throw new NameNotFoundException("Class "+ classId +"not found");
		}finally {
			return classRepository.save(existingClass);
		}
    }
    public String deleteClass(int classId) {
        ClassEntity classEntity = classRepository.findById(classId).orElse(null);
        String msg = "";

        if(classRepository.findById(classId).isPresent()) {
            try{
                GradingSchemes gradingSchemes = gradingSchemeRepository.findByClassEntity_ClassId(classId);
                if (gradingSchemes != null) {
                    gradingSchemeRepository.delete(gradingSchemes);
                }
                List<ReportEntity> reports = reportRepository.findByRelatedClassClassId(classId);
                if (!reports.isEmpty()) {
                    reportRepository.deleteAll(reports);
                }
                List<ClassSpreadsheet> classSpreadsheets = classSpreadsheetRepository.findByClassEntity(classEntity);
                if(!classSpreadsheets.isEmpty()){
                    classSpreadsheetRepository.deleteAll(classSpreadsheets);
                }
                classRepository.deleteById(classId);
                msg = "Class record successfully deleted!";
            } catch (Exception e) {
                msg = "Error deleting class record: " + e.getMessage();
            }

		}else {
			msg = "Class ID "+ classId +" NOT FOUND!";
		}
		return msg;
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

    public void addStudentToClass(ClassEntity classEntity, StudentEntity studentEntity) {
            classEntity.getStudents().add(studentEntity);
            classRepository.save(classEntity);
    }
}
