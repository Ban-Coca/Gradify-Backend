package com.capstone.gradify.Service.spreadsheet;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleSpreadsheetService implements CloudSpreadsheetInterface {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);

    @Value("${google.credentials.file-path:classpath:credentials/google-sheets-credentials.json}")
    private Resource googleCredentialsFile;

    @Autowired
    private ClassSpreadsheetService classSpreadsheetService;

    @Autowired
    private ClassRepository classRepository;

    @Override
    public ClassSpreadsheet processSharedSpreadsheet(String sharedLink, TeacherEntity teacher)
            throws IOException, GeneralSecurityException {

        // Extract spreadsheet ID from the shared link
        String spreadsheetId = extractSpreadsheetId(sharedLink);
        if (spreadsheetId == null) {
            throw new IllegalArgumentException("Invalid Google Sheets URL: " + sharedLink);
        }

        // Set up Google Sheets API client
        Sheets sheetsService = createSheetsService();

        // Get spreadsheet metadata to determine sheet names and title
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        String spreadsheetName = spreadsheet.getProperties().getTitle();

        List<Sheet> sheets = spreadsheet.getSheets();
        if (sheets == null || sheets.isEmpty()) {
            throw new IOException("Spreadsheet has no sheets");
        }

        // Process the first sheet (can be modified to process all sheets if needed)
        String sheetName = sheets.get(0).getProperties().getTitle();
        String range = sheetName; // This gets all data from the sheet

        // Get the values from the sheet
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            throw new IOException("No data found in spreadsheet");
        }

        // Convert Google Sheets data to the format expected by ClassSpreadsheetService
        List<Map<String, String>> records = convertToRecords(values);

        // Create ClassEntity from spreadsheet data
        ClassEntity classEntity = new ClassEntity();
        classEntity.setTeacher(teacher);

        // Extract class details from spreadsheet name
        String cleanName = cleanSpreadsheetName(spreadsheetName);
        String[] parts = cleanName.split("-");

        if (parts.length >= 2) {
            classEntity.setClassName(parts[0].trim());
            classEntity.setSection(parts[1].trim());
        } else {
            classEntity.setClassName(cleanName);
        }

        // Generate class code
        classEntity.setClassCode(classSpreadsheetService.generateRandomClassCode());

        // Set other ClassEntity properties
        Date now = new Date();
        classEntity.setCreatedAt(now);
        classEntity.setUpdatedAt(now);
        classEntity.setSemester(classSpreadsheetService.determineCurrentSemester());
        classEntity.setSchoolYear(classSpreadsheetService.determineCurrentSchoolYear());

        // Save the ClassEntity
        classEntity = classRepository.save(classEntity);

        // Create and save the ClassSpreadsheet
        return classSpreadsheetService.saveRecord(
                spreadsheetName + ".sheet",
                teacher,
                records,
                classEntity,
                Collections.emptyMap());// Placeholder for maxAssessmentValue);
    }

    private String cleanSpreadsheetName(String name) {
        // Remove any file extension if present
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    @Override
    public boolean canProcessLink(String link) {
        // Check if the link is a Google Sheets URL
        if (link == null) return false;

        // Google Sheets patterns
        String[] patterns = {
                "^https?://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]+)",
                "^https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9-_]+)",
                "^https?://sheets\\.google\\.com/([a-zA-Z0-9-_]+)"
        };

        for (String regex : patterns) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(link);
            if (matcher.find()) {
                return true;
            }
        }

        return false;
    }

    private String extractSpreadsheetId(String sharedLink) {
        String[] patterns = {
                "^https?://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]+).*",
                "^https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9-_]+).*",
                "^https?://sheets\\.google\\.com/([a-zA-Z0-9-_]+).*"
        };

        for (String regex : patterns) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(sharedLink);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Load credentials from the resource file
        InputStream credentialsStream = googleCredentialsFile.getInputStream();
        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(SCOPES);

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Sheets.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName("Gradify")
                .build();
    }

    private List<Map<String, String>> convertToRecords(List<List<Object>> values) {
        List<Map<String, String>> records = new ArrayList<>();

        if (values.size() < 2) {
            // Need at least header row and one data row
            return records;
        }

        // First row is headers
        List<Object> headerRow = values.get(0);
        List<String> headers = new ArrayList<>();
        for (Object header : headerRow) {
            headers.add(header.toString().trim());
        }

        // Remaining rows are data
        for (int i = 1; i < values.size(); i++) {
            List<Object> dataRow = values.get(i);
            Map<String, String> record = new HashMap<>();

            for (int j = 0; j < Math.min(headers.size(), dataRow.size()); j++) {
                String header = headers.get(j);
                String value = dataRow.get(j).toString();
                record.put(header, value);
            }

            // Fill missing values with empty strings
            for (int j = dataRow.size(); j < headers.size(); j++) {
                record.put(headers.get(j), "");
            }

            // Ensure we have student number, first name, and last name
            ensureStudentFields(record, headers);

            records.add(record);
        }

        return records;
    }

    private void ensureStudentFields(Map<String, String> record, List<String> headers) {
        // Check for student number field
        if (!record.containsKey("Student Number")) {
            // Try alternative keys
            for (String header : headers) {
                if (header.toLowerCase().contains("student") &&
                        (header.toLowerCase().contains("id") || header.toLowerCase().contains("number"))) {
                    record.put("Student Number", record.get(header));
                    break;
                }
            }
        }

        // Check for name fields
        if (!record.containsKey("First Name") && !record.containsKey("Last Name")) {
            // Try to find name fields
            String fullName = null;

            // Look for full name field
            for (String header : headers) {
                if (header.equalsIgnoreCase("Name") || header.equalsIgnoreCase("Full Name")) {
                    fullName = record.get(header);
                    break;
                }
            }

            // If we found a full name, split it
            if (fullName != null && !fullName.trim().isEmpty()) {
                String[] nameParts = fullName.trim().split("\\s+", 2);
                record.put("First Name", nameParts[0]);
                if (nameParts.length > 1) {
                    record.put("Last Name", nameParts[1]);
                } else {
                    record.put("Last Name", "");
                }
            }
        }
    }
}