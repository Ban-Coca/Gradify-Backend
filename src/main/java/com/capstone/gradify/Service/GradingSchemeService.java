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

    public GradingSchemes getGradingSchemeByClassEntityId(int id) {
        GradingSchemes gradingSchemes = gradingSchemeRepository.findByClassEntity_ClassId(id);
        if (gradingSchemes == null) {
            throw new RuntimeException("Grading scheme not found for class ID: " + id);
        }
        return gradingSchemes;
    }

    public GradingSchemes updateGradingScheme(GradingSchemes updatedScheme, Integer classId, Integer teacherId) {
        // Find the existing grading scheme first
        GradingSchemes existingScheme = gradingSchemeRepository.findByClassEntity_ClassId(classId);
        if (existingScheme == null) {
            throw new RuntimeException("Grading scheme not found for class ID: " + classId);
        }

        // Update the grading scheme content
        existingScheme.setGradingScheme(updatedScheme.getGradingScheme());

        // Update teacher if provided
        if (teacherId != null) {
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found with ID: " + teacherId));
            existingScheme.setTeacherEntity(teacher);
        }

        // Save and return the updated entity
        return gradingSchemeRepository.save(existingScheme);
    }

    public String getGradeSchemeByClassEntityId(int id) {
        GradingSchemes gradingSchemes = gradingSchemeRepository.findByClassEntity_ClassId(id);
        if (gradingSchemes == null) {
            throw new RuntimeException("Grading scheme not found for class ID: " + id);
        }
        return gradingSchemes.getGradingScheme();
    }
}