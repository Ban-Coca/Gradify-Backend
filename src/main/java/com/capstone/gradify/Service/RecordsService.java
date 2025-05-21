package com.capstone.gradify.Service;

import com.capstone.gradify.Entity.records.*;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.capstone.gradify.Entity.user.StudentEntity;

import java.util.*;

@Service
public class RecordsService {
    @Autowired
    private GradeRecordRepository gradeRecordsRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private GradingSchemeService gradingSchemeService;
    @Autowired
    private ClassService classService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * DTO for student table data
     */
    public static class StudentTableData {
        private String studentName;
        private String studentNumber;
        private String grade;
        private double percentage;
        private String status;
        private int userId;
        public StudentTableData(String studentName, String studentNumber, String grade, double percentage, String status, int userId) {
            this.studentName = studentName;
            this.studentNumber = studentNumber;
            this.grade = grade;
            this.percentage = percentage;
            this.status = status;
            this.userId = userId;
        }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public String getStudentNumber() { return studentNumber; }
        public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
    }

    public List<StudentTableData> getClassRosterTableData(int classId) {
        // Get all grade records for this class
        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

        // Get grading scheme for this class
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        // Calculate grades and collect student data
        List<StudentTableData> tableData = new ArrayList<>();

        for (GradeRecordsEntity record : allRecords) {
            // Calculate numerical grade
            double percentageGrade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), record.getClassRecord().getAssessmentMaxValues());

            // Get student information
            String studentNumber = record.getStudentNumber();
            Optional<StudentEntity> studentOpt = studentRepository.findByStudentNumber(studentNumber);

