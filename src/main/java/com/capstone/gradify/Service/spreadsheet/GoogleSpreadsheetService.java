package com.capstone.gradify.Service.spreadsheet;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSpreadsheetService implements CloudSpreadsheetInterface {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);

    @Value("${google.sheets.credentials}")
    private String googleCredentialsPath;

    private final ClassSpreadsheetService classSpreadsheetService;
    private final ClassSpreadsheetRepository classSpreadsheetRepository;
    private final ClassRepository classRepository;

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
        log.debug("The spreadsheet name is {}", spreadsheetName);

        List<ClassSpreadsheet> exists = classSpreadsheetRepository.findByUploadedBy_UserIdAndFileName(teacher.getUserId(), spreadsheetName + ".sheet");

        if (!exists.isEmpty() && exists.get(0).getFileName().equals(spreadsheetName + ".sheet")) {
            throw new IllegalArgumentException(String.format("Spreadsheet with the name %s already exists", spreadsheetName));
        }

        List<Sheet> sheets = spreadsheet.getSheets();
        if (sheets == null || sheets.isEmpty()) {
            throw new IOException("Spreadsheet has no sheets");
        }

        // Get the values from the sheet
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheets.get(0).getProperties().getTitle())
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            throw new IOException("No data found in spreadsheet");
        }

        List<String> headers = new ArrayList<>();
        for (Object header : values.get(0)) {
            headers.add(header.toString().trim());
        }

        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        if (values.size() > 1) {
            List<Object> maxRow = values.get(1);
            for (int i = 0; i < headers.size(); i++) {
                Object val = i < maxRow.size() ? maxRow.get(i) : null;
                if (val instanceof Number) {
                    maxAssessmentValues.put(headers.get(i), ((Number) val).intValue());
                } else if (val != null) {
                    try {
                        maxAssessmentValues.put(headers.get(i), Integer.parseInt(val.toString()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Convert Google Sheets data to the format expected by ClassSpreadsheetService
        List<Map<String, String>> records = convertToRecords(values);
        classSpreadsheetService.preValidateAllRecords(records, maxAssessmentValues);
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
        ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(
                spreadsheetName + ".sheet",
                teacher,
                records,
                classEntity,
                maxAssessmentValues,
                sharedLink,
                true);

        Set<StudentEntity> students = new HashSet<>();
        savedSpreadsheet.getGradeRecords().forEach(record -> {
            if (record.getStudent() != null) {
                students.add(record.getStudent());
            }
        });
        classEntity.setStudents(students);
        classRepository.save(classEntity);

        return savedSpreadsheet;
    }

    @Scheduled(fixedDelay = 300000) // 5 minutes = 300,000 milliseconds
    @Transactional
    public void pollForSpreadsheetUpdates() {
        log.info("Starting scheduled polling for Google Sheets updates");

        try {
            List<ClassSpreadsheet> activeSpreadsheets = classSpreadsheetService.getActiveGoogleSpreadsheets();

            for (ClassSpreadsheet spreadsheet : activeSpreadsheets) {
                try {
                    checkForUpdates(spreadsheet);
                } catch (Exception e) {
                    log.error("Error checking updates for spreadsheet {}: {}",
                            spreadsheet.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error during scheduled polling: {}", e.getMessage());
        }
    }

    public void triggerManualUpdate(ClassSpreadsheet spreadsheet) {
        if (spreadsheet == null) {
            throw new IllegalArgumentException("Spreadsheet cannot be null");
        }

        try {
            log.info("Manual update triggered for spreadsheet: {}", spreadsheet.getFileName());
            checkForUpdates(spreadsheet);
            log.info("Manual update completed for spreadsheet: {}", spreadsheet.getFileName());
        } catch (Exception e) {
            log.error("Manual update failed for spreadsheet {}: {}", spreadsheet.getFileName(), e.getMessage(), e);
            throw new RuntimeException("Manual update failed for spreadsheet: " + spreadsheet.getFileName(), e);
        }
    }


    @Transactional
    protected void checkForUpdates(ClassSpreadsheet existingSpreadsheet)
            throws IOException, GeneralSecurityException {

        String spreadsheetId = extractSpreadsheetId(existingSpreadsheet.getSharedLink());
        if (spreadsheetId == null) {
            log.warn("Cannot extract spreadsheet ID from link: {}", existingSpreadsheet.getSharedLink());
            return;
        }

        Sheets sheetsService = createSheetsService();
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();

        // Check if the spreadsheet was modified since last sync
        // Note: Google Sheets API doesn't provide last modified time directly
        // So we'll compare the data content

        String sheetName = spreadsheet.getSheets().get(0).getProperties().getTitle();
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName)
                .execute();

        List<List<Object>> currentValues = response.getValues();
        if (currentValues == null || currentValues.isEmpty()) {
            return;
        }

        // Convert current data and compare with existing
        List<Map<String, String>> currentRecords = convertToRecords(currentValues);

        // Check if data has changed by comparing record count and content hash
        if (hasDataChanged(existingSpreadsheet, currentRecords)) {
            log.info("Changes detected in spreadsheet: {}", existingSpreadsheet.getFileName());
            updateSpreadsheetData(existingSpreadsheet, currentRecords, currentValues);
        }
    }

    private boolean hasDataChanged(ClassSpreadsheet existingSpreadsheet,
                                   List<Map<String, String>> newRecords) {

        int existingRecordCount = classSpreadsheetService.getStudentRecordCount(existingSpreadsheet);

        if (existingRecordCount != newRecords.size()) {
            return true;
        }

        // Calculate a simple hash of the data for comparison
        String newDataHash = calculateDataHash(newRecords);
        return !newDataHash.equals(existingSpreadsheet.getDataHash());
    }

    private String calculateDataHash(List<Map<String, String>> records) {
        StringBuilder dataBuilder = new StringBuilder();
        for (Map<String, String> record : records) {
            dataBuilder.append(record.toString());
        }
        return String.valueOf(dataBuilder.toString().hashCode());
    }

    private void updateSpreadsheetData(ClassSpreadsheet existingSpreadsheet,
                                       List<Map<String, String>> newRecords,
                                       List<List<Object>> values) throws IOException {
        ClassEntity classEntity = existingSpreadsheet.getClassEntity();
        // Extract max assessment values from the data
        Map<String, Integer> maxAssessmentValues = new HashMap<>();
        if (values.size() > 1) {
            List<String> headers = new ArrayList<>();
            for (Object header : values.get(0)) {
                headers.add(header.toString().trim());
            }

            List<Object> maxRow = values.get(1);
            for (int i = 0; i < headers.size(); i++) {
                Object val = i < maxRow.size() ? maxRow.get(i) : null;
                if (val instanceof Number) {
                    maxAssessmentValues.put(headers.get(i), ((Number) val).intValue());
                } else if (val != null) {
                    try {
                        maxAssessmentValues.put(headers.get(i), Integer.parseInt(val.toString()));
                    } catch (NumberFormatException ignored) {
                        log.warn("Failed to parse integer for header '{}' with value '{}'", headers.get(i), val);
                    }
                }
            }
        }

        // Validate new records
        classSpreadsheetService.preValidateAllRecords(newRecords, maxAssessmentValues);

        // Update max assessment values in the existing spreadsheet
        // This ensures any new assessments are added
        existingSpreadsheet.setAssessmentMaxValues(maxAssessmentValues);

        String newDataHash = calculateDataHash(newRecords);
        existingSpreadsheet.setDataHash(newDataHash);

        // Update the spreadsheet data
        ClassSpreadsheet updatedSpreadsheet = classSpreadsheetService.updateSpreadsheet(
                existingSpreadsheet,
                newRecords,
                maxAssessmentValues
        );

        Set<StudentEntity> students = new HashSet<>();
        updatedSpreadsheet.getGradeRecords().forEach(record -> {
            if (record.getStudent() != null) {
                students.add(record.getStudent());
            }
        });
        classEntity.setStudents(students);
        classRepository.save(classEntity);

        log.info("Successfully updated spreadsheet data for: {}", existingSpreadsheet.getFileName());
    }

    private String cleanSpreadsheetName(String name) {
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    @Override
    public boolean canProcessLink(String link) {
        if (link == null) return false;

        String[] patterns = {
                "^https?://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]+)",
                "^https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9-_]+)",
                "^https?://sheets\\.google\\.com/([a-zA-Z0-9-_]+)",
                "^https?://drive\\.google\\.com/file/d/([a-zA-Z0-9-_]+).*"
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

    private String convertDriveUrlToSheetsUrl(String driveUrl) {
        // Check if it's a Drive file URL and convert to Sheets URL
        Pattern drivePattern = Pattern.compile("https://drive\\.google\\.com/file/d/([a-zA-Z0-9-_]+)");
        Matcher matcher = drivePattern.matcher(driveUrl);

        if (matcher.find()) {
            String fileId = matcher.group(1);
            // Convert to Google Sheets URL format
            return "https://docs.google.com/spreadsheets/d/" + fileId + "/edit";
        }

        return driveUrl; // Return original if not a Drive URL
    }

    private String extractSpreadsheetId(String sharedLink) {
        // First, try to convert Drive URLs to Sheets URLs
        String processedLink = convertDriveUrlToSheetsUrl(sharedLink);

        String[] patterns = {
                "^https?://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]+).*",
                "^https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9-_]+).*",
                "^https?://sheets\\.google\\.com/([a-zA-Z0-9-_]+).*",
                "^https?://drive\\.google\\.com/file/d/([a-zA-Z0-9-_]+).*"
        };

        for (String regex : patterns) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(processedLink);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Load credentials from the file path
            InputStream credentialsStream = getCredentialsStream();

            // Create credentials - this should be a service account key file
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(SCOPES);

            // Refresh the credentials to ensure they're valid
            credentials.refreshIfExpired();

            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            return new Sheets.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                    .setApplicationName("Gradify")
                    .build();

        } catch (Exception e) {
            // Log the detailed error for debugging
            System.err.println("Error creating Google Sheets service: " + e.getMessage());

            // Re-throw with a more descriptive message
            throw new GeneralSecurityException("Failed to initialize Google Sheets API: " + e.getMessage(), e);
        }
    }

    private InputStream getCredentialsStream() throws IOException {
        if (googleCredentialsPath == null || googleCredentialsPath.trim().isEmpty()) {
            throw new IOException("Google credentials path is not configured. Please set GOOGLE_SHEETS_CREDENTIALS environment variable.");
        }

        String trimmedPath = googleCredentialsPath.trim();
        log.info("Attempting to load Google credentials...");

        // Check if the content is JSON (starts with '{' and contains service account structure)
        if (trimmedPath.startsWith("{") && trimmedPath.contains("\"type\"") && trimmedPath.contains("\"service_account\"")) {
            try {
                // Parse the JSON to validate and fix formatting
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode credentialsNode = objectMapper.readTree(trimmedPath);

                // Validate required fields
                if (!credentialsNode.has("private_key") || !credentialsNode.has("client_email")) {
                    throw new IOException("Invalid service account JSON: missing required fields");
                }

                // Get the private key and fix its formatting
                String privateKeyValue = credentialsNode.get("private_key").asText();
                String fixedPrivateKey = fixPrivateKeyFormatting(privateKeyValue);

                // Create a new JSON object with the corrected private key
                ObjectNode fixedCredentials = (ObjectNode) credentialsNode;
                fixedCredentials.put("private_key", fixedPrivateKey);

                String correctedJson = objectMapper.writeValueAsString(fixedCredentials);
                log.info("Successfully parsed and corrected service account JSON");

                return new ByteArrayInputStream(correctedJson.getBytes(StandardCharsets.UTF_8));

            } catch (JsonProcessingException e) {
                log.error("Failed to parse service account JSON: {}", e.getMessage());
                throw new IOException("Invalid JSON format in credentials: " + e.getMessage(), e);
            }
        }

        // Handle file paths (existing code)
        if (trimmedPath.startsWith("classpath:")) {
            String resourcePath = trimmedPath.substring("classpath:".length());
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IOException("Could not find credentials file in classpath: " + resourcePath);
            }
            return stream;
        } else if (trimmedPath.startsWith("file:")) {
            String filePath = trimmedPath.substring("file:".length());
            return new FileInputStream(filePath);
        } else {
            return new FileInputStream(trimmedPath);
        }
    }

    private List<Map<String, String>> convertToRecords(List<List<Object>> values) {
        List<Map<String, String>> records = new ArrayList<>();

        if (values.size() < 2) {
            return records;
        }

        // First row is headers
        List<Object> headerRow = values.get(0);
        List<String> headers = new ArrayList<>();
        for (Object header : headerRow) {
            headers.add(header.toString().trim());
        }

        // Remaining rows are data
        for (int i = 2; i < values.size(); i++) {
            List<Object> dataRow = values.get(i);
            Map<String, String> record = new HashMap<>();

            for (int j = 0; j < Math.min(headers.size(), dataRow.size()); j++) {
                String header = headers.get(j);
                String value = dataRow.get(j) != null ? dataRow.get(j).toString() : "";
                record.put(header, value);
            }

            // Fill missing values with empty strings
            for (int j = dataRow.size(); j < headers.size(); j++) {
                record.put(headers.get(j), "");
            }

            ensureStudentFields(record, headers);
            records.add(record);
        }

        return records;
    }

    private void ensureStudentFields(Map<String, String> record, List<String> headers) {
        // Check for student number field
        if (!record.containsKey("Student Number")) {
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
            String fullName = null;

            for (String header : headers) {
                if (header.equalsIgnoreCase("Name") || header.equalsIgnoreCase("Full Name")) {
                    fullName = record.get(header);
                    break;
                }
            }

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

    private String fixPrivateKeyFormatting(String privateKeyValue) {
        if (privateKeyValue == null || privateKeyValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key cannot be null or empty");
        }

        String fixedKey = privateKeyValue.trim();

        // Handle various newline escape scenarios
        fixedKey = fixedKey
                .replace("\\\\n", "\n")     // Double-escaped newlines
                .replace("\\n", "\n")       // Single-escaped newlines
                .replace("\\\\r\\\\n", "\n") // Windows-style double-escaped
                .replace("\\r\\n", "\n")     // Windows-style single-escaped
                .replace("\\=", "=")         // Escaped equals signs
                .replace("\\\"/", "/")       // Escaped forward slashes
                .replace("\\\"", "\"");      // Escaped quotes

        // Ensure proper PEM format structure
        if (!fixedKey.startsWith("-----BEGIN PRIVATE KEY-----")) {
            if (!fixedKey.contains("-----BEGIN PRIVATE KEY-----")) {
                throw new IllegalArgumentException("Invalid private key: missing BEGIN marker");
            }
            // Extract the key content if it's embedded in a larger string
            int beginIndex = fixedKey.indexOf("-----BEGIN PRIVATE KEY-----");
            fixedKey = fixedKey.substring(beginIndex);
        }

        if (!fixedKey.endsWith("-----END PRIVATE KEY-----")) {
            if (!fixedKey.contains("-----END PRIVATE KEY-----")) {
                throw new IllegalArgumentException("Invalid private key: missing END marker");
            }
            // Extract only up to the end marker
            int endIndex = fixedKey.indexOf("-----END PRIVATE KEY-----") + "-----END PRIVATE KEY-----".length();
            fixedKey = fixedKey.substring(0, endIndex);
        }

        // Split the key into lines and rebuild with proper formatting
        String[] lines = fixedKey.split("\n");
        StringBuilder rebuiltKey = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                rebuiltKey.append(line);
                // Add newline except for the last line
                if (i < lines.length - 1) {
                    rebuiltKey.append("\n");
                }
            }
        }

        String result = rebuiltKey.toString();

        // Final validation - ensure the key has the correct structure
        if (!result.startsWith("-----BEGIN PRIVATE KEY-----\n") &&
                !result.startsWith("-----BEGIN PRIVATE KEY-----")) {
            // Add proper newline after BEGIN if missing
            result = result.replace("-----BEGIN PRIVATE KEY-----", "-----BEGIN PRIVATE KEY-----\n");
        }

        if (!result.endsWith("\n-----END PRIVATE KEY-----") &&
                !result.endsWith("-----END PRIVATE KEY-----")) {
            // Add proper newline before END if missing
            result = result.replace("-----END PRIVATE KEY-----", "\n-----END PRIVATE KEY-----");
        }

        // Clean up any double newlines that might have been created
        result = result.replace("\n\n", "\n");

        log.debug("Private key formatting completed successfully");
        return result;
    }
}