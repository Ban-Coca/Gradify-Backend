package com.capstone.gradify.Service.spreadsheet;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import com.capstone.gradify.Entity.user.StudentEntity;

import java.io.IOException;
import java.util.*;

@Service
public class ClassSpreadsheetService {
    Logger logger = LoggerFactory.getLogger(ClassSpreadsheetService.class);
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private GradeRecordRepository gradeRecordRepository;
    @Autowired
    private ClassRepository classRepository;
    public ClassSpreadsheetService() {
        super();
    }

    public ClassSpreadsheet saveRecord(String fileName, TeacherEntity teacher, List<Map<String, String>> records, Map<String, Integer> maxAssessmentValues) {
        ClassSpreadsheet classSpreadsheet = new ClassSpreadsheet();
        classSpreadsheet.setFileName(fileName);
        classSpreadsheet.setUploadedBy(teacher);
        classSpreadsheet.setAssessmentMaxValues(maxAssessmentValues);
        classSpreadsheet.setClassName(extractFileName(fileName));

        // Logic to save the records to the database
        // For example, you can iterate through the records and save each one
        for (Map<String, String> record : records) {
            GradeRecordsEntity gradeRecord = new GradeRecordsEntity();
            gradeRecord.setStudentNumber(record.get("Student Number"));
            gradeRecord.setGrades(record);
            gradeRecord.setClassRecord(classSpreadsheet);

            classSpreadsheet.getGradeRecords().add(gradeRecord);

        }
        return classSpreadsheetRepository.save(classSpreadsheet);
    }

    public Optional<ClassSpreadsheet> getClassSpreadsheetById(Long id){
        Optional<ClassSpreadsheet> classSpreadsheet = classSpreadsheetRepository.findById(id);
        if (classSpreadsheet.isEmpty()) {
            throw new RuntimeException("Class spreadsheet not found");
        }
        return classSpreadsheet;
    }

