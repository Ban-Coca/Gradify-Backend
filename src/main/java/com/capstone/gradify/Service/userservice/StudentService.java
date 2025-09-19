package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final StudentRepository studentRepository;

    public StudentEntity save(StudentEntity teacher) {
        return studentRepository.save(teacher);
    }
    public StudentEntity findByUserId(int userId) {
        return studentRepository.findByUserId(userId);
    }
    public String getEmailById(int id) {
        StudentEntity student = studentRepository.findByUserId(id);
        return student.getEmail();
    }
}
