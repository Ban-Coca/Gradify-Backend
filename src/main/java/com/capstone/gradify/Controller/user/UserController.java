package com.capstone.gradify.Controller.user;

import java.util.*;
import java.io.IOException;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.VerificationCode;
import com.capstone.gradify.Service.EmailService;
import com.capstone.gradify.Service.VerificationCodeService;
import com.capstone.gradify.util.VerificationCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletResponse;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.userservice.UserService;


@RestController
@RequestMapping("api/user")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userv;

    @Autowired
    private VerificationCodeService codeService;

    @Autowired
    private EmailService emailService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${GOOGLE_CLIENT_ID}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET}")
    private String googleClientSecret;

    @Value("${MICROSOFT_CLIENT_ID}")
    private String microsoftClientId;

    @Value("${MICROSOFT_CLIENT_SECRET}")
    private String microsoftClientSecret;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${microsoft.redirect-uri}")
    private String microsoftRedirectUri;

    @GetMapping("/print")
    public String print() {
        return "Hello, User";
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("API is running on port 8080.");
    }

    // Helper method to serialize UserEntity to a JSON string
    private String serializeUser(UserEntity user) {
        return String.format("{\"userId\":%d,\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"%s\"}",
                user.getUserId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole().name());
    }

    @GetMapping("/oauth2/authorize/google")
    public void redirectToGoogle(HttpServletResponse response) throws IOException {
        String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + googleClientId
            + "&redirect_uri=" + googleRedirectUri
            + "&response_type=code"
            + "&scope=openid%20email%20profile";
        response.sendRedirect(googleAuthUrl);
    }

    @GetMapping("/oauth2/authorize/microsoft")
    public void redirectToMicrosoft(HttpServletResponse response) throws IOException {
        String microsoftAuthUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
            + "?client_id=" + microsoftClientId
            + "&redirect_uri=" + microsoftRedirectUri
            + "&response_type=code"
            + "&scope=openid%20email%20profile";
        response.sendRedirect(microsoftAuthUrl);
    }

    @GetMapping("/oauth2/success")
    public void oauth2LoginSuccess(@AuthenticationPrincipal OAuth2User principal, HttpServletResponse response) {
        try {
            logger.info("OAuth2 login successful for user: {}", (String) principal.getAttribute("email"));
    
            // Extract user details from OAuth2User
            String email = principal.getAttribute("email");
            String firstName = principal.getAttribute("given_name");
            String lastName = principal.getAttribute("family_name");
    
            if (email == null || firstName == null || lastName == null) {
                logger.error("Missing required attributes from OAuth2 provider");
                response.sendRedirect("http://localhost:5173/login-failure?error=InvalidOAuth2Response");
                return;
            }
    
            // Check if the user exists in the database
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                // Register the user if they don't exist
                user = new UserEntity();
                user.setEmail(email);
                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setRole(Role.STUDENT); // Default role
                user.setIsActive(true);
                user.setCreatedAt(new Date());
                user.setLastLogin(new Date());
                user = userv.postUserRecord(user);
            } else {
                // Update last login time for existing user
                user.setLastLogin(new Date());
                userv.postUserRecord(user);
            }
    
            // Generate JWT token
            String token = generateToken(user);
    
            // Redirect to the frontend with token and user details
            String frontendRedirectUrl = "http://localhost:5173/oauth2/callback";
            response.sendRedirect(frontendRedirectUrl + "?token=" + token + "&user=" + serializeUser(user));
    
        } catch (Exception e) {
            logger.error("Error during OAuth2 login: ", e);
            try {
                response.sendRedirect("http://localhost:5173/login-failure?error=OAuth2LoginFailed");
            } catch (IOException ioException) {
                logger.error("Error redirecting to frontend: ", ioException);
            }
        }
    }

    @GetMapping("/oauth2/failure")
    public ResponseEntity<?> oauth2LoginFailure() {
        logger.warn("OAuth2 login failed");
        return ResponseEntity.status(401).body(Map.of("error", "OAuth2 login failed. Please try again."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        try {
            logger.info("Login request received: {}", loginRequest);

            String email = loginRequest.get("email");
            String password = loginRequest.get("password");

            if (email == null || password == null) {
                logger.warn("Email or password is null: email={}, password={}", email, password);
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password cannot be null"));
            }

            logger.info("Login attempt for email: {}", email);

            // Find user by email
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                logger.warn("No user found with email: {}", email);
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid email or password"));
            }

            logger.info("User found: {}", user);

            // Check password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("Invalid password for email: {}", email);
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid email or password"));
            }

            // Generate JWT token
            String token = generateToken(user);
            logger.info("Successfully generated token for user: {}", email);

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", getUserResponseMap(user));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Login error: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/postuserrecord")
    public ResponseEntity<?> postUserRecord(@RequestBody UserEntity user) {
        try {
            logger.info("Received registration request for email: {}", user.getEmail());

            // Validate required fields
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }

            // Encrypt the password
            String encryptedPassword = passwordEncoder.encode(user.getPassword());
            user.setPassword(encryptedPassword);

            // Set default values for other fields
            user.setCreatedAt(new Date());
            user.setLastLogin(new Date());
            user.setIsActive(true);
            user.setFailedLoginAttempts(0);
            user.setRole(user.getRole() != null ? user.getRole() : Role.STUDENT);

            UserEntity savedUser = userv.postUserRecord(user);

            // Generate a JWT token for the user
            String token = generateToken(savedUser);

            // Don't send the encrypted password back in the response
            savedUser.setPassword(null);

            // Include the token in the response
            Map<String, Object> response = new HashMap<>();
            response.put("user", savedUser);
            response.put("token", token);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating user: ", e);
            return ResponseEntity.badRequest().body("Error creating user: " + e.getMessage());
        }
    }
    
    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserEntity updatedUserDetails, @RequestParam("userId") int userId) {
        try {
            logger.info("Received profile update request for user: {}", userId);

            // Validate user
            UserEntity user = userv.findById(userId);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            // Update user details
            user.setFirstName(updatedUserDetails.getFirstName());
            user.setLastName(updatedUserDetails.getLastName());
            user.setRole(updatedUserDetails.getRole());
            user.setIsActive(updatedUserDetails.IsActive());

            // Save updated user
            UserEntity updatedUser = userv.postUserRecord(user);

            return ResponseEntity.ok(getUserResponseMap(updatedUser));

        } catch (Exception e) {
            logger.error("Error updating profile: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }

    @GetMapping("/getallusers")
    public List<UserEntity> getAllUsers() {
        return userv.getAllUsers();
    }

    @DeleteMapping("/deleteuserdetails/{userId}")
    public String deleteUser(@PathVariable int userId) {
        return userv.deleteUser(userId);
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            // Find user by email
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                // For security reasons, don't reveal if email exists or not
                return ResponseEntity.ok(Map.of("message", "If your email exists in our system, you will receive a reset code"));
            }

            // Generate a random 6-digit verification code
            String verificationCode = VerificationCodeGenerator.generateVerificationCode();

            // Save the verification code
            codeService.createVerificationCode(user, verificationCode);

            // Send email with verification code
            emailService.sendVerificationEmail(email, verificationCode);

            return ResponseEntity.ok(Map.of("message", "If your email exists in our system, you will receive a reset code"));

        } catch (Exception e) {
            logger.error("Error in password reset request: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error processing request: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            if (email == null || email.isEmpty() || code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and verification code are required"));
            }

            // Find user by email
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Invalid or expired verification code"));
            }

            // Find verification code for user
            Optional<VerificationCode> verificationCodeOpt = codeService.findByUser(user);

            if (verificationCodeOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Invalid or expired verification code"));
            }

            VerificationCode verificationCode = verificationCodeOpt.get();

            // Check if code matches and is not expired
            if (!verificationCode.getCode().equals(code) || !codeService.isCodeValid(verificationCode)) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired verification code"));
            }

            // Generate a temporary token for secure password reset
            String resetToken = UUID.randomUUID().toString();
            // Store the reset token (you might want to add this field to your VerificationCode entity)
            verificationCode.setResetToken(resetToken);
            codeService.save(verificationCode);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful",
                    "resetToken", resetToken
            ));

        } catch (Exception e) {
            logger.error("Error in code verification: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error verifying code: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String resetToken = request.get("resetToken");
            String newPassword = request.get("newPassword");

            logger.info("Password reset execution for email: {}", email);

            if (email == null || email.isEmpty() || resetToken == null || resetToken.isEmpty() ||
                    newPassword == null || newPassword.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email, reset token, and new password are required"));
            }

            // Find user by email
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Invalid request"));
            }

            // Verify reset token
            Optional<VerificationCode> verificationCodeOpt = codeService.findByUser(user);

            if (verificationCodeOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Invalid request"));
            }

            VerificationCode verificationCode = verificationCodeOpt.get();

            // Check if token matches and is not expired
            if (!verificationCode.getResetToken().equals(resetToken) || !codeService.isCodeValid(verificationCode)) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired reset token"));
            }

            // Encrypt and update password
            String encryptedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encryptedPassword);
            user.setFailedLoginAttempts(0); // Reset failed login attempts

            userv.postUserRecord(user);

            // Delete the verification code record to prevent reuse
            codeService.deleteByUser(user);

            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            logger.error("Error in password reset: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error in password reset: " + e.getMessage()));
        }
    }
    

    // @GetMapping("/admin/dashboard")
    // public ResponseEntity<String> adminDashboard() {
    //     return ResponseEntity.ok("Welcome to the Admin Dashboard");
    // }

    // @GetMapping("/teacher/dashboard")
    // public ResponseEntity<String> teacherDashboard() {
    //     return ResponseEntity.ok("Welcome to the Teacher Dashboard");
    // }

    // @GetMapping("/student/dashboard")
    // public ResponseEntity<String> studentDashboard() {
    //     return ResponseEntity.ok("Welcome to the Student Dashboard");
    // }

    // @GetMapping("/debug/roles")
    // public ResponseEntity<?> debugRoles() {
    //     return ResponseEntity.ok(SecurityContextHolder.getContext().getAuthentication().getAuthorities());
    // }

    private Map<String, Object> getUserResponseMap(UserEntity user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("userId", user.getUserId());
        userMap.put("email", user.getEmail());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("role", user.getRole().name());
        userMap.put("isActive", user.IsActive());
        userMap.put("createdAt", user.getCreatedAt());
        userMap.put("lastLogin", user.getLastLogin());
        return userMap;
    }

    private String generateToken(UserEntity user) {
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }  
}