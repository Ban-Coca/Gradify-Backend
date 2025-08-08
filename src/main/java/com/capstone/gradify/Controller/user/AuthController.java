package com.capstone.gradify.Controller.user;

import com.azure.core.credential.AccessToken;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.spreadsheet.MicrosoftGraphTokenService;
import com.capstone.gradify.Service.userservice.AuthService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.response.AuthResult;
import com.capstone.gradify.dto.response.TokenResponse;
import com.capstone.gradify.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.microsoft.graph.models.User;
import org.springframework.web.client.RestTemplate;

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
            TokenResponse tokenResponse = microsoftGraphTokenService.exchangeCodeForTokens(code);

            if (tokenResponse.getRefreshToken() == null || tokenResponse.getRefreshToken().isEmpty()) {
                throw new RuntimeException("No refresh token received from Microsoft. User may need to re-consent with offline access permissions.");
            }
            User azureUser = getUserInfoFromToken(tokenResponse.getAccessToken());

            UserEntity user = userService.findOrCreateFromAzure(azureUser);
            microsoftGraphTokenService.storeUserTokenDirect(user.getUserId(), tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), tokenResponse.getExpiresIn());

            String jwtToken = jwtUtil.generateToken(user);

            String redirectUrl = String.format(
                    "http://localhost:5173/auth/azure/callback?token=%s&userId=%d&email=%s&firstName=%s&lastName=%s&role=%s&provider=Microsoft",
                    jwtToken,
                    user.getUserId(),
                    user.getEmail(),
                    Objects.requireNonNull(azureUser.getGivenName()),
                    Objects.requireNonNull(azureUser.getSurname()),
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

    private User getUserInfoFromToken(String accessToken) {
        String userInfoEndpoint = "https://graph.microsoft.com/v1.0/me";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoEndpoint,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> userInfo = response.getBody();

            // Create User object from the response
            User user = new User();
            user.setId((String) userInfo.get("id"));
            user.setMail((String) userInfo.get("mail"));
            user.setUserPrincipalName((String) userInfo.get("userPrincipalName"));
            user.setGivenName((String) userInfo.get("givenName"));
            user.setSurname((String) userInfo.get("surname"));
            user.setDisplayName((String) userInfo.get("displayName"));

            return user;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user info: " + e.getMessage(), e);
        }
    }

}
