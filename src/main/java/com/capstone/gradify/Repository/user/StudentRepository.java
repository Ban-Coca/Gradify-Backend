package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Integer> {
    StudentEntity findByEmail(String email);
    Optional<StudentEntity> findByStudentNumber(String studentNumber);
    StudentEntity findByUserId(int userId);
}
