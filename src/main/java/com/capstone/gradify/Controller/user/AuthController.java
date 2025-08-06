package com.capstone.gradify.Controller.user;

import com.azure.core.credential.AccessToken;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.spreadsheet.MicrosoftGraphTokenService;
import com.capstone.gradify.Service.userservice.AuthService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.response.AuthResult;
import com.capstone.gradify.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.microsoft.graph.models.User;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final MicrosoftGraphTokenService microsoftGraphTokenService;
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
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            response.sendRedirect("http://localhost:5173/auth/azure/callback?error=" + error);
            return null;
        }

        try{
            AuthResult authResult = authService.authenticateWithCode(code);
            User azureUser = authResult.getUser();
            AccessToken accessToken = authResult.getToken();

            UserEntity user = userService.findOrCreateFromAzure(azureUser);

            microsoftGraphTokenService.storeUserTokenDirect(user.getUserId(), accessToken);

            String jwtToken = jwtUtil.generateToken(user);

            String redirectUrl = String.format(
                    "http://localhost:5173/auth/azure/callback?token=%s&userId=%d&email=%s&name=%s&role=%s",
                    jwtToken,
                    user.getUserId(),
                    user.getEmail(),
                    Objects.requireNonNull(azureUser.getDisplayName()),
                    user.getRole() != null ? user.getRole().name() : "UNKNOWN"
            );
            response.sendRedirect(redirectUrl);
            return null;
        } catch (Exception e) {
            response.sendRedirect("http://localhost:5173/auth/azure/callback?error=auth_failed&message=" +
                    URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            return null;
        }
    }
}
