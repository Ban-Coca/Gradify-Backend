package com.capstone.gradify.Repository.records;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface ClassSpreadsheetRepository extends JpaRepository<ClassSpreadsheet, Long> {
    List<ClassSpreadsheet> findByUploadedBy(TeacherEntity teacher);
    List<ClassSpreadsheet> findByClassEntity(ClassEntity classEntity);
    List<ClassSpreadsheet> findByFileName(String fileName);
    List<ClassSpreadsheet> findByClassEntity_ClassId(Integer classId);
    List<ClassSpreadsheet> findByUploadedBy_UserId(int userId);
    List<ClassSpreadsheet> findByItemIdIsNotNull();
    List<ClassSpreadsheet> findByIsGoogleSheetsAndSharedLinkIsNotNull(Boolean isGoogleSheets);
    Optional<ClassSpreadsheet> findByItemId(String itemId);
    List<ClassSpreadsheet> findByUploadedBy_UserIdAndItemIdIsNotNull(Integer userId);
    @Query("SELECT cs FROM ClassSpreadsheet cs LEFT JOIN FETCH cs.gradeRecords WHERE cs.id = :id")
    Optional<ClassSpreadsheet> findByIdWithGradeRecords(@Param("id") Long id);
}
