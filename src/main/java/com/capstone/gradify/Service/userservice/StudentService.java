package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StudentService {
    @Autowired
    private StudentRepository studentRepository;

//    @Transactional
//    public StudentEntity registerStudentAccount(String studentNumber, String major, String yearLevel) {
//        Optional<StudentEntity> existingStudent = studentRepository.findByStudentNumber(studentNumber);
//        if (existingStudent.isPresent()) {
//            // Update existing student with account details
//            StudentEntity student = existingStudent.get();
//            student.setEmail(email);
//            student.setPassword(passwordEncoder.encode(password));
//            student.setName(name);
//            // Set other account fields as needed
//            return studentRepository.save(student);
//        } else {
//            // Create new student account
//            StudentEntity newStudent = new StudentEntity();
//            newStudent.setEmail(email);
//            newStudent.setPassword(passwordEncoder.encode(password));
//            newStudent.setName(name);
//            newStudent.setStudentNumber(studentNumber);
//            // Set other required fields
//            return studentRepository.save(newStudent);
//        }
//    }
}