            if (studentOpt.isPresent()) {
                StudentEntity student = studentOpt.get();
                String studentName = student.getFirstName() + " " + student.getLastName();

                // Determine letter grade based on percentage
                String letterGrade = convertToLetterGrade(percentageGrade);

                // Determine status based on grade
                String status = determineStatus(percentageGrade);

                // Create table data object
                StudentTableData data = new StudentTableData(
                        studentName,
                        studentNumber,
                        letterGrade,
                        percentageGrade,
                        status,
                        student.getUserId()
                );

                tableData.add(data);
            }
        }

        return tableData;
    }

    /**
     * Convert numeric percentage to letter grade
     */
    private String convertToLetterGrade(double percentage) {
        if (percentage >= 90) return "A";
        else if (percentage >= 80) return "B";
        else if (percentage >= 70) return "C";
        else if (percentage >= 60) return "D";
        else return "F";
    }

    /**
     * Determine student status based on grade
     */
    private String determineStatus(double percentage) {
        if (percentage >= 80) return "Good Standing";
        else if (percentage >= 70) return "Passing";
        else if (percentage >= 60) return "At Risk";
        else return "Failing";
    }

    public double calculateStudentGrade(int studentNumber, int classId) {
        // Get student's grade records for this class
        List<GradeRecordsEntity> records = gradeRecordsRepository.findByStudent_UserIdAndClassRecord_ClassEntity_ClassId(studentNumber, classId);

        if (records.isEmpty()) {
            throw new RuntimeException("No grade records found for student ID: " + studentNumber + " in class ID: " + classId);
        }

        // Use the first record found
        GradeRecordsEntity record = records.get(0);

        // Get grading scheme for this class
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        // Calculate the grade based on the grading scheme
        return calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), record.getClassRecord().getAssessmentMaxValues());
    }

    public Map<String, Double> calculateClassGrades(int classId) {
        // Get all grade records for this class
        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

        // Get grading scheme for this class
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        // Calculate grade for each student
        Map<String, Double> studentGrades = new HashMap<>();
        for (GradeRecordsEntity record : allRecords) {
            double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), record.getClassRecord().getAssessmentMaxValues());
            String studentNumber = record.getStudentNumber();
            studentGrades.put(studentNumber, grade);
        }

        return studentGrades;
    }

    public double calculateGrade(Map<String, String> grades, String schemeJson, Map<String, Integer> assessmentMaxValues) {
        try {
            // Parse the grading scheme from JSON
            List<Map<String, Object>> schemeItems = mapper.readValue(
                    schemeJson, new TypeReference<List<Map<String, Object>>>() {});

            double totalGrade = 0.0;
            double totalAppliedWeight = 0.0;

            for (Map<String, Object> schemeItem : schemeItems) {
                String name = (String) schemeItem.get("name");
                double weight = Double.parseDouble(schemeItem.get("weight").toString());

                // Map scheme items to their corresponding grade records
                double categoryScore = getCategoryScore(grades, name, assessmentMaxValues);

                if (categoryScore >= 0) { // Only include if we found a valid score
                    totalGrade += (categoryScore * (weight / 100.0));
                    totalAppliedWeight += weight;
                }
            }

            // Normalize by the total applied weight if not all categories were found
            if (totalAppliedWeight > 0) {
                return (totalGrade / (totalAppliedWeight / 100)) * 100;
            } else {
                return 0.0;
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing grading scheme: " + e.getMessage());
        }
    }

    private double getCategoryScore(Map<String, String> grades, String category, Map<String, Integer> assessmentMaxValues) {
        // Map category names to potential keys in the grade records
        category = category.trim();
        Map<String, Integer> maxValues = assessmentMaxValues != null ? assessmentMaxValues : new HashMap<>();
        // Handle common category mappings
        if (category.equalsIgnoreCase("Quizzes")) {
            // Look for quiz-related keys (Q1, Q2, etc.)
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("Q\\d+") || key.toLowerCase().startsWith("quiz")) {
                    try {
                        double score = Double.parseDouble(entry.getValue());
                        int maxValue = maxValues.getOrDefault(key, 100);
                        total += (score / maxValue) * 100;
                        count++;
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }

            return count > 0 ? total / count : -1;
        } else if (category.equalsIgnoreCase("Midterm Exam")) {
            // Look for midterm keys
            if (parseGradeValue(grades.get("ME")) != -1) {
                double score = parseGradeValue(grades.get("ME"));
                int maxValue = maxValues.getOrDefault("ME", 100);
                return (score / maxValue) * 100;
            } else if (parseGradeValue(grades.get("Midterm")) != -1) {
                double score = parseGradeValue(grades.get("Midterm"));
                int maxValue = maxValues.getOrDefault("Midterm", 100);
                return (score / maxValue) * 100;
            } else if (parseGradeValue(grades.get("Midterm Exam")) != -1) {
                double score = parseGradeValue(grades.get("Midterm Exam"));
                int maxValue = maxValues.getOrDefault("Midterm Exam", 100);
                return (score / maxValue) * 100;
            }
            return -1;
        } else if (category.equalsIgnoreCase("Final Exam")) {
            // Look for final exam keys
            if (parseGradeValue(grades.get("FE")) != -1) {
                double score = parseGradeValue(grades.get("FE"));
                int maxValue = maxValues.getOrDefault("FE", 100);
                return (score / maxValue) * 100;
            } else if (parseGradeValue(grades.get("Final")) != -1) {
                double score = parseGradeValue(grades.get("Final"));
                int maxValue = maxValues.getOrDefault("Final", 100);
                return (score / maxValue) * 100;
            } else if (parseGradeValue(grades.get("Final Exam")) != -1) {
                double score = parseGradeValue(grades.get("Final Exam"));
                int maxValue = maxValues.getOrDefault("Final Exam", 100);
                return (score / maxValue) * 100;
            }
        return -1;
    } else {
        // For other categories, try exact match
        for (Map.Entry<String, String> entry : grades.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) {
                double score = parseGradeValue(entry.getValue());
                int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                return (score / maxValue) * 100;
            }
        }

            // If no match found, return -1 to indicate missing category
            return -1;
        }
    }

    public double calculateClassAverageGrade(int classId) {
        // Get all grade records for this class
        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

        if (allRecords.isEmpty()) {
            return 0.0; // No records found, return 0
        }

        // Get grading scheme for this class
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        // Calculate grade for each student and sum them
        double totalGrades = 0.0;
        int studentCount = 0;

        for (GradeRecordsEntity record : allRecords) {
            double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), record.getClassRecord().getAssessmentMaxValues());
            totalGrades += grade;
            studentCount++;
        }

        // Return the average
        return studentCount > 0 ? totalGrades / studentCount : 0.0;
    }
    public int getStudentCount(int classId) {

        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);
        return allRecords.size();
    }
    private double parseGradeValue(String value) {
        if (value == null) return -1;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    public List<GradeRecordsEntity> getGradeRecordsByStudentId(int studentId) {
        return gradeRecordsRepository.findByStudent_UserId(studentId);
    }

    public Map<String, String> getStudentCourseGrades(int studentId, int classId) {
        List<GradeRecordsEntity> gradeRecords = gradeRecordsRepository.findByStudent_UserIdAndClassRecord_ClassEntity_ClassId(studentId, classId);

        if (!gradeRecords.isEmpty() && gradeRecords.get(0).getGrades() != null) {
            return gradeRecords.get(0).getGrades();
        }
        return Collections.emptyMap();
    }

    public List<GradeRecordsEntity> getGradeRecordsByStudentIdAndClassId(int studentId, int classId) {
        return gradeRecordsRepository.findByStudent_UserIdAndClassRecord_ClassEntity_ClassId(studentId, classId);
    }

    public int countAtRiskStudents(int teacherId) {
        // Find all classes taught by this teacher
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        // Track unique at-risk students by student number
        Set<String> uniqueAtRiskStudents = new HashSet<>();

        // Check each class taught by this teacher
        for (ClassEntity classEntity : teacherClasses) {
            int classId = classEntity.getClassId();
            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

            // Get grading scheme for this class
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            for (GradeRecordsEntity record : classRecords) {
                double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(),
                        record.getClassRecord().getAssessmentMaxValues());
                if (grade < 60 && record.getStudentNumber() != null) {
                    uniqueAtRiskStudents.add(record.getStudentNumber());
                }
            }
        }

        return uniqueAtRiskStudents.size();
    }


    public int countTopPerformingStudents(int teacherId) {
        // Find all classes taught by this teacher
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        // Track unique top-performing students by student number
        Set<String> uniqueTopStudents = new HashSet<>();

        // Check each class taught by this teacher
        for (ClassEntity classEntity : teacherClasses) {
            int classId = classEntity.getClassId();
            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

            // Get grading scheme for this class
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            for (GradeRecordsEntity record : classRecords) {
                double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(),
                        record.getClassRecord().getAssessmentMaxValues());
                if (grade >= 80 && record.getStudentNumber() != null) {
                    uniqueTopStudents.add(record.getStudentNumber());
                }
            }
        }

        return uniqueTopStudents.size();
    }

    public int getStudentCountByTeacher(int teacherId) {
        // First, find all classes taught by this teacher
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        // Use a Set to track unique students by their student number
        Set<String> uniqueStudentNumbers = new HashSet<>();

        // For each class, get all students and add them to the set
        for (ClassEntity classEntity : teacherClasses) {
            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classEntity.getClassId());

            for (GradeRecordsEntity record : classRecords) {
                if (record.getStudentNumber() != null) {
                    uniqueStudentNumbers.add(record.getStudentNumber());
                }
            }
        }

        // Return the count of unique students
        return uniqueStudentNumbers.size();
    }
}