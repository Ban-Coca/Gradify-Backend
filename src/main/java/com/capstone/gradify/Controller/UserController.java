package com.capstone.gradify.Controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import com.capstone.gradify.Entity.UserEntity;
import com.capstone.gradify.Service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("api/user")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userv;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @GetMapping("/print")
    public String print() {
        return "Hello, User";
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("API is running on port 8080.");
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
            user.setRole(user.getRole() != null ? user.getRole() : "USER");

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

    @PostMapping("/forget1")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            UserEntity user = userv.findByEmail(email);

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));

        } catch (Exception e) {
            logger.error("Error in password reset: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error in password reset: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot2")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String newPassword = request.get("NewPassword");

            logger.info("Password reset request for email: {}", email);

            if (email == null || email.isEmpty() || newPassword == null || newPassword.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and new password are required"));
            }

            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            String encryptedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encryptedPassword);

            userv.postUserRecord(user);
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
        userMap.put("role", user.getRole());
        userMap.put("isActive", user.IsActive());
        userMap.put("createdAt", user.getCreatedAt());
        userMap.put("lastLogin", user.getLastLogin());
        return userMap;
    }

    private String generateToken(UserEntity user) {
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }  
}