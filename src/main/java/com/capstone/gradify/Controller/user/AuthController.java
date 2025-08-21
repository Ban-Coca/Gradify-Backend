package com.capstone.gradify.Controller.user;

import com.azure.core.credential.AccessToken;
import com.capstone.gradify.Config.SecurityConfig;
import com.capstone.gradify.Entity.TempTokens;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.TempTokensRepository;
import com.capstone.gradify.Service.spreadsheet.MicrosoftGraphTokenService;
import com.capstone.gradify.Service.userservice.AuthService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.request.RegisterRequest;
import com.capstone.gradify.dto.response.AuthResult;
import com.capstone.gradify.dto.response.LoginResponse;
import com.capstone.gradify.dto.response.TokenResponse;
import com.capstone.gradify.dto.response.UserResponse;
import com.capstone.gradify.mapper.UserMapper;
import com.capstone.gradify.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.microsoft.graph.models.User;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final MicrosoftGraphTokenService microsoftGraphTokenService;
    private final UserMapper userMapper;
    private final TempTokensRepository tempTokensRepository;
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
            Optional<UserEntity> existingUserOpt = userService.findByAzureId(azureUser.getId());

            if (existingUserOpt.isPresent()) {
                UserEntity user = existingUserOpt.get();
                microsoftGraphTokenService.storeUserTokenDirect(
                        user.getUserId(),
                        tokenResponse.getAccessToken(),
                        tokenResponse.getRefreshToken(),
                        tokenResponse.getExpiresIn()
                );

                String jwtToken = jwtUtil.generateToken(user);

                String redirectUrl = String.format(
                        "http://localhost:5173/auth/azure/callback?onboardingRequired=false&token=%s&userId=%d&email=%s&firstName=%s&lastName=%s&role=%s&provider=Microsoft",
                        jwtToken,
                        user.getUserId(),
                        user.getEmail(),
                        Objects.requireNonNullElse(user.getFirstName(), ""),
                        Objects.requireNonNullElse(user.getLastName(), ""),
                        user.getRole() != null ? user.getRole().name() : "UNKNOWN"
                );
                response.sendRedirect(redirectUrl);
                return null;
            }
            TempTokens tempTokens = new TempTokens();
            tempTokens.setAzureId(azureUser.getId());
            tempTokens.setAccessToken(tokenResponse.getAccessToken());
            tempTokens.setRefreshToken(tokenResponse.getRefreshToken());
            tempTokens.setExpiresIn(tokenResponse.getExpiresIn());
            tempTokensRepository.save(tempTokens);

            String redirectUrl = String.format(
                    "http://localhost:5173/auth/azure/callback?onboardingRequired=true&azureId=%s&email=%s&firstName=%s&lastName=%s",
                    azureUser.getId(),
                    azureUser.getMail(),
                    Objects.requireNonNullElse(azureUser.getGivenName(), ""),
                    Objects.requireNonNullElse(azureUser.getSurname(), "")
            );
            response.sendRedirect(redirectUrl);
            return null;

        } catch (Exception e) {
            response.sendRedirect("http://localhost:5173/auth/azure/callback?error=auth_failed&message=" +
                    URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            return null;
        }
    }

    @PostMapping("/azure/finalize-teacher")
    public ResponseEntity<?> finalizeTeacherRegistration(
            @RequestBody RegisterRequest request) {

        try {
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            UserEntity user = userService.createTeacherFromOAuth(request);
            if (user == null) {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
            }
            // Retrieve the temporary tokens using the Azure ID
            TempTokens tempTokens = tempTokensRepository.findByAzureId(request.getAzureId()).orElseThrow(() -> new RuntimeException("No temporary tokens found for Azure ID: " + request.getAzureId()));

            // Store the tokens in the MicrosoftGraphTokenService
            microsoftGraphTokenService.storeUserTokenDirect(
                    user.getUserId(),
                    tempTokens.getAccessToken(),
                    tempTokens.getRefreshToken(),
                    tempTokens.getExpiresIn()
            );
            // Remove the temporary tokens from the repository
            tempTokensRepository.delete(tempTokens);

            String token = jwtUtil.generateToken(user);

            UserResponse userResponse = userMapper.toResponseDTO(user);
            LoginResponse response = new LoginResponse(userResponse, token);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to finalize registration", "message", e.getMessage()));
        }
    }

    @PostMapping("/azure/finalize-student")
    public ResponseEntity<?> finalizeStudentRegistration(
            @RequestBody RegisterRequest request) {

        try {
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            UserEntity user = userService.createStudentFromOAuth(request);
            if (user == null) {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
            }
            // Retrieve the temporary tokens using the Azure ID
            TempTokens tempTokens = tempTokensRepository.findByAzureId(request.getAzureId()).orElseThrow(() -> new RuntimeException("No temporary tokens found for Azure ID: " + request.getAzureId()));

            // Store the tokens in the MicrosoftGraphTokenService
            microsoftGraphTokenService.storeUserTokenDirect(
                    user.getUserId(),
                    tempTokens.getAccessToken(),
                    tempTokens.getRefreshToken(),
                    tempTokens.getExpiresIn()
            );
            // Remove the temporary tokens from the repository
            tempTokensRepository.delete(tempTokens);

            String token = jwtUtil.generateToken(user);

            UserResponse userResponse = userMapper.toResponseDTO(user);
            LoginResponse response = new LoginResponse(userResponse, token);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to finalize registration", "message", e.getMessage()));
        }
    }

    @PostMapping("/google/finalize/{role}")
    public ResponseEntity<?> finalizeGoogleRegistration(@PathVariable String role, @RequestBody RegisterRequest request) {
        try{
            logger.debug("Role: " + role);
            logger.debug("Role: " + request.getRole());
            if(role.equalsIgnoreCase("student") && request.getRole().equalsIgnoreCase("student")){
                if(request.getEmail() == null || request.getEmail().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
                }
                UserEntity user = userService.createStudentFromOAuth(request);
                if(user == null) {
                    return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
                }
                String token = jwtUtil.generateToken(user);
                UserResponse userResponse = userMapper.toResponseDTO(user);
                LoginResponse response = new LoginResponse(userResponse, token);
                return ResponseEntity.ok(response);
            } else if (role.equalsIgnoreCase("teacher") && request.getRole().equalsIgnoreCase("teacher")) {
                if(request.getEmail() == null || request.getEmail().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
                }
                UserEntity user = userService.createTeacherFromOAuth(request);
                if(user == null) {
                    return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
                }
                String token = jwtUtil.generateToken(user);
                UserResponse userResponse = userMapper.toResponseDTO(user);
                LoginResponse response = new LoginResponse(userResponse, token);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified"));
        }catch (Exception e){
            return ResponseEntity.status(500).body(Map.of("error", "Failed to finalize registration", "message", e.getMessage()));
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
