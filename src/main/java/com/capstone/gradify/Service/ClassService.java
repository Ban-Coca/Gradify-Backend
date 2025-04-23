package com.capstone.gradify.Service;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import javax.naming.NameNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Repository.records.ClassRepository;

@Service
public class ClassService {
    
    @Autowired
    private ClassRepository classRepository;
    
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
            existingClass.setRoom(newclassEntity.getRoom());
            existingClass.setUpdatedAt(new Date());
            existingClass.setSection(newclassEntity.getSection());
            existingClass.setSchedule(newclassEntity.getSchedule());
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
}
