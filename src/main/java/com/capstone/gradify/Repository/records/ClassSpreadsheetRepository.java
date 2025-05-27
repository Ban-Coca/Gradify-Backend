package com.capstone.gradify.Repository.records;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
