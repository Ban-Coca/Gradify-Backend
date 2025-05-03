package com.capstone.gradify.Service;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class ClassSpreadsheetService {

    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;

    public ClassSpreadsheetService() {
        super();
    }

    public ClassSpreadsheet saveRecord(String fileName, TeacherEntity teacher, List<Map<String, String>> records){
        ClassSpreadsheet classSpreadsheet = new ClassSpreadsheet();
        classSpreadsheet.setFileName(fileName);
        classSpreadsheet.setUploadedBy(teacher);
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
        // Logic to parse the spreadsheet file and extract records
        // For example, using Apache POI or any other library to read Excel files

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

    private String getCellValue(Cell cell) {
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
    private String determineCurrentSemester() {
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

    private String determineCurrentSchoolYear() {
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

    private String generateClassCode(String className, String semester, String year, String section) {
        // Remove spaces and special characters
        String baseCode = className.replaceAll("[^a-zA-Z0-9]", "");

        // Take first 3 chars of class name + first char of semester + last 2 digits of year + section
        String code = baseCode.substring(0, Math.min(3, baseCode.length())) +
                semester.substring(0, 1) +
                year.substring(Math.max(0, year.length() - 2)) +
                section;

        return code.toUpperCase();
    }

    private String generateRandomClassCode() {
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

    // Update the saveRecord method to include ClassEntity
    public ClassSpreadsheet saveRecord(String filename, TeacherEntity teacher,
                                       List<Map<String, String>> records, ClassEntity classEntity) {
        ClassSpreadsheet spreadsheet = new ClassSpreadsheet();
        spreadsheet.setFileName(filename);
        spreadsheet.setUploadedBy(teacher);
        spreadsheet.setClassName(classEntity.getClassName()); // Set the class name from ClassEntity
        spreadsheet.setClassEntity(classEntity);
        // Create grade records
        List<GradeRecordsEntity> gradeRecords = new ArrayList<>();
        for (Map<String, String> record : records) {
            GradeRecordsEntity gradeRecord = new GradeRecordsEntity();
            gradeRecord.setStudentNumber(record.get("Student Number")); // Adjust based on your data structure
            gradeRecord.setClassRecord(spreadsheet);


            gradeRecord.setGrades(record);

            gradeRecords.add(gradeRecord);
        }

        spreadsheet.setGradeRecords(gradeRecords);


        return classSpreadsheetRepository.save(spreadsheet);
    }
}
