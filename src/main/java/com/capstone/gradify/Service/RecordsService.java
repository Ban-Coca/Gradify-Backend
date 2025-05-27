package com.capstone.gradify.Service;

import com.capstone.gradify.Entity.records.*;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
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
            double grade = percentageGrade / 100; // Convert to percentage
            // Get student information
            String studentNumber = record.getStudentNumber();
            Optional<StudentEntity> studentOpt = studentRepository.findByStudentNumber(studentNumber);

            if (studentOpt.isPresent()) {
                StudentEntity student = studentOpt.get();
                String studentName = student.getFirstName() + " " + student.getLastName();

                // Determine letter grade based on percentage
                String letterGrade = convertToLetterGrade(grade);

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
                totalAppliedWeight += weight;
                // Map scheme items to their corresponding grade records
                double categoryScore = getCategoryScore(grades, name, assessmentMaxValues);

                if (categoryScore >= 0) { // Only include if we found a valid score
                    totalGrade += (categoryScore * (weight / 100.0));
                } else {
                    // Missing category - count as zero instead of ignoring
                    totalGrade += 0;  // This is effectively adding nothing, but makes the intention clear
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

        // Handle common category mappings with multiple variations
        if (category.equalsIgnoreCase("Quizzes") || category.equalsIgnoreCase("Quiz") ||
                category.equalsIgnoreCase("Quizes") || category.equalsIgnoreCase("Short Quiz") ||
                category.equalsIgnoreCase("Pop Quiz") || category.equalsIgnoreCase("Weekly Quiz")) {
            // Look for quiz-related keys with extensive variations
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.matches("Q\\d+") || key.matches("q\\d+") || key.matches("quiz\\d+") || key.matches("qz\\d+") ||
                        key.startsWith("quiz") || key.contains("quiz") || key.startsWith("q ") ||
                        key.equals("q") || key.contains("short quiz") || key.contains("pop quiz") ||
                        key.contains("weekly quiz") || key.contains("daily quiz") || key.matches("sq\\d+")) {
                    try {
                        double score = Double.parseDouble(entry.getValue());
                        int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                        total += (score / maxValue) * 100;
                        count++;
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
            return count > 0 ? total / count : -1;

        } else if (category.equalsIgnoreCase("Assignments") || category.equalsIgnoreCase("Assignment") ||
                category.equalsIgnoreCase("Homework") || category.equalsIgnoreCase("Homeworks") ||
                category.equalsIgnoreCase("Task") || category.equalsIgnoreCase("Tasks") ||
                category.equalsIgnoreCase("Exercise") || category.equalsIgnoreCase("Exercises") ||
                category.equalsIgnoreCase("Activity") || category.equalsIgnoreCase("Activities")) {
            // Look for assignment-related keys with extensive variations
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.matches("A\\d+") || key.matches("a\\d+") || key.matches("assignment\\d+") || key.matches("assign\\d+") ||
                        key.matches("hw\\d+") || key.matches("homework\\d+") || key.matches("h\\d+") ||
                        key.matches("task\\d+") || key.matches("t\\d+") || key.matches("exercise\\d+") ||
                        key.matches("ex\\d+") || key.matches("activity\\d+") || key.matches("act\\d+") ||
                        key.startsWith("assignment") || key.startsWith("assign") || key.startsWith("homework") ||
                        key.startsWith("task") || key.startsWith("exercise") || key.startsWith("activity") ||
                        key.contains("assignment") || key.contains("homework") || key.contains("task") ||
                        key.contains("exercise") || key.contains("activity") || key.startsWith("hw") ||
                        key.equals("a") || key.equals("h") || key.equals("t") || key.contains("seatwork") ||
                        key.contains("classwork") || key.contains("work")) {
                    try {
                        double score = Double.parseDouble(entry.getValue());
                        int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                        total += (score / maxValue) * 100;
                        count++;
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
            return count > 0 ? total / count : -1;

        } else if (category.equalsIgnoreCase("Projects") || category.equalsIgnoreCase("Project") ||
                category.equalsIgnoreCase("Group Project") || category.equalsIgnoreCase("Group Projects") ||
                category.equalsIgnoreCase("Individual Project") || category.equalsIgnoreCase("Team Project") ||
                category.equalsIgnoreCase("Mini Project") || category.equalsIgnoreCase("Major Project")) {
            // Look for project-related keys with extensive variations
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.matches("p\\d+") || key.matches("project\\d+") || key.matches("proj\\d+") ||
                        key.matches("gp\\d+") || key.matches("group\\d+") || key.matches("team\\d+") ||
                        key.matches("mini\\d+") || key.matches("major\\d+") || key.matches("mp\\d+") ||
                        key.startsWith("project") || key.startsWith("proj") || key.contains("project") ||
                        key.contains("group project") || key.contains("team project") ||
                        key.contains("individual project") || key.contains("mini project") ||
                        key.contains("major project") || key.startsWith("group") || key.startsWith("team") ||
                        key.equals("p") || key.contains("final project") || key.contains("capstone project")) {
                    try {
                        double score = Double.parseDouble(entry.getValue());
                        int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                        total += (score / maxValue) * 100;
                        count++;
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
            return count > 0 ? total / count : -1;

        } else if (category.equalsIgnoreCase("Participation") || category.equalsIgnoreCase("Class Participation") ||
                category.equalsIgnoreCase("Attendance") || category.equalsIgnoreCase("Engagement") ||
                category.equalsIgnoreCase("Class Work") || category.equalsIgnoreCase("Classwork") ||
                category.equalsIgnoreCase("Class Engagement") || category.equalsIgnoreCase("Active Participation") ||
                category.equalsIgnoreCase("Discussion") || category.equalsIgnoreCase("Class Discussion")) {
            // Look for participation-related keys with extensive variations
            String[] participationKeys = {"Participation", "Class Participation", "CP", "Attendance",
                    "Class Work", "Classwork", "CW", "Engagement", "Class Engagement",
                    "Active Participation", "Discussion", "Class Discussion", "Disc",
                    "Participate", "Attend", "Present", "Active", "Involve", "Interaction"};
            for (String key : participationKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }

            // Check for abbreviated or partial matches
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("participation") || key.contains("attendance") ||
                        key.contains("engagement") || key.contains("discussion") ||
                        key.equals("cp") || key.equals("cw") || key.equals("disc") ||
                        key.contains("classwork") || key.contains("class work")) {
                    double score = parseGradeValue(entry.getValue());
                    int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Prelim Exam") || category.equalsIgnoreCase("Preliminary Exam") ||
                category.equalsIgnoreCase("Prelim") || category.equalsIgnoreCase("Preliminary") ||
                category.equalsIgnoreCase("Pre Exam") || category.equalsIgnoreCase("Pre-Exam") ||
                category.equalsIgnoreCase("Prelims") || category.equalsIgnoreCase("First Exam") ||
                category.equalsIgnoreCase("1st Exam") || category.equalsIgnoreCase("Exam 1")) {
            // Look for preliminary exam keys with extensive variations
            String[] prelimKeys = {"PE", "Prelim", "Preliminary", "Prelim Exam", "Preliminary Exam",
                    "Pre-Exam", "Pre Exam", "Prelims", "First Exam", "1st Exam",
                    "Exam 1", "Exam1", "E1", "PreE", "PrelE", "Initial Exam"};
            for (String key : prelimKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }

            // Check for pattern matches
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("prelim") || key.contains("preliminary") ||
                        key.contains("first exam") || key.contains("1st exam") ||
                        key.equals("pe") || key.equals("e1") || key.contains("initial exam")) {
                    double score = parseGradeValue(entry.getValue());
                    int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Pre-Final Exam") || category.equalsIgnoreCase("Prefinal Exam")) {
            // Look for pre-final exam keys
            String[] preFinalKeys = {"PFE", "Pre-Final", "Prefinal", "Pre-Final Exam", "Prefinal Exam"};
            for (String key : preFinalKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Midterm Exam") || category.equalsIgnoreCase("Midterm") ||
                category.equalsIgnoreCase("Mid-Term") || category.equalsIgnoreCase("Mid Term") ||
                category.equalsIgnoreCase("Middle Exam") || category.equalsIgnoreCase("Second Exam") ||
                category.equalsIgnoreCase("2nd Exam") || category.equalsIgnoreCase("Exam 2")) {
            // Look for midterm keys with extensive variations
            String[] midtermKeys = {"ME", "MT", "Midterm", "Midterm Exam", "Mid-Term", "Mid Term",
                    "Mid-Term Exam", "Middle Exam", "Second Exam", "2nd Exam",
                    "Exam 2", "Exam2", "E2", "MidE", "MiddleE"};
            for (String key : midtermKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }

            // Check for pattern matches
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("midterm") || key.contains("mid-term") || key.contains("mid term") ||
                        key.contains("middle exam") || key.contains("second exam") || key.contains("2nd exam") ||
                        key.equals("me") || key.equals("mt") || key.equals("e2")) {
                    double score = parseGradeValue(entry.getValue());
                    int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Final Exam") || category.equalsIgnoreCase("Final") ||
                category.equalsIgnoreCase("Finals") || category.equalsIgnoreCase("Final Examination") ||
                category.equalsIgnoreCase("Comprehensive Exam") || category.equalsIgnoreCase("Comprehensive") ||
                category.equalsIgnoreCase("Third Exam") || category.equalsIgnoreCase("3rd Exam") ||
                category.equalsIgnoreCase("Exam 3") || category.equalsIgnoreCase("Last Exam")) {
            // Look for final exam keys with extensive variations
            String[] finalKeys = {"FE", "Final", "Final Exam", "Finals", "Final Examination",
                    "Comprehensive", "Comprehensive Exam", "Comp", "CompE", "Third Exam",
                    "3rd Exam", "Exam 3", "Exam3", "E3", "Last Exam", "LE", "FinalE"};
            for (String key : finalKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }

            // Check for pattern matches
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("final") || key.contains("comprehensive") ||
                        key.contains("third exam") || key.contains("3rd exam") ||
                        key.contains("last exam") || key.equals("fe") || key.equals("e3") ||
                        key.equals("comp") || key.equals("le")) {
                    double score = parseGradeValue(entry.getValue());
                    int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Labs") || category.equalsIgnoreCase("Laboratory")) {
            // Look for lab-related keys (Lab1, Lab2, etc.)
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("Lab\\d+") || key.matches("Laboratory\\d+") || key.matches("L\\d+") ||
                        key.toLowerCase().startsWith("lab") || key.toLowerCase().contains("laboratory")) {
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

        } else if (category.equalsIgnoreCase("Recitation") || category.equalsIgnoreCase("Recitations")) {
            // Look for recitation-related keys
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("R\\d+") || key.matches("Recitation\\d+") || key.matches("Recit\\d+") ||
                        key.toLowerCase().startsWith("recitation") || key.toLowerCase().startsWith("recit") ||
                        key.toLowerCase().contains("recitation")) {
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

        } else if (category.equalsIgnoreCase("Presentations")) {
            // Look for presentation-related keys
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("Presentation\\d+") || key.matches("Pres\\d+") ||
                        key.toLowerCase().startsWith("presentation") || key.toLowerCase().startsWith("pres") ||
                        key.toLowerCase().contains("presentation") || key.toLowerCase().contains("report")) {
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

        } else if (category.equalsIgnoreCase("Case Study") || category.equalsIgnoreCase("Case Studies")) {
            // Look for case study-related keys
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("CS\\d+") || key.matches("Case\\d+") || key.matches("CaseStudy\\d+") ||
                        key.toLowerCase().startsWith("case") || key.toLowerCase().contains("case study")) {
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

        } else if (category.equalsIgnoreCase("Essays") || category.equalsIgnoreCase("Writing")) {
            // Look for essay/writing-related keys
            double total = 0;
            int count = 0;

            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String key = entry.getKey();
                if (key.matches("Essay\\d+") || key.matches("E\\d+") || key.matches("Writing\\d+") ||
                        key.toLowerCase().startsWith("essay") || key.toLowerCase().startsWith("writing") ||
                        key.toLowerCase().contains("essay") || key.toLowerCase().contains("paper")) {
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

        } else if (category.equalsIgnoreCase("Practicum") || category.equalsIgnoreCase("Practical")) {
            // Look for practicum/practical-related keys
            String[] practicumKeys = {"Practicum", "Practical", "Prac", "Hands-on", "Practice"};
            for (String key : practicumKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Thesis") || category.equalsIgnoreCase("Capstone")) {
            // Look for thesis/capstone-related keys
            String[] thesisKeys = {"Thesis", "Capstone", "Thesis Defense", "Final Project", "Senior Project"};
            for (String key : thesisKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else if (category.equalsIgnoreCase("Oral Exam") || category.equalsIgnoreCase("Oral Examination")) {
            // Look for oral exam-related keys
            String[] oralKeys = {"OE", "Oral", "Oral Exam", "Oral Examination", "Viva", "Defense"};
            for (String key : oralKeys) {
                if (parseGradeValue(grades.get(key)) != -1) {
                    double score = parseGradeValue(grades.get(key));
                    int maxValue = maxValues.getOrDefault(key, 100);
                    return (score / maxValue) * 100;
                }
            }
            return -1;

        } else {
            // For other categories, try exact match first, then partial match
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(category)) {
                    double score = parseGradeValue(entry.getValue());
                    int maxValue = maxValues.getOrDefault(entry.getKey(), 100);
                    return (score / maxValue) * 100;
                }
            }

            // Try partial match if exact match fails
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                if (entry.getKey().toLowerCase().contains(category.toLowerCase()) ||
                        category.toLowerCase().contains(entry.getKey().toLowerCase())) {
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

    public Map<String, Object> getStudentCourseGrades(int studentId, int classId) {
        List<GradeRecordsEntity> gradeRecords = gradeRecordsRepository.findByStudent_UserIdAndClassRecord_ClassEntity_ClassId(studentId, classId);

        Map<String, Object> result = new HashMap<>();
        if (!gradeRecords.isEmpty() && gradeRecords.get(0).getGrades() != null) {
            result.put("grades", gradeRecords.get(0).getGrades());
            result.put("assessmentMaxValues", gradeRecords.get(0).getClassRecord().getAssessmentMaxValues());
        } else {
            result.put("grades", Collections.emptyMap());
            result.put("assessmentMaxValues", Collections.emptyMap());
        }
        return result;
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
                double percentage = grade / 100; // Convert to percentage
                if (percentage < 60 && record.getStudentNumber() != null) {
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
                double percentage = grade / 100; // Convert to percentage
                if (percentage >= 80 && record.getStudentNumber() != null) {
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

    public double getStudentAveragePercentage(int studentId) {
        List<GradeRecordsEntity> gradeRecords = gradeRecordsRepository.findByStudent_UserId(studentId);

        if (gradeRecords.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int count = 0;

        for (GradeRecordsEntity record : gradeRecords) {
            int classId = record.getClassRecord().getClassEntity().getClassId();
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);
            double percentage = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(), record.getClassRecord().getAssessmentMaxValues());
            total += percentage;
            count++;
        }

        return count > 0 ? total / count : 0.0;
    }

    public Map<Integer, Double> getAllGradesByStudentId(int studentId) {
        List<GradeRecordsEntity> gradeRecords = gradeRecordsRepository.findByStudent_UserId(studentId);
        Map<Integer, Double> gradesByClass = new HashMap<>();

        for (GradeRecordsEntity record : gradeRecords) {
            int classId = record.getClassRecord().getClassEntity().getClassId();
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);
            double percentage = calculateGrade(
                record.getGrades(),
                gradingScheme.getGradingScheme(),
                record.getClassRecord().getAssessmentMaxValues()
            );
            gradesByClass.put(classId, percentage);
        }

        return gradesByClass;
    }

    public List<Map<String, Object>> getClassAveragesForStudent(int studentId) {
        List<GradeRecordsEntity> gradeRecords = gradeRecordsRepository.findByStudent_UserId(studentId);
        Set<Integer> classIds = new HashSet<>();
        Map<Integer, String> classNames = new HashMap<>();

        for (GradeRecordsEntity record : gradeRecords) {
            if (record.getClassRecord() != null && record.getClassRecord().getClassEntity() != null) {
                int classId = record.getClassRecord().getClassEntity().getClassId();
                classIds.add(classId);
                classNames.put(classId, record.getClassRecord().getClassEntity().getClassName());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Integer classId : classIds) {
            double avg = calculateClassAverageGrade(classId);
            Map<String, Object> map = new HashMap<>();
            map.put("classId", classId);
            map.put("className", classNames.get(classId));
            map.put("average", avg);
            result.add(map);
        }
        return result;
    }

    public Map<String, Integer> getTeacherGradeDistribution(int teacherId) {
        // Find all classes taught by this teacher
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("A", 0);
        distribution.put("B", 0);
        distribution.put("C", 0);
        distribution.put("D", 0);
        distribution.put("F", 0);

        // Process each class
        for (ClassEntity classEntity : teacherClasses) {
            List<StudentTableData> classData = getClassRosterTableData(classEntity.getClassId());

            for (StudentTableData student : classData) {
                String grade = student.getGrade();
                distribution.put(grade, distribution.get(grade) + 1);
            }
        }

        return distribution;
    }

    public static class TeacherAssessmentPerformance {
        private String assessmentType;
        private double overallAverage;
        private double topQuartileAverage;
        private double bottomQuartileAverage;
        private int totalStudents;

        public TeacherAssessmentPerformance(String assessmentType, double overallAverage,
                                            double topQuartileAverage, double bottomQuartileAverage, int totalStudents) {
            this.assessmentType = assessmentType;
            this.overallAverage = overallAverage;
            this.topQuartileAverage = topQuartileAverage;
            this.bottomQuartileAverage = bottomQuartileAverage;
            this.totalStudents = totalStudents;
        }

        // Getters and setters
        public String getAssessmentType() { return assessmentType; }
        public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
        public double getOverallAverage() { return overallAverage; }
        public void setOverallAverage(double overallAverage) { this.overallAverage = overallAverage; }
        public double getTopQuartileAverage() { return topQuartileAverage; }
        public void setTopQuartileAverage(double topQuartileAverage) { this.topQuartileAverage = topQuartileAverage; }
        public double getBottomQuartileAverage() { return bottomQuartileAverage; }
        public void setBottomQuartileAverage(double bottomQuartileAverage) { this.bottomQuartileAverage = bottomQuartileAverage; }
        public int getTotalStudents() { return totalStudents; }
        public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }
    }


    public List<TeacherAssessmentPerformance> getClassPerformanceData(int teacherId) {
        // Find all classes taught by this teacher
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        // Collect all assessment data by type across all classes
        Map<String, List<Double>> assessmentScores = new HashMap<>();

        for (ClassEntity classEntity : teacherClasses) {
            int classId = classEntity.getClassId();
            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

            if (classRecords.isEmpty()) continue;

            Map<String, Integer> assessmentMaxValues = classRecords.get(0).getClassRecord().getAssessmentMaxValues();

            // Process each assessment type in this class
            for (String assessmentKey : assessmentMaxValues.keySet()) {
                String assessmentType = getAssessmentType(assessmentKey);

                // Initialize list if not exists
                assessmentScores.computeIfAbsent(assessmentType, k -> new ArrayList<>());

                // Collect scores for this assessment type
                for (GradeRecordsEntity record : classRecords) {
                    Map<String, String> grades = record.getGrades();
                    if (grades.containsKey(assessmentKey)) {
                        double score = parseGradeValue(grades.get(assessmentKey));
                        if (score >= 0) {
                            int maxValue = assessmentMaxValues.get(assessmentKey);
                            double percentage = (score / maxValue) * 100;
                            assessmentScores.get(assessmentType).add(percentage);
                        }
                    }
                }
            }
        }

        // Calculate performance statistics for each assessment type
        List<TeacherAssessmentPerformance> performanceData = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : assessmentScores.entrySet()) {
            String assessmentType = entry.getKey();
            List<Double> scores = entry.getValue();

            if (!scores.isEmpty()) {
                // Calculate averages
                double overallAverage = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                // Sort scores for quartile calculations
                Collections.sort(scores);
                int size = scores.size();

                // Top quartile (top 25%)
                int topQuartileStart = (int) Math.ceil(size * 0.75);
                double topQuartileAverage = scores.subList(topQuartileStart, size)
                        .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                // Bottom quartile (bottom 25%)
                int bottomQuartileEnd = (int) Math.ceil(size * 0.25);
                double bottomQuartileAverage = scores.subList(0, bottomQuartileEnd)
                        .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                performanceData.add(new TeacherAssessmentPerformance(
                        assessmentType, overallAverage, topQuartileAverage, bottomQuartileAverage, scores.size()));
            }
        }

        // Sort by assessment type order
        performanceData.sort((a, b) -> getAssessmentTypeOrder(a.getAssessmentType()) - getAssessmentTypeOrder(b.getAssessmentType()));

        return performanceData;
    }

    /**
     * Get assessment type from assessment key
     */
    private String getAssessmentType(String key) {
        if (key.matches("Q\\d+")) {
            return "Quizzes";
        } else if (key.matches("A\\d+")) {
            return "Assignments";
        } else if (key.matches("P\\d+")) {
            return "Projects";
        } else if (key.equalsIgnoreCase("ME") || key.equalsIgnoreCase("Midterm")) {
            return "Midterm Exams";
        } else if (key.equalsIgnoreCase("FE") || key.equalsIgnoreCase("Final")) {
            return "Final Exams";
        } else {
            return "Other";
        }
    }

    /**
     * Get assessment type order for sorting
     */
    private int getAssessmentTypeOrder(String assessmentType) {
        switch (assessmentType.toLowerCase()) {
            case "quizzes": return 1;
            case "assignments": return 2;
            case "projects": return 3;
            case "midterm exams": return 4;
            case "final exams": return 5;
            default: return 6;
        }
    }

}