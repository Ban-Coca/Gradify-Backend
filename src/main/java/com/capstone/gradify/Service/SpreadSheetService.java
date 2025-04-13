package com.capstone.gradify.Service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class SpreadSheetService {

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
}
