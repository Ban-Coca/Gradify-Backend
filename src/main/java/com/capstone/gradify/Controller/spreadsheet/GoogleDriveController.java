package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserRepository;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.Service.spreadsheet.CloudSpreadsheetManager;
import com.capstone.gradify.Service.spreadsheet.GoogleSpreadsheetService;
import com.capstone.gradify.Service.userservice.TeacherService;
import com.capstone.gradify.Service.userservice.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/google")
@RequiredArgsConstructor
public class GoogleDriveController {
    private final UserService userService;
    private final UserTokenRepository userTokenRepository;
    private final CloudSpreadsheetManager cloudSpreadsheetManager;
    private final GoogleSpreadsheetService googleSpreadsheetService;
    private final TeacherService teacherService;

    @GetMapping("/access-token")
    public ResponseEntity<?> getAccessToken(@RequestParam int userId) {
        UserEntity user = userService.findById(userId);
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + userId);
        }

        UserToken userToken = userTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Access token not found for user ID: " + userId));

        return ResponseEntity.ok(Map.of("accessToken", userToken.getAccessToken()));
    }

    @PostMapping("/drive/save")
    public ResponseEntity<?> savedSheetFromDrive(@RequestParam int userId, @RequestParam String urlLink) throws GeneralSecurityException, IOException {
        if(!cloudSpreadsheetManager.canProcessLink(urlLink)){
            throw new RuntimeException("The provided link format is unsupported.");
        }
        TeacherEntity teacher = teacherService.findByUserId(userId);
        ClassSpreadsheet spreadsheet = googleSpreadsheetService.processSharedSpreadsheet(urlLink, teacher);
        if (spreadsheet == null) {
            throw new RuntimeException("Failed to process the shared spreadsheet from the provided link.");
        }
        return ResponseEntity.ok(spreadsheet);
    }
}
