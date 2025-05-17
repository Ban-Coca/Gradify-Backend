package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Integer> {
    StudentEntity findByEmail(String email);
    List<StudentEntity> findByYearLevel(String yearLevel);
    List<StudentEntity> findByMajor(String major);
    List<StudentEntity> findByInstitution(String institution);
    Optional<StudentEntity> findByStudentNumber(String studentNumber);
    StudentEntity findByUserId(int userId);
}