    public List<Map<String, String>> parseClassRecord(MultipartFile file) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        Map<String, Integer> maxAssessmentValues = new HashMap<>();

        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);

        Iterator<Row> rowIterator = sheet.iterator();
        List<String> headers = new ArrayList<>();
        if (rowIterator.hasNext()) {
            Row headerRow = rowIterator.next();
            for(Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
        }

        if (rowIterator.hasNext()) {
            Row maxValueRow = rowIterator.next();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                Cell cell = maxValueRow.getCell(i);

                // Only process assessment columns (those with numeric values)
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    int maxValue = (int) cell.getNumericCellValue();
                    maxAssessmentValues.put(header, maxValue);
                }
            }
        }

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Map<String, String> record = new java.util.HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.getCell(i);
                String cellValue = getCellValue(cell);
                record.put(headers.get(i), cellValue);
            }
            records.add(record);
        }
        workbook.close();
        return records;
    }

    public Map<String, Integer> getMaxAssessmentValue(MultipartFile file) throws IOException {
        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);

        Iterator<Row> rowIterator = sheet.iterator();
        List<String> headers = new ArrayList<>();
        if (rowIterator.hasNext()) {
            Row headerRow = rowIterator.next();
            for(Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
        }

        if (rowIterator.hasNext()) {
            Row maxValueRow = rowIterator.next();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                Cell cell = maxValueRow.getCell(i);

                // Only process assessment columns (those with numeric values)
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    int maxValue = (int) cell.getNumericCellValue();
                    maxAssessmentValues.put(header, maxValue);
                }
            }
        }
        workbook.close();
        return maxAssessmentValues;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return ""; // Return empty string for null cells
        }
        String value = "";
        switch (cell.getCellType()) {
            case STRING:
                value = cell.getStringCellValue();
                break;
            case NUMERIC:
                value = String.valueOf(cell.getNumericCellValue());
                break;
            case BOOLEAN:
                value = String.valueOf(cell.getBooleanCellValue());
                break;
            default:
                break;
        }
        return value;
    }

    public String extractFileName(String fileName){
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public ClassEntity createClassEntityFromSpreadsheet(MultipartFile file, List<Map<String, String>> records, TeacherEntity teacher) {
        ClassEntity classEntity = new ClassEntity();

        // Set the teacher
        classEntity.setTeacher(teacher);

        String filename = file.getOriginalFilename();
        if (filename != null) {

            if (filename.contains(".")) {
                filename = filename.substring(0, filename.lastIndexOf('.'));
            }

            // Split by underscore or other delimiter
            String[] parts = filename.split("-");
            if (parts.length >= 4) {
                classEntity.setClassName(parts[0]);
                classEntity.setSection(parts[1]);
                classEntity.setClassCode(generateClassCode(parts[0], parts[1], parts[2], parts[3]));
            } else if (parts.length >= 2) {
                // Handle case when fewer parts are available
                classEntity.setClassName(parts[0]);
                classEntity.setSection(parts[1]);
                classEntity.setClassCode(generateRandomClassCode());
            } else {
                // Not enough parts in the filename
                classEntity.setClassName(extractFileName(filename));
                classEntity.setClassCode(generateRandomClassCode());
            }
        }

        if (records != null && !records.isEmpty()) {
            // This is just an example - adjust based on your spreadsheet structure
            Map<String, String> firstRecord = records.get(0);

            if (classEntity.getClassName() == null && firstRecord.containsKey("Class")) {
                classEntity.setClassName(firstRecord.get("Class"));
            }

            // Add more extraction logic as needed
        }

        // Set timestamps
        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);

        // For any missing required fields, set defaults or throw an error
        if (classEntity.getClassName() == null) {
            classEntity.setClassName("Untitled Class");
        }

        if (classEntity.getSemester() == null) {
            // You could derive the current semester based on the current date
            classEntity.setSemester(determineCurrentSemester());
        }

        if (classEntity.getSchoolYear() == null) {
            // Set the current school year
            classEntity.setSchoolYear(determineCurrentSchoolYear());
        }

        if (classEntity.getClassCode() == null) {

            classEntity.setClassCode(generateRandomClassCode());
        }

        return classEntity;
    }


    // Helper methods for the service
    public String determineCurrentSemester() {
        // Logic to determine current semester based on date
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);

        if (month >= Calendar.JANUARY && month <= Calendar.MAY) {
            return "2nd";
        } else if (month >= Calendar.JUNE && month <= Calendar.JULY) {
            return "Midyear";
        } else {
            return "1st";
        }
    }

    public String determineCurrentSchoolYear() {
        // Logic to determine current school year
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);

        // If it's after August, the school year is current year - next year
        if (month >= Calendar.AUGUST) {
            return year + "-" + (year + 1);
        } else {
            return (year - 1) + "-" + year;
        }
    }

    public String generateClassCode(String className, String semester, String year, String section) {
        // Remove spaces and special characters
        String baseCode = className.replaceAll("[^a-zA-Z0-9]", "");

        // Take first 3 chars of class name + first char of semester + last 2 digits of year + section
        String code = baseCode.substring(0, Math.min(3, baseCode.length())) +
                semester.substring(0, 1) +
                year.substring(Math.max(0, year.length() - 2)) +
                section;

        return code.toUpperCase();
    }

    public String generateRandomClassCode() {
        // Generate a random 6-character alphanumeric code
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    @Transactional
    public GradeRecordsEntity createGradeRecordWithStudentAssociation(
            String studentNumber,
            String studentFirstName,
            String studentLastName,
            ClassSpreadsheet classRecord,
            Map<String, String> grades) {

        if (classRecord.getId() == null) {
            classRecord = classSpreadsheetRepository.save(classRecord);
        }

        // Find the student by student number or create new one
        StudentEntity student = studentRepository.findByStudentNumber(studentNumber)
                .orElseGet(() -> {
                    StudentEntity newStudent = new StudentEntity();
                    newStudent.setStudentNumber(studentNumber);

//                    // Split name into first and last name if available
//                    if (studentName != null && !studentName.trim().isEmpty()) {
//                        String[] nameParts = studentName.trim().split("\\s+", 2);
//                        newStudent.setFirstName(nameParts[0]);
//                        if (nameParts.length > 1) {
//                            newStudent.setLastName(nameParts[1]);
//                        }
//                    }
                    newStudent.setFirstName(studentFirstName);
                    newStudent.setLastName(studentLastName);
                    newStudent.setRole(Role.STUDENT);
                    newStudent.setIsActive(true);
                    newStudent.setCreatedAt(new Date());

                    // You might need a temporary email and password if these are required
                    // or mark them as to be completed later by the student
                    newStudent.setEmail(studentNumber + "@temp.edu"); // Temporary email
                    newStudent.setPassword("PLACEHOLDER"); // Will need to be changed when student registers

                    return studentRepository.save(newStudent);
                });

        // Create and populate the grade record
        GradeRecordsEntity gradeRecord = new GradeRecordsEntity();
        gradeRecord.setStudentNumber(studentNumber);
        gradeRecord.setStudent(student); // Associate with student
        gradeRecord.setClassRecord(classRecord);
        gradeRecord.setGrades(grades);

        return gradeRecordRepository.save(gradeRecord);
    }

    public ClassSpreadsheet saveRecord(String filename, TeacherEntity teacher,
                                       List<Map<String, String>> records, ClassEntity classEntity, Map<String, Integer> maxAssessmentValues) {
        ClassSpreadsheet spreadsheet = new ClassSpreadsheet();
        spreadsheet.setFileName(filename);
        spreadsheet.setUploadedBy(teacher);
        spreadsheet.setClassName(classEntity.getClassName()); // Set the class name from ClassEntity
        spreadsheet.setClassEntity(classEntity);
        spreadsheet.setAssessmentMaxValues(maxAssessmentValues);
        // Create grade records
        List<GradeRecordsEntity> gradeRecords = new ArrayList<>();
        for (Map<String, String> record : records) {
            String studentFirstName = record.get("First Name");
            String studentLastName = record.get("Last Name");
            String studentNumber = record.get("Student Number");

//            if (studentName == null) {
//                // Try common field names or patterns in your data
//                studentName = record.get("Name") != null ? record.get("name") :
//                        (record.get("fullName") != null ? record.get("fullName") :
//                                (record.get("First Name") + " " + record.get("Last Name")));
//            }

            if (studentNumber == null) {
                studentNumber = record.get("StudentNumber");
            }

            // Create the grade record with student association
            GradeRecordsEntity gradeRecord = createGradeRecordWithStudentAssociation(
                    studentNumber,
                    studentFirstName,
                    studentLastName,
                    spreadsheet,
                    record
            );


            gradeRecord.setGrades(record);

            gradeRecords.add(gradeRecord);
        }

        spreadsheet.setGradeRecords(gradeRecords);


        return classSpreadsheetRepository.save(spreadsheet);
    }

    public List<ClassSpreadsheet> getClassSpreadSheetByClassId(int classId) {
        ClassEntity classEntity = classRepository.findByClassId(classId);
        if (classEntity == null) {
            throw new RuntimeException("Class not found");
        }
        return classSpreadsheetRepository.findByClassEntity(classEntity);
    }
}
