package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.dto.response.StudentDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;

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

    public StudentEntity getStudentDetail(int classId, int studentId) {
        ClassEntity classEntity = getClassById(classId);
        if (classEntity == null) {
            throw new EntityNotFoundException("Class not found");
        }
        StudentEntity studentEntity = studentRepository.findById(studentId).orElse(null);
        if (studentEntity == null) {
            throw new EntityNotFoundException("Student not found");
        }
        Set<StudentEntity> enrolledStudents = classEntity.getStudents();
        if (enrolledStudents == null || !enrolledStudents.contains(studentEntity)) {
            throw new RuntimeException("Student is not enrolled in the specified class");
        }
        return studentEntity;
    }
}
