package com.capstone.gradify.Repository.records;

import com.capstone.gradify.Entity.user.TeacherEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.capstone.gradify.Entity.records.ClassEntity;

import java.util.List;

@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {
    ClassEntity findByClassCode(String classCode);
    List<ClassEntity> findByTeacher(TeacherEntity teacher);
    List<ClassEntity> findBySchoolYearAndSemester(String schoolYear, String semester);

}
