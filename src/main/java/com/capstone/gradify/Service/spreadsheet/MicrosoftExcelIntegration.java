package com.capstone.gradify.Service.spreadsheet;

import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.io.IOException;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Entity.user.StudentEntity;

// OkHttp imports
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.FormBody;
import okhttp3.Response;

// Jackson imports for JSON parsing
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Other imports
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MicrosoftExcelIntegration implements CloudSpreadsheetInterface {

    private final ClassSpreadsheetService classSpreadsheetService;
    private final ClassRepository classRepository;
    
    @Value("${microsoft.graph.client.id:}")
    private String clientId;
    
    @Value("${microsoft.graph.client.secret:}")
    private String clientSecret;
    
    @Value("${microsoft.graph.tenant.id:}")
    private String tenantId;

    @Autowired
    public MicrosoftExcelIntegration(ClassSpreadsheetService classSpreadsheetService, 
                                   ClassRepository classRepository) {
        this.classSpreadsheetService = classSpreadsheetService;
        this.classRepository = classRepository;
    }

    @Override
    public ClassSpreadsheet processSharedSpreadsheet(String sharedLink, TeacherEntity teacher) 
            throws IOException, GeneralSecurityException {
        
        try {
            System.out.println("Processing SharePoint URL: " + sharedLink);
            
            // For SharePoint sharing URLs, we need to use the sharing API directly
            String accessToken = getAccessToken();
            
            // Download the Excel file using the sharing URL
            byte[] excelData = downloadExcelFileFromSharingUrl(sharedLink, accessToken);
            
            // Convert to MultipartFile for processing
            MultipartFile multipartFile = createMultipartFileFromBytes(excelData, "shared_excel.xlsx");
            
            // Process the file using existing spreadsheet service
            List<Map<String, String>> records = classSpreadsheetService.parseClassRecord(multipartFile);
            Map<String, Integer> maxAssessmentValue = classSpreadsheetService.getMaxAssessmentValue(multipartFile);
            
            // Create class entity
            ClassEntity classEntity = classSpreadsheetService.createClassEntityFromSpreadsheet(
                multipartFile, records, teacher);
            classEntity = classRepository.save(classEntity);
            
            // Save the spreadsheet record
            ClassSpreadsheet savedSpreadsheet = classSpreadsheetService.saveRecord(
                "Microsoft Excel - " + extractFileName(sharedLink),
                teacher,
                records,
                classEntity,
                maxAssessmentValue
            );
            
            // Update class with students
            Set<StudentEntity> students = new HashSet<>();
            savedSpreadsheet.getGradeRecords().forEach(record -> {
                if (record.getStudent() != null) {
                    students.add(record.getStudent());
                }
            });
            
            classEntity.setStudents(students);
            classRepository.save(classEntity);
            
            return savedSpreadsheet;
            
        } catch (Exception e) {
            System.err.println("Error processing Microsoft Excel spreadsheet: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to process Microsoft Excel spreadsheet: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canProcessLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return false;
        }
        
        // Check for various Microsoft Excel Online URL patterns
        return link.contains("onedrive.live.com") ||
               link.contains("1drv.ms") ||
               link.contains("sharepoint.com") ||
               link.contains("office.com/x/") ||
               link.contains("excel.office.com") ||
               (link.contains("microsoft.com") && link.contains("excel"));
    }

    /**
     * Download Excel file directly from SharePoint sharing URL using Microsoft Graph API
     */
    private byte[] downloadExcelFileFromSharingUrl(String sharingUrl, String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();
        
        System.out.println("Downloading from sharing URL: " + sharingUrl);
        
        try {
            // Step 1: Encode the sharing URL for the Graph API
            String encodedUrl = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sharingUrl.getBytes(StandardCharsets.UTF_8));
            
            // Step 2: Use the shares endpoint to access the shared file
            String graphUrl = "https://graph.microsoft.com/v1.0/shares/u!" + encodedUrl + "/driveItem/content";
            
            System.out.println("Graph API URL: " + graphUrl);
            
            Request request = new Request.Builder()
                .url(graphUrl)
                .header("Authorization", "Bearer " + accessToken)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                System.out.println("Response status: " + response.code());
                System.out.println("Response message: " + response.message());
                
                if (response.isSuccessful() && response.body() != null) {
                    byte[] data = response.body().bytes();
                    System.out.println("Successfully downloaded " + data.length + " bytes");
                    return data;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    System.err.println("Error response: " + responseBody);
                    
                    // If direct access fails, try alternative approaches
                    return tryAlternativeDownloadMethods(sharingUrl, accessToken);
                }
            }
        } catch (Exception e) {
            System.err.println("Primary download method failed: " + e.getMessage());
            e.printStackTrace();
            
            // Try alternative methods
            return tryAlternativeDownloadMethods(sharingUrl, accessToken);
        }
    }

    /**
     * Try alternative methods to download the file
     */
    private byte[] tryAlternativeDownloadMethods(String sharingUrl, String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();
        
        // Method 1: Try without encoding special characters
        try {
            String simpleEncodedUrl = java.util.Base64.getUrlEncoder()
                .encodeToString(sharingUrl.getBytes(StandardCharsets.UTF_8));
            String graphUrl = "https://graph.microsoft.com/v1.0/shares/" + simpleEncodedUrl + "/driveItem/content";
            
            System.out.println("Trying alternative method 1: " + graphUrl);
            
            Request request = new Request.Builder()
                .url(graphUrl)
                .header("Authorization", "Bearer " + accessToken)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] data = response.body().bytes();
                    System.out.println("Alternative method 1 succeeded: " + data.length + " bytes");
                    return data;
                }
            }
        } catch (Exception e) {
            System.out.println("Alternative method 1 failed: " + e.getMessage());
        }

        // Method 2: Try to get file info first, then download
        try {
            String encodedUrl = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sharingUrl.getBytes(StandardCharsets.UTF_8));
            String infoUrl = "https://graph.microsoft.com/v1.0/shares/u!" + encodedUrl + "/driveItem";
            
            System.out.println("Getting file info from: " + infoUrl);
            
            Request infoRequest = new Request.Builder()
                .url(infoUrl)
                .header("Authorization", "Bearer " + accessToken)
                .build();
            
            try (Response infoResponse = client.newCall(infoRequest).execute()) {
                if (infoResponse.isSuccessful() && infoResponse.body() != null) {
                    String responseBody = infoResponse.body().string();
                    System.out.println("File info response: " + responseBody);
                    
                    // Parse the response to get download URL
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode fileInfo = mapper.readTree(responseBody);
                    
                    if (fileInfo.has("@microsoft.graph.downloadUrl")) {
                        String downloadUrl = fileInfo.get("@microsoft.graph.downloadUrl").asText();
                        System.out.println("Found direct download URL: " + downloadUrl);
                        
                        Request downloadRequest = new Request.Builder()
                            .url(downloadUrl)
                            .build(); // No auth needed for direct download URL
                        
                        try (Response downloadResponse = client.newCall(downloadRequest).execute()) {
                            if (downloadResponse.isSuccessful() && downloadResponse.body() != null) {
                                byte[] data = downloadResponse.body().bytes();
                                System.out.println("Direct download succeeded: " + data.length + " bytes");
                                return data;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Alternative method 2 failed: " + e.getMessage());
        }

        throw new IOException("All download methods failed for SharePoint file");
    }

    /**
     * Get access token for Microsoft Graph API using client credentials flow
     */
    private String getAccessToken() throws IOException {
        System.out.println("Getting access token...");
        System.out.println("Client ID configured: " + !clientId.isEmpty());
        System.out.println("Tenant ID configured: " + !tenantId.isEmpty());
        System.out.println("Client Secret configured: " + !clientSecret.isEmpty());
        
        if (clientId.isEmpty() || clientSecret.isEmpty() || tenantId.isEmpty()) {
            throw new IOException("Microsoft Graph credentials not configured. Please set:\n" +
                "- microsoft.graph.client.id\n" +
                "- microsoft.graph.client.secret\n" +
                "- microsoft.graph.tenant.id");
        }

        OkHttpClient client = new OkHttpClient();
        
        RequestBody formBody = new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("scope", "https://graph.microsoft.com/.default")
            .build();
        
        Request request = new Request.Builder()
            .url("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")
            .post(formBody)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            System.out.println("Token response status: " + response.code());
            
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                System.err.println("Token request failed: " + responseBody);
                throw new IOException("Failed to get access token: " + response.code() + " - " + response.message());
            }
            
            String responseBody = response.body().string();
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);
            
            if (!jsonNode.has("access_token")) {
                throw new IOException("Access token not found in response");
            }
            
            String token = jsonNode.get("access_token").asText();
            System.out.println("Access token obtained successfully");
            return token;
            
        } catch (Exception e) {
            System.err.println("Error getting access token: " + e.getMessage());
            throw new IOException("Failed to get access token: " + e.getMessage(), e);
        }
    }

    /**
     * Create MultipartFile from byte array
     */
    private MultipartFile createMultipartFileFromBytes(byte[] data, String filename) {
        return new MultipartFile() {
            @Override
            public String getName() { return "file"; }
            
            @Override
            public String getOriginalFilename() { return filename; }
            
            @Override
            public String getContentType() { 
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; 
            }
            
            @Override
            public boolean isEmpty() { return data.length == 0; }
            
            @Override
            public long getSize() { return data.length; }
            
            @Override
            public byte[] getBytes() { return data; }
            
            @Override
            public InputStream getInputStream() { return new ByteArrayInputStream(data); }
            
            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                Files.write(dest.toPath(), data);
            }
        };
    }

    /**
     * Extract filename from URL for display purposes
     */
    private String extractFileName(String url) {
        try {
            // Try to extract filename from URL path
            String[] pathParts = url.split("/");
            for (int i = pathParts.length - 1; i >= 0; i--) {
                if (pathParts[i].contains(".xlsx") || pathParts[i].contains(".xls")) {
                    return pathParts[i];
                }
            }
            return "Shared Excel File";
        } catch (Exception e) {
            return "Shared Excel File";
        }
    }
}