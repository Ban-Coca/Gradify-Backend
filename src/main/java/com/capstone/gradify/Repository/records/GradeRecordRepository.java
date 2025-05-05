package com.capstone.gradify.Repository.records;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
@Repository
public interface GradeRecordRepository extends JpaRepository<GradeRecordsEntity, Integer> {
    // Custom query methods can be defined here if needed
    // For example, you can add methods to find records by student, class, etc.
    // Example: List<GradeRecordEntity> findByStudentId(Integer studentId);
}
