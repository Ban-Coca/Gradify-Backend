package com.capstone.gradify.Repository;

import com.capstone.gradify.Entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Integer> {
    List<ReportEntity> findByStudentUserId(int studentId);

    // Find reports created by a teacher
    List<ReportEntity> findByTeacherUserId(int teacherId);

    // Find reports for a specific class
    List<ReportEntity> findByRelatedClassClassId(int classId);

    // Find reports for a student in a specific class
    List<ReportEntity> findByStudentUserIdAndRelatedClassClassId(int studentId, int classId);

    // Find reports linked to a specific grade record
    List<ReportEntity> findByGradeRecordId(Long gradeRecordId);
}
