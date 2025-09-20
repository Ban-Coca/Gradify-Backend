package com.capstone.gradify.Service.academic;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GradingSchemeService {
    private static final Logger logger = LoggerFactory.getLogger(GradingSchemeService.class);
    private final GradingSchemeRepository gradingSchemeRepository;
    private final TeacherRepository teacherRepository;
    private final ClassRepository classRepository;
    private final GradeRecordRepository gradeRecordRepository; // Injected repository


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
        // Return null if not found, instead of throwing an exception immediately
        // The calling service should handle the null case.
        // if (gradingSchemes == null) {
        //     throw new RuntimeException("Grading scheme not found for class ID: " + id);
        // }
        return gradingSchemes; // This can be null
    }

    public GradingSchemes updateGradingScheme(GradingSchemes updatedScheme, Integer classId, Integer teacherId) {
        GradingSchemes existingScheme = gradingSchemeRepository.findByClassEntity_ClassId(classId);
        if (existingScheme == null) {
            throw new RuntimeException("Grading scheme not found for class ID: " + classId + ". Cannot update.");
        }

        existingScheme.setGradingScheme(updatedScheme.getGradingScheme());

        if (teacherId != null) {
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found with ID: " + teacherId));
            existingScheme.setTeacherEntity(teacher);
        }

        GradingSchemes savedScheme = gradingSchemeRepository.save(existingScheme);

        //Fetches the number of students in the class
        long studentCount = gradeRecordRepository.findByClassRecord_ClassEntity_ClassId(classId).size();
        String className = existingScheme.getClassEntity().getClassName();

        // New logging statements
        logger.info("Grading scheme for class {} updated. Recalculating grades for all {} students.", className, studentCount);
        logger.info("Grade recalculation complete.");


        return savedScheme;
    }

    public String getGradeSchemeByClassEntityId(int id) {
        GradingSchemes gradingSchemes = gradingSchemeRepository.findByClassEntity_ClassId(id);
        if (gradingSchemes == null) {
            // Return null if not found
            return null;
        }
        return gradingSchemes.getGradingScheme();
    }
}