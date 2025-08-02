package com.capstone.gradify.Controller.user;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.userservice.AuthService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.microsoft.graph.models.User;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private JwtUtil jwtUtil;
    @GetMapping("/azure/login")
    public ResponseEntity<?> initialAzureLogin(){
        try {
            String authUrl = authService.getAuthorizationUrl();
            return ResponseEntity.ok(Map.of("authUrl", authUrl, "message", "Redirect to Azure for authentication"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate authentication URL", "message", e.getMessage()));
        }
    }

    @GetMapping("/azure/callback")
    public ResponseEntity<?> handleAzureCallback(
            @RequestParam String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authentication failed", "message", error));
        }

        try{
            User azureUser = authService.authenticateWithCode(code);

            UserEntity user = userService.findOrCreateFromAzure(azureUser);

            String jwtToken = jwtUtil.generateToken(user);

            return ResponseEntity.ok(Map.of(
                    "token", jwtToken,
                    "user", Map.of(
                            "id", user.getUserId(),
                            "email", user.getEmail(),
                            "name", Objects.requireNonNull(azureUser.getDisplayName())
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Authentication failed", "message", e.getMessage()));
        }
    }
}
