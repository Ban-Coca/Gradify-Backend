package com.capstone.gradify.Repository.records;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;

import java.util.List;

@Repository
public interface GradeRecordRepository extends JpaRepository<GradeRecordsEntity, Integer> {
    List<GradeRecordsEntity> findByClassRecord_ClassEntity_ClassId(int classId);
    List<GradeRecordsEntity> findByStudent_UserIdAndClassRecord_ClassEntity_ClassId(int studentId, int classId);
    List<GradeRecordsEntity> findByStudent_UserId(int studentId);
}
