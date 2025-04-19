package com.capstone.gradify.Service;

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
    ClassSpreadsheetRepository classSpreadsheetRepository;

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
}
