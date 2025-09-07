package com.capstone.gradify.Controller.spreadsheet;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserRepository;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.Service.userservice.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/google")
@RequiredArgsConstructor
public class GoogleDriveController {
    private final UserService userService;
    private final UserTokenRepository userTokenRepository;

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

    @PostMapping("drive/save")
    public ResponseEntity<?> savedSheetFromDrive(){

    }
}
