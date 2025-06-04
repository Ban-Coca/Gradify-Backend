package com.capstone.gradify.Service;

import com.capstone.gradify.Entity.records.*;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.records.GradingSchemeRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.capstone.gradify.Entity.user.StudentEntity;

import java.util.*;

@Service
public class RecordsService {
    private static final Logger logger = LoggerFactory.getLogger(RecordsService.class); // Ensure logger is initialized
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
        logger.info("Fetching class roster for class ID: {}", classId);
        List<GradeRecordsEntity> allRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        if (gradingScheme == null) {
            logger.warn("No grading scheme found for class ID: {}. Grades will be N/A.", classId);
        } else {
            logger.info("Using grading scheme for class ID: {}: {}", classId, gradingScheme.getGradingScheme());
        }

        List<StudentTableData> tableData = new ArrayList<>();

        for (GradeRecordsEntity record : allRecords) {
            double percentageForDisplay = 0.0; // This will be the 0-100 scale for the Percentage column
            String letterGradeToStore = "N/A";
            String statusToStore = "Scheme Missing";

            if (gradingScheme != null && gradingScheme.getGradingScheme() != null) {
                percentageForDisplay = calculateGrade( // This returns 0-100
                        record.getGrades(),
                        gradingScheme.getGradingScheme(),
                        record.getClassRecord().getAssessmentMaxValues()
                );

                // Log the value EXACTLY before passing it to convertToLetterGrade
                logger.info("Passing to convertToLetterGrade for {}: {}", record.getStudentNumber(), percentageForDisplay);
                letterGradeToStore = convertToLetterGrade(percentageForDisplay); // Pass the 0-100 scale

                statusToStore = determineStatus(percentageForDisplay);

                logger.info("Calculated for {}: Percentage = {}, Letter Grade = {}, Status = {}",
                        record.getStudentNumber(), percentageForDisplay, letterGradeToStore, statusToStore);
            }

            Optional<StudentEntity> studentOpt = studentRepository.findByStudentNumber(record.getStudentNumber());

            if (studentOpt.isPresent()) {
                StudentEntity student = studentOpt.get();
                String studentName = student.getFirstName() + " " + student.getLastName();

                StudentTableData data = new StudentTableData(
                        studentName,
                        record.getStudentNumber(),
                        letterGradeToStore,
                        percentageForDisplay, // Use the 0-100 scaled value
                        statusToStore,
                        student.getUserId()
                );
                tableData.add(data);
            } else {
                logger.warn("Student not found for student number: {}", record.getStudentNumber());
            }
        }
        logger.info("Finished processing class roster for class ID: {}. Total students: {}", classId, tableData.size());
        return tableData;
    }
    /**
     * Convert numeric percentage to letter grade
     */
    private String convertToLetterGrade(double percentage) { // Expects 0-100
        String grade;
        if (percentage >= 90) grade = "A";
        else if (percentage >= 80) grade = "B";
        else if (percentage >= 70) grade = "C";
        else if (percentage >= 60) grade = "D";
        else grade = "F";
        // This log is critical:
        logger.info("convertToLetterGrade - Input: {}, Output: {}", percentage, grade);
        return grade;
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
        if (schemeJson == null) { // This check is important
            return 0.0;
        }
        try {
            List<Map<String, Object>> schemeItems = mapper.readValue(
                    schemeJson, new TypeReference<List<Map<String, Object>>>() {});

            double totalGrade = 0.0;
            double totalAppliedWeight = 0.0;

            for (Map<String, Object> schemeItem : schemeItems) {
                String name = (String) schemeItem.get("name");
                // Ensure weight is present and is a number before parsing
                Object weightObj = schemeItem.get("weight");
                if (weightObj == null) continue; // Skip if weight is missing

                double weight;
                try {
                    weight = Double.parseDouble(weightObj.toString());
                } catch (NumberFormatException e) {
                    // logger.warn("Invalid weight format for category '{}': {}", name, weightObj);
                    continue; // Skip if weight is not a valid number
                }

                totalAppliedWeight += weight;
                double categoryScore = getCategoryScore(grades, name, assessmentMaxValues);

                if (categoryScore >= 0) {
                    totalGrade += (categoryScore * (weight / 100.0));
                } else {
                    totalGrade += 0;
                }
            }

            if (totalAppliedWeight > 0 && totalAppliedWeight <= 100) { // Ensure totalAppliedWeight is reasonable
                // If totalAppliedWeight sums to 100, this simplifies to totalGrade
                // If it's less (e.g. some categories had no grades), this will scale appropriately.
                return (totalGrade / (totalAppliedWeight / 100.0));
            } else if (totalAppliedWeight == 0) {
                return 0.0;
            } else {
                // If totalAppliedWeight is > 100, it implies an issue with scheme definition
                // logger.warn("Total applied weight {} is greater than 100 for scheme: {}", totalAppliedWeight, schemeJson);
                // Decide how to handle this: normalize to 100, or return as is, or return error indicator
                return totalGrade; // Or (totalGrade / (totalAppliedWeight / 100.0)) if that's intended.
            }

        } catch (JsonProcessingException e) {
            // logger.error("Error parsing grading scheme JSON: {}", schemeJson, e);
            // It might be better to throw a custom, more specific exception or return a special value.
            throw new RuntimeException("Error parsing grading scheme: " + e.getMessage(), e);
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
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);
        Set<String> uniqueAtRiskStudents = new HashSet<>();

        if (teacherClasses == null || teacherClasses.isEmpty()) {
            return 0;
        }

        for (ClassEntity classEntity : teacherClasses) {
            int classId = classEntity.getClassId();
            // Fetch grading scheme
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            // If no grading scheme, skip grade calculation for this class
            if (gradingScheme == null || gradingScheme.getGradingScheme() == null) {
                // Optionally log this:
                // logger.warn("No grading scheme found for class ID: {}. Skipping for at-risk calculation.", classId);
                continue;
            }

            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

            for (GradeRecordsEntity record : classRecords) {
                double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(),
                        record.getClassRecord().getAssessmentMaxValues());
                // Assuming calculateGrade returns a percentage (e.g., 85.0 for 85%)
                // If it returns a 0-1 scale, adjust the condition below.
                if (grade < 60 && record.getStudentNumber() != null) { // Ensure your 'at-risk' threshold is correct
                    uniqueAtRiskStudents.add(record.getStudentNumber());
                }
            }
        }
        return uniqueAtRiskStudents.size();
    }


    public int countTopPerformingStudents(int teacherId) {
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);
        Set<String> uniqueTopStudents = new HashSet<>();

        if (teacherClasses == null || teacherClasses.isEmpty()) {
            return 0;
        }

        for (ClassEntity classEntity : teacherClasses) {
            int classId = classEntity.getClassId();
            // Fetch grading scheme
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            // If no grading scheme, skip grade calculation for this class
            if (gradingScheme == null || gradingScheme.getGradingScheme() == null) {
                // Optionally log this:
                // logger.warn("No grading scheme found for class ID: {}. Skipping for top-performer calculation.", classId);
                continue;
            }

            List<GradeRecordsEntity> classRecords = gradeRecordsRepository.findByClassRecord_ClassEntity_ClassId(classId);

            for (GradeRecordsEntity record : classRecords) {
                double grade = calculateGrade(record.getGrades(), gradingScheme.getGradingScheme(),
                        record.getClassRecord().getAssessmentMaxValues());
                // Assuming calculateGrade returns a percentage (e.g., 85.0 for 85%)
                // If it returns a 0-1 scale, adjust the condition below.
                if (grade >= 80 && record.getStudentNumber() != null) { // Ensure your 'top-performing' threshold is correct
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
        List<ClassEntity> teacherClasses = classService.getClassesByTeacherId(teacherId);

        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("A", 0);
        distribution.put("B", 0);
        distribution.put("C", 0);
        distribution.put("D", 0);
        distribution.put("F", 0);
        distribution.put("N/A", 0); // Add N/A for missing schemes, or choose to ignore these students

        if (teacherClasses == null || teacherClasses.isEmpty()) {
            return distribution; // Return empty distribution if teacher has no classes
        }

        for (ClassEntity classEntity : teacherClasses) {
            List<StudentTableData> classData = getClassRosterTableData(classEntity.getClassId());

            for (StudentTableData student : classData) {
                String grade = student.getGrade(); // This can be "A", "B", ..., "F", or "N/A"

                // Use getOrDefault to handle cases where the grade might not be an expected key (e.g., "N/A")
                // or if you decide not to pre-initialize "N/A"
                distribution.put(grade, distribution.getOrDefault(grade, 0) + 1);
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