//package com.capstone.gradify.Service.spreadsheet;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import java.nio.file.Files;
//import java.io.IOException;
//
//import com.capstone.gradify.Entity.records.ClassEntity;
//import com.capstone.gradify.Entity.records.ClassSpreadsheet;
//import com.capstone.gradify.Entity.user.TeacherEntity;
//import com.capstone.gradify.Repository.records.ClassRepository;
//import com.capstone.gradify.Entity.user.StudentEntity;
//
//// OkHttp imports
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.FormBody;
//import okhttp3.Response;
//
//// Jackson imports for JSON parsing
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//// Other imports
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.ByteArrayInputStream;
//import java.io.File;
//import java.io.InputStream;
//import java.security.GeneralSecurityException;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import java.nio.charset.StandardCharsets;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//@Service
//@RequiredArgsConstructor
//public class MicrosoftExcelIntegration {
//
//    private final ClassSpreadsheetService classSpreadsheetService;
//    private final ClassRepository classRepository;
//
//    @Value("${microsoft.graph.client-id:}")
//    private String clientId;
//
//    @Value("${microsoft.graph.client-secret:}")
//    private String clientSecret;
//
//    @Value("${microsoft.graph.tenant-id:}")
//    private String tenantId;
//
//
//
//    /**
//     * Extract filename from URL for display purposes
//     */
//    private String extractFileName(String url) {
//        try {
//            // Try to extract filename from URL path
//            String[] pathParts = url.split("/");
//            for (int i = pathParts.length - 1; i >= 0; i--) {
//                if (pathParts[i].contains(".xlsx") || pathParts[i].contains(".xls")) {
//                    return pathParts[i];
//                }
//            }
//            return "Shared Excel File";
//        } catch (Exception e) {
//            return "Shared Excel File";
//        }
//    }
//}