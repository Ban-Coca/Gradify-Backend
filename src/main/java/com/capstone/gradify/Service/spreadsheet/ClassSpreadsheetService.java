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
import com.capstone.gradify.dto.response.GradeErrorDetail;
import com.capstone.gradify.exceptions.GradeException.GradeExceedsMaximumException;
import com.capstone.gradify.exceptions.GradeException.GradeValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import com.capstone.gradify.Entity.user.StudentEntity;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClassSpreadsheetService {
    Logger logger = LoggerFactory.getLogger(ClassSpreadsheetService.class);
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final StudentRepository studentRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final ClassRepository classRepository;

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

    // Overload createClassEntityFromSpreadsheet to accept an optional custom name
    public ClassEntity createClassEntityFromSpreadsheet(MultipartFile file, List<Map<String, String>> records, TeacherEntity teacher, String customClassName) {
        ClassEntity classEntity = new ClassEntity();
        classEntity.setTeacher(teacher);

        String filename = (customClassName != null && !customClassName.isEmpty()) ? customClassName : file.getOriginalFilename();

        if (filename != null) {
            String baseName = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
            String[] parts = baseName.split("-");

            if (parts.length >= 2 && customClassName == null) { // Only use parts if not custom named for section
                classEntity.setClassName(parts[0].trim());
                classEntity.setSection(parts[1].trim());
            } else {
                classEntity.setClassName(baseName.trim());
                // If section is not in filename or it's a custom name, it might need to be set differently or default
                classEntity.setSection(parts.length > 1 && customClassName == null ? parts[1].trim() : "Default Section");
            }

            if (parts.length >= 4 && customClassName == null) {
                classEntity.setClassCode(generateClassCode(parts[0], parts[1], parts[2], parts[3]));
            } else {
                classEntity.setClassCode(generateRandomClassCode());
            }

        } else {
            classEntity.setClassName("Untitled Class");
            classEntity.setSection("Default Section");
            classEntity.setClassCode(generateRandomClassCode());
        }

        // Default Semester and School Year if not derivable
        classEntity.setSemester(determineCurrentSemester());
        classEntity.setSchoolYear(determineCurrentSchoolYear());

        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);

        return classEntity;
    }
    // Keep the original method for compatibility if it's used elsewhere without customClassName
    public ClassEntity createClassEntityFromSpreadsheet(MultipartFile file, List<Map<String, String>> records, TeacherEntity teacher) {
        return createClassEntityFromSpreadsheet(file, records, teacher, null);
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
                    newStudent.setActive(true);
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
    // Microsoft Excel compatibility
    // METHOD FOR SAVING RECORDS WITH FOLDER DETAILS FROM MICROSOFT EXCEL
    public ClassSpreadsheet saveRecord(String filename, String itemId, String folderName, String folderId, TeacherEntity teacher,
                                       List<Map<String, String>> records, ClassEntity classEntity, Map<String, Integer> maxAssessmentValues) {

        // PRE VALIDATE ALL GRADES AGAINST MAX VALUES
        preValidateAllRecords(records, maxAssessmentValues);

        ClassSpreadsheet spreadsheet = new ClassSpreadsheet();
        spreadsheet.setFileName(filename);
        spreadsheet.setUploadedBy(teacher);
        spreadsheet.setClassName(classEntity.getClassName()); // Set the class name from ClassEntity
        spreadsheet.setClassEntity(classEntity);
        spreadsheet.setAssessmentMaxValues(maxAssessmentValues);
        // Create grade records
        spreadsheet.setFolderName(folderName);
        spreadsheet.setFolderId(folderId);
        spreadsheet.setItemId(itemId);

        spreadsheet.setVisibleAssessments(new HashSet<>());

        List<GradeRecordsEntity> gradeRecords = new ArrayList<>();
        for (Map<String, String> record : records) {
            String studentFirstName = (record.get("First Name") != null && !record.get("First Name").trim().isEmpty())
                    ? record.get("First Name")
                    : (record.get("FIRST NAME") != null && !record.get("FIRST NAME").trim().isEmpty())
                    ? record.get("FIRST NAME")
                    : (record.get("FirstName") != null && !record.get("FirstName").trim().isEmpty())
                    ? record.get("FirstName")
                    : (record.get("FIRSTNAME") != null && !record.get("FIRSTNAME").trim().isEmpty())
                    ? record.get("FIRSTNAME")
                    : (record.get("Name") != null && !record.get("Name").trim().isEmpty())
                    ? record.get("Name").split("\\s+")[0]
                    : (record.get("NAME") != null && !record.get("NAME").trim().isEmpty())
                    ? record.get("NAME").split("\\s+")[0]
                    : "Unknown";

            String studentLastName = (record.get("Last Name") != null && !record.get("Last Name").trim().isEmpty())
                    ? record.get("Last Name")
                    : (record.get("LAST NAME") != null && !record.get("LAST NAME").trim().isEmpty())
                    ? record.get("LAST NAME")
                    : (record.get("LastName") != null && !record.get("LastName").trim().isEmpty())
                    ? record.get("LastName")
                    : (record.get("LASTNAME") != null && !record.get("LASTNAME").trim().isEmpty())
                    ? record.get("LASTNAME")
                    : (record.get("Surname") != null && !record.get("Surname").trim().isEmpty())
                    ? record.get("Surname")
                    : (record.get("SURNAME") != null && !record.get("SURNAME").trim().isEmpty())
                    ? record.get("SURNAME")
                    : (record.get("Name") != null && !record.get("Name").trim().isEmpty() && record.get("Name").split("\\s+").length > 1)
                    ? record.get("Name").split("\\s+", 2)[1]
                    : (record.get("NAME") != null && !record.get("NAME").trim().isEmpty() && record.get("NAME").split("\\s+").length > 1)
                    ? record.get("NAME").split("\\s+", 2)[1]
                    : "Unknown";

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

    // ORIGINAL METHOD FOR SAVING RECORDS FROM UPLOADS
    public ClassSpreadsheet saveRecord(String filename, TeacherEntity teacher,
                                       List<Map<String, String>> records, ClassEntity classEntity, Map<String, Integer> maxAssessmentValues) {

        // PRE VALIDATE ALL GRADES AGAINST MAX VALUES
        preValidateAllRecords(records, maxAssessmentValues);

        ClassSpreadsheet spreadsheet = new ClassSpreadsheet();
        spreadsheet.setFileName(filename);
        spreadsheet.setUploadedBy(teacher);
        spreadsheet.setClassName(classEntity.getClassName()); // Set the class name from ClassEntity
        spreadsheet.setClassEntity(classEntity);
        spreadsheet.setAssessmentMaxValues(maxAssessmentValues);
        // Create grade records

        spreadsheet.setVisibleAssessments(new HashSet<>());

        List<GradeRecordsEntity> gradeRecords = new ArrayList<>();

        for (Map<String, String> record : records) {
            // TODO: FIX NAME PARSING FOR UPLOAD IT IS BROKEN THE NAME WOULD BE NULL
            String studentFirstName = (record.get("First Name") != null && !record.get("First Name").trim().isEmpty())
                    ? record.get("First Name")
                    : (record.get("FIRST NAME") != null && !record.get("FIRST NAME").trim().isEmpty())
                    ? record.get("FIRST NAME")
                    : (record.get("FirstName") != null && !record.get("FirstName").trim().isEmpty())
                    ? record.get("FirstName")
                    : (record.get("FIRSTNAME") != null && !record.get("FIRSTNAME").trim().isEmpty())
                    ? record.get("FIRSTNAME")
                    : (record.get("Name") != null && !record.get("Name").trim().isEmpty())
                    ? record.get("Name").split("\\s+")[0]
                    : (record.get("NAME") != null && !record.get("NAME").trim().isEmpty())
                    ? record.get("NAME").split("\\s+")[0]
                    : "Unknown";

            String studentLastName = (record.get("Last Name") != null && !record.get("Last Name").trim().isEmpty())
                    ? record.get("Last Name")
                    : (record.get("LAST NAME") != null && !record.get("LAST NAME").trim().isEmpty())
                    ? record.get("LAST NAME")
                    : (record.get("LastName") != null && !record.get("LastName").trim().isEmpty())
                    ? record.get("LastName")
                    : (record.get("LASTNAME") != null && !record.get("LASTNAME").trim().isEmpty())
                    ? record.get("LASTNAME")
                    : (record.get("Surname") != null && !record.get("Surname").trim().isEmpty())
                    ? record.get("Surname")
                    : (record.get("SURNAME") != null && !record.get("SURNAME").trim().isEmpty())
                    ? record.get("SURNAME")
                    : (record.get("Name") != null && !record.get("Name").trim().isEmpty() && record.get("Name").split("\\s+").length > 1)
                    ? record.get("Name").split("\\s+", 2)[1]
                    : (record.get("NAME") != null && !record.get("NAME").trim().isEmpty() && record.get("NAME").split("\\s+").length > 1)
                    ? record.get("NAME").split("\\s+", 2)[1]
                    : "Unknown";

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

    public ClassSpreadsheet updateSpreadsheet(ClassSpreadsheet spreadsheet, List<Map<String, String>> updatedRecords, Map<String, Integer> maxAssessmentValues) {
        ClassSpreadsheet existingSpreadsheet = classSpreadsheetRepository.findByIdWithGradeRecords(spreadsheet.getId()).orElseThrow(() -> new RuntimeException("Class spreadsheet not found"));

        Map<String, GradeRecordsEntity> existingRecordsByStudentId = new HashMap<>();
        for (GradeRecordsEntity record : existingSpreadsheet.getGradeRecords()) {
            existingRecordsByStudentId.put(record.getStudentNumber(), record);
        }
        List<GradeRecordsEntity> updatedGradeRecords = new ArrayList<>();
        for (Map<String, String> record : updatedRecords) {
            String studentNumber = record.get("Student Number");
            String studentFirstName = record.get("First Name");
            String studentLastName = record.get("Last Name");

            if (studentNumber == null) {
                logger.warn("Skipping record with missing student number");
                continue;
            }

            // Check if this student already exists in the spreadsheet
            if (existingRecordsByStudentId.containsKey(studentNumber)) {
                // Update existing grade record
                GradeRecordsEntity existingRecord = existingRecordsByStudentId.get(studentNumber);
                existingRecord.setGrades(record);
                updatedGradeRecords.add(existingRecord);
            } else {
                // Create new grade record for new student
                GradeRecordsEntity newRecord = createGradeRecordWithStudentAssociation(
                        studentNumber,
                        studentFirstName,
                        studentLastName,
                        existingSpreadsheet,
                        record
                );
                updatedGradeRecords.add(newRecord);
            }
        }

        // Update the spreadsheet with the new records
        existingSpreadsheet.setGradeRecords(updatedGradeRecords);
        return classSpreadsheetRepository.save(existingSpreadsheet);
    }
    @Transactional
    public ClassSpreadsheet updateSpreadsheet(Long spreadsheetId, MultipartFile file, TeacherEntity teacher) throws IOException {
        // Fetch the existing spreadsheet
        ClassSpreadsheet existingSpreadsheet = classSpreadsheetRepository.findById(spreadsheetId)
                .orElseThrow(() -> new RuntimeException("Class spreadsheet not found"));

        // Parse the new data from file
        List<Map<String, String>> updatedRecords = parseClassRecord(file);
        Map<String, Integer> updatedMaxValues = getMaxAssessmentValue(file);

        // Update basic spreadsheet information
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            existingSpreadsheet.setFileName(fileName);
            existingSpreadsheet.setClassName(extractFileName(fileName));
        }

        existingSpreadsheet.setAssessmentMaxValues(updatedMaxValues);

        // Create a map of existing grade records by student number for quick lookup
        Map<String, GradeRecordsEntity> existingRecordsByStudentId = new HashMap<>();
        for (GradeRecordsEntity record : existingSpreadsheet.getGradeRecords()) {
            existingRecordsByStudentId.put(record.getStudentNumber(), record);
        }

        // Process updated records
        List<GradeRecordsEntity> updatedGradeRecords = new ArrayList<>();
        for (Map<String, String> record : updatedRecords) {
            String studentNumber = record.get("Student Number");
            String studentFirstName = record.get("First Name");
            String studentLastName = record.get("Last Name");

            if (studentNumber == null) {
                logger.warn("Skipping record with missing student number");
                continue;
            }

            // Check if this student already exists in the spreadsheet
            if (existingRecordsByStudentId.containsKey(studentNumber)) {
                // Update existing grade record
                GradeRecordsEntity existingRecord = existingRecordsByStudentId.get(studentNumber);
                existingRecord.setGrades(record);
                updatedGradeRecords.add(existingRecord);
            } else {
                // Create new grade record for new student
                GradeRecordsEntity newRecord = createGradeRecordWithStudentAssociation(
                        studentNumber,
                        studentFirstName,
                        studentLastName,
                        existingSpreadsheet,
                        record
                );
                updatedGradeRecords.add(newRecord);
            }
        }

        // Update the spreadsheet with the new records
        existingSpreadsheet.setGradeRecords(updatedGradeRecords);

        // Save and return the updated spreadsheet
        return classSpreadsheetRepository.save(existingSpreadsheet);
    }

    public List<ClassSpreadsheet> getClassSpreadSheetByClassId(int classId) {
        ClassEntity classEntity = classRepository.findByClassId(classId);
        if (classEntity == null) {
            throw new RuntimeException("Class not found");
        }
        return classSpreadsheetRepository.findByClassEntity(classEntity);
    }

    public List<ClassSpreadsheet> getClassSpreadsheetsByClassId(Integer classId) {
        // Assuming you have a repository method for this
        // If not, you'll need to add one to your repository
        return classSpreadsheetRepository.findByClassEntity_ClassId(classId);
    }

    public Optional<ClassEntity> findClassByNameAndTeacher(String className, TeacherEntity teacher) {
        List<ClassEntity> teacherClasses = classRepository.findByTeacher(teacher);
        return teacherClasses.stream()
                .filter(c -> c.getClassName().equalsIgnoreCase(className))
                .findFirst();
    }

    @Transactional
    public ClassSpreadsheet updateExistingClassData(ClassEntity existingClass, MultipartFile file, TeacherEntity teacher) throws IOException {
        List<ClassSpreadsheet> existingSpreadsheets = classSpreadsheetRepository.findByClassEntity(existingClass);
        if (existingSpreadsheets.isEmpty()) {
            throw new RuntimeException("No existing spreadsheet record found to update for class: " + existingClass.getClassName());
        }
        ClassSpreadsheet classSpreadsheetToUpdate = existingSpreadsheets.get(0);

        List<Map<String, String>> newRecordsMaps = parseClassRecord(file);
        Map<String, Integer> newMaxAssessmentValues = getMaxAssessmentValue(file);

        classSpreadsheetToUpdate.setFileName(file.getOriginalFilename()); // Update filename

        Map<String, Integer> mergedMaxValues = new HashMap<>(classSpreadsheetToUpdate.getAssessmentMaxValues() != null ? classSpreadsheetToUpdate.getAssessmentMaxValues() : new HashMap<>());
        mergedMaxValues.putAll(newMaxAssessmentValues);
        classSpreadsheetToUpdate.setAssessmentMaxValues(mergedMaxValues);

        Map<String, GradeRecordsEntity> currentGradeRecordsMap = new HashMap<>();
        if (classSpreadsheetToUpdate.getGradeRecords() != null) {
            for (GradeRecordsEntity gre : classSpreadsheetToUpdate.getGradeRecords()) {
                if (gre.getStudentNumber() != null) {
                    currentGradeRecordsMap.put(gre.getStudentNumber(), gre);
                }
            }
        } else {
            classSpreadsheetToUpdate.setGradeRecords(new ArrayList<>());
        }


        Set<StudentEntity> studentsInClass = existingClass.getStudents() != null ? new HashSet<>(existingClass.getStudents()) : new HashSet<>();

        for (Map<String, String> newRecordMap : newRecordsMaps) {
            String studentNumber = newRecordMap.get("Student Number");
            String studentFirstName = newRecordMap.get("First Name");
            String studentLastName = newRecordMap.get("Last Name");

            if (studentNumber == null || studentNumber.trim().isEmpty()) {
                logger.warn("Skipping record due to missing student number: {}", newRecordMap);
                continue;
            }

            GradeRecordsEntity gradeRecordToUpdate = currentGradeRecordsMap.get(studentNumber);

            if (gradeRecordToUpdate != null) {
                Map<String, String> mergedGrades = new HashMap<>(gradeRecordToUpdate.getGrades());
                newRecordMap.forEach((key, value) -> {
                    if (value != null && !value.trim().isEmpty()) {
                        mergedGrades.put(key, value);
                    }
                });
                gradeRecordToUpdate.setGrades(mergedGrades);
            } else {
                gradeRecordToUpdate = createGradeRecordWithStudentAssociation(
                        studentNumber, studentFirstName, studentLastName, classSpreadsheetToUpdate, newRecordMap);
                classSpreadsheetToUpdate.getGradeRecords().add(gradeRecordToUpdate);
            }
            if (gradeRecordToUpdate.getStudent() != null) {
                studentsInClass.add(gradeRecordToUpdate.getStudent());
            }
        }

        existingClass.setStudents(studentsInClass);
        existingClass.setUpdatedAt(new Date());
        classRepository.save(existingClass);

        return classSpreadsheetRepository.save(classSpreadsheetToUpdate);
    }

    @Transactional
    public ClassSpreadsheet replaceClassDataFromFile(ClassEntity existingClass, MultipartFile file, TeacherEntity teacher) throws IOException {
        List<ClassSpreadsheet> existingSpreadsheets = classSpreadsheetRepository.findByClassEntity(existingClass);
        if (existingSpreadsheets.isEmpty()) {
            // If no spreadsheet exists, this could technically be a "create new" for this class
            // but the flow implies we are replacing an existing one.
            // For robust handling, you might create one if it's missing, or throw a specific error.
            // For now, let's assume one should exist if we're "replacing" its data.
            throw new RuntimeException("No spreadsheet found for class " + existingClass.getClassName() + " to replace.");
        }
        ClassSpreadsheet spreadsheetToReplace = existingSpreadsheets.get(0);

        // Delete old grade records. Clearing the collection and relying on orphanRemoval=true is cleaner.
        if (spreadsheetToReplace.getGradeRecords() != null) {
            gradeRecordRepository.deleteAll(spreadsheetToReplace.getGradeRecords()); // Explicitly delete
            spreadsheetToReplace.getGradeRecords().clear(); // Clear the collection
        } else {
            spreadsheetToReplace.setGradeRecords(new ArrayList<>());
        }
        // Flush to ensure deletes are processed before new inserts, if necessary,
        // especially if student numbers could be re-used immediately (though less likely with UUIDs or sequences for IDs).
        // classSpreadsheetRepository.flush(); // If issues persist with constraints

        List<Map<String, String>> newRecordsMap = parseClassRecord(file);
        Map<String, Integer> newMaxAssessmentValues = getMaxAssessmentValue(file);

        spreadsheetToReplace.setFileName(file.getOriginalFilename());
        // spreadsheetToReplace.setClassName(extractFileName(file.getOriginalFilename())); // ClassName of ClassSpreadsheet entity, not ClassEntity
        spreadsheetToReplace.setAssessmentMaxValues(newMaxAssessmentValues);
        // uploadedBy should be the same teacher

        List<GradeRecordsEntity> newGradeRecordsList = new ArrayList<>();
        Set<StudentEntity> studentsInClass = new HashSet<>();

        for (Map<String, String> recordMap : newRecordsMap) {
            String studentNumber = recordMap.get("Student Number");
            String studentFirstName = recordMap.get("First Name");
            String studentLastName = recordMap.get("Last Name");

            if (studentNumber == null || studentNumber.trim().isEmpty()) {
                logger.warn("Skipping record in replace due to missing student number: {}", recordMap);
                continue;
            }

            GradeRecordsEntity newGradeRecord = createGradeRecordWithStudentAssociation(
                    studentNumber, studentFirstName, studentLastName, spreadsheetToReplace, recordMap
            );
            newGradeRecordsList.add(newGradeRecord);
            if (newGradeRecord.getStudent() != null) {
                studentsInClass.add(newGradeRecord.getStudent());
            }
        }
        spreadsheetToReplace.setGradeRecords(newGradeRecordsList); // Set the new list

        existingClass.setStudents(studentsInClass);
        existingClass.setUpdatedAt(new Date());
        classRepository.save(existingClass);

        return classSpreadsheetRepository.save(spreadsheetToReplace);
    }

    public Map<String, Integer> getMaxAssessmentValuesByClassId(Integer classId) {
        List<ClassSpreadsheet> spreadsheets = classSpreadsheetRepository.findByClassEntity_ClassId(classId);
        if (spreadsheets.isEmpty()) {
            throw new RuntimeException("No class spreadsheet found for class ID: " + classId);
        }
        return spreadsheets.get(0).getAssessmentMaxValues();
    }

    //LEGACY METHOD FOR VALIDATING GRADES AGAINST MAX VALUES
    private void validateGradesAgainstMaxValues(Map<String, String> record, Map<String, Integer> maxAssessmentValues, int rowNumber, String studentNumber) {
        if (maxAssessmentValues == null || maxAssessmentValues.isEmpty()) {
            return; // No validation if max values are not available
        }

        for (Map.Entry<String, String> entry : record.entrySet()) {
            String assessmentName = entry.getKey();
            String gradeValue = entry.getValue();

            // Skip non-assessment fields
            if (assessmentName.equals("First Name") || assessmentName.equals("Last Name") ||
                    assessmentName.equals("Student Number") || assessmentName.equals("StudentNumber")) {
                continue;
            }

            // Check if this assessment has a maximum value defined
            if (maxAssessmentValues.containsKey(assessmentName)) {
                Integer maxValue = maxAssessmentValues.get(assessmentName);

                if (gradeValue != null && !gradeValue.trim().isEmpty()) {
                    try {
                        double grade = Double.parseDouble(gradeValue.trim());
                        if (grade > maxValue) {
                            throw new GradeExceedsMaximumException(grade, maxValue, rowNumber, studentNumber);
                        }
                    } catch (NumberFormatException e) {
                        // Skip validation for non-numeric grades (e.g., "A", "B", "Incomplete")
                        logger.debug("Skipping grade validation for non-numeric value: {} in assessment: {}", gradeValue, assessmentName);
                    }
                }
            }
        }
    }

    /**
     * Pre-validates all records for grade constraints before saving.
     * Throws GradeValidationException if any validation errors are found.
     *
     * @param records List of grade records to validate
     * @param maxAssessmentValues Map of assessment names to their maximum allowed values
     * @throws GradeValidationException if any grades exceed maximum values
     */
    public void preValidateAllRecords(List<Map<String, String>> records, Map<String, Integer> maxAssessmentValues) {
        List<GradeErrorDetail> validationErrors = new ArrayList<>();
        int rowNumber = 3; // Starting row number (assuming headers are in rows 1-2)

        for (Map<String, String> record : records) {
            String studentNumber = getStudentNumber(record);

            // Validate each assessment in the record
            for (Map.Entry<String, String> entry : record.entrySet()) {
                String assessmentName = entry.getKey();
                String gradeValue = entry.getValue();

                // Skip non-assessment fields
                if (assessmentName.equals("First Name") || assessmentName.equals("Last Name") ||
                        assessmentName.equals("Student Number") || assessmentName.equals("StudentNumber")) {
                    continue;
                }

                // Check if this assessment has a maximum value defined
                if (maxAssessmentValues.containsKey(assessmentName)) {
                    Integer maxValue = maxAssessmentValues.get(assessmentName);

                    if (gradeValue != null && !gradeValue.trim().isEmpty()) {
                        try {
                            double grade = Double.parseDouble(gradeValue.trim());
                            if (grade > maxValue) {
                                validationErrors.add(new GradeErrorDetail(
                                        String.format("Grade %.1f exceeds maximum of %d", grade, maxValue),
                                        rowNumber,
                                        studentNumber,
                                        assessmentName,  // Now we have access to the assessment name
                                        grade,           // Actual grade value
                                        maxValue.doubleValue()  // Max value
                                ));
                            }
                        } catch (NumberFormatException e) {
                            // Skip validation for non-numeric grades
                            logger.debug("Skipping grade validation for non-numeric value: {} in assessment: {}", gradeValue, assessmentName);
                        }
                    }
                }
            }
            rowNumber++;
        }

        // If there are any validation errors, throw an exception with all errors
        if (!validationErrors.isEmpty()) {
            throw new GradeValidationException("Grade validation failed for multiple records", validationErrors);
        }
    }

    /**
     * Helper method to extract student number from record with fallback options
     */
    private String getStudentNumber(Map<String, String> record) {
        String studentNumber = record.get("Student Number");
        if (studentNumber == null) {
            studentNumber = record.get("StudentNumber");
        }
        return studentNumber;
    }
}
