package com.capstone.gradify.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.*;
import javax.naming.NameNotFoundException;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClassService {
    
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;
    @Autowired
    private TeacherRepository teacherRepository;

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
        }catch(NoSuchElementException nex){
			throw new NameNotFoundException("Class "+ classId +"not found");
		}finally {
			return classRepository.save(existingClass);
		}
    }
    public String deleteClass(int classId) {
        String msg = "";

        if(classRepository.findById(classId).isPresent()) {
			classRepository.deleteById(classId);
			msg = "Class record successfully deleted!";
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
    public List<ClassEntity> getClassesByTeacherId(int teacherId) {
        TeacherEntity teacherEntity = teacherRepository.findById(teacherId).orElse(null);
        return classRepository.findByTeacher(teacherEntity);
    }


}
