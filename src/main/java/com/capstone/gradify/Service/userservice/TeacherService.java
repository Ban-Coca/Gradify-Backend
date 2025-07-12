package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final ClassRepository classRepository;

    public TeacherEntity save(TeacherEntity teacher) {
        return teacherRepository.save(teacher);
    }
    public TeacherEntity findByUserId(int userId) {
        return teacherRepository.findByUserId(userId);
    }

    private ClassEntity getClassById(int classId) {
        return classRepository.findById(classId).orElse(null);
    }

    public String getTeacherFullNameByClassId(int classId) {
        ClassEntity classEntity = getClassById(classId);
        if (classEntity != null && classEntity.getTeacher() != null) {
            TeacherEntity teacher = classEntity.getTeacher();
            return teacher.getFirstName() + " " + teacher.getLastName();
        }
        return null;
    }
}
