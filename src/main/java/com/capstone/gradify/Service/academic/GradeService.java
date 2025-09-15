package com.capstone.gradify.Service.academic;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradeService {
    private final GradeRecordRepository gradeRecordsRepository;
    private final ClassSpreadsheetRepository classSpreadsheetRepository;

    public GradeRecordsEntity getStudentVisibleGrades(String studentNumber, int classId) {

        GradeRecordsEntity originalRecord = gradeRecordsRepository.findByStudentNumberAndClassRecord_ClassEntity_ClassId(studentNumber, classId);

        if (originalRecord == null) {
            return null; // No record found for this student in this class
        }

        Set<String> visibleAssessments = originalRecord.getClassRecord().getVisibleAssessments();

        GradeRecordsEntity filteredRecord = createFilteredRecord(originalRecord, visibleAssessments);

        return filteredRecord;
    }

    public List<GradeRecordsEntity> getStudentVisibleGradesAllClasses(String studentNumber) {
        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByStudentNumber(studentNumber);
        return allRecords.stream()
                .map(record -> {
                    Set<String> visibleAssessments = record.getClassRecord().getVisibleAssessments();
                    return createFilteredRecord(record, visibleAssessments);
                })
                .collect(Collectors.toList());
    }

    private GradeRecordsEntity createFilteredRecord(GradeRecordsEntity original, Set<String> visibleAssessments) {
        GradeRecordsEntity filtered = new GradeRecordsEntity();
        filtered.setId(original.getId());
        filtered.setStudent(original.getStudent());
        filtered.setStudentNumber(original.getStudentNumber());
        filtered.setClassRecord(original.getClassRecord());

        Map<String, String> filteredGrades = original.getGrades().entrySet()
                .stream()
                .filter(entry -> shouldIncludeGrade(entry.getKey(), visibleAssessments))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        filtered.setGrades(filteredGrades);
        return filtered;
    }

    private boolean shouldIncludeGrade(String key, Set<String> visibleAssessments) {
        // If no visible assessments, include all grades
        if (isStudentInfoField(key)) {
            return true;
        }
        // Check if the key matches any of the visible assessments
        return visibleAssessments != null && visibleAssessments.contains(key);
    }

    private boolean isStudentInfoField(String fieldName) {
        return fieldName.equals("Last Name") ||
                fieldName.equals("First Name") ||
                fieldName.equals("Student Number") ||
                fieldName.equals("StudentNumber") ||
                fieldName.equals("Name") ||
                fieldName.equals("fullName");
    }

    public GradeRecordsEntity getTeacherViewGrades(int id) {
        return gradeRecordsRepository.findById(id).orElse(null);
    }

    public Set<String> getAvailableAssessments(int classId) {
        return classSpreadsheetRepository.findByClassEntity_ClassId(classId)
                .stream()
                .flatMap(spreadsheet -> spreadsheet.getAssessmentMaxValues().keySet().stream())
                .collect(Collectors.toSet());
    }

    public Set<String> getVisibleAssessments(int classId) {
        return classSpreadsheetRepository.findByClassEntity_ClassId(classId)
                .stream()
                .flatMap(spreadsheet -> spreadsheet.getVisibleAssessments().stream())
                .collect(Collectors.toSet());
    }

    public void updateAssessmentVisibility(Long classSpreadsheetId, Set<String> visibleAssessments) {
        ClassSpreadsheet spreadsheet = classSpreadsheetRepository.findById(classSpreadsheetId)
                .orElseThrow(() -> new EntityNotFoundException("Class spreadsheet not found"));

        // Validate that all visible assessments exist in the spreadsheet
        Set<String> availableAssessments = spreadsheet.getAssessmentMaxValues().keySet();
        Set<String> invalidAssessments = visibleAssessments.stream()
                .filter(assessment -> !availableAssessments.contains(assessment))
                .collect(Collectors.toSet());

        if (!invalidAssessments.isEmpty()) {
            throw new IllegalArgumentException("Invalid assessments: " + invalidAssessments);
        }

        spreadsheet.setVisibleAssessments(visibleAssessments);
        classSpreadsheetRepository.save(spreadsheet);
    }

    public void toggleAssessmentVisibility(Long classSpreadsheetId, String assessmentName) {
        ClassSpreadsheet spreadsheet = classSpreadsheetRepository.findById(classSpreadsheetId)
                .orElseThrow(() -> new EntityNotFoundException("Class spreadsheet not found"));

        Set<String> visibleAssessments = spreadsheet.getVisibleAssessments();
        if (visibleAssessments == null) {
            visibleAssessments = new HashSet<>();
        }

        if (visibleAssessments.contains(assessmentName)) {
            visibleAssessments.remove(assessmentName);
        } else {
            // Validate assessment exists
            if (!spreadsheet.getAssessmentMaxValues().containsKey(assessmentName)) {
                throw new IllegalArgumentException("Assessment not found: " + assessmentName);
            }
            visibleAssessments.add(assessmentName);
        }

        spreadsheet.setVisibleAssessments(visibleAssessments);
        classSpreadsheetRepository.save(spreadsheet);
    }

}
