package com.capstone.gradify.Service;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GradingSchemeService {
    @Autowired
    private GradingSchemeRepository gradingSchemeRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private ClassRepository classRepository;
    public GradingSchemes saveGradingScheme(GradingSchemes gradingScheme, Integer classId, Integer teacherId) {
        TeacherEntity teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found with ID: " + teacherId));

        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found with ID: " + classId));

        // Associate the grading scheme with the teacher and class
        gradingScheme.setTeacherEntity(teacher);
        gradingScheme.setClassEntity(classEntity);
        return gradingSchemeRepository.save(gradingScheme);
    }
}
