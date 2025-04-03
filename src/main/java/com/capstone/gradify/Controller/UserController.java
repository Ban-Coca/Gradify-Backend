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

    @GetMapping("/oauth2-success")
    public ResponseEntity<?> oauth2LoginSuccess() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Map<String, Object> userDetails = (Map<String, Object>) authentication.getPrincipal();

            String email = (String) userDetails.get("email");
            if (email == null || email.isEmpty()) {
                logger.warn("OAuth2 login failed: Email is missing");
                return ResponseEntity.badRequest().body("Email is required for OAuth2 login");
            }

            UserEntity user = userv.findByEmail(email);

            if (user == null) {
                // Create a new user
                user = new UserEntity();
                user.setEmail(email);
                user.setFirstName((String) userDetails.get("given_name"));
                user.setLastName((String) userDetails.get("family_name"));

                // Validate and assign role
                String role = (String) userDetails.getOrDefault("role", "STUDENT"); // Default to STUDENT if no role is provided
                List<String> allowedRoles = List.of("ADMIN", "TEACHER", "STUDENT");
                if (!allowedRoles.contains(role.toUpperCase())) {
                    logger.warn("Invalid role provided for OAuth2 user: {}", role);
                    return ResponseEntity.badRequest().body("Invalid role. Allowed roles are: ADMIN, TEACHER, STUDENT");
                }
                user.setRole(role.toUpperCase());

                // Set default values for other fields
                user.setCreatedAt(new Date());
                user.setLastLogin(new Date());
                user.setIsActive(true);
                user.setFailedLoginAttempts(0);

                // Save the user to the database
                userv.postUserRecord(user);
            }

            logger.info("OAuth2 login successful for user: {}", email);
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            logger.error("OAuth2 login error: ", e);
            return ResponseEntity.status(500).body("An unexpected error occurred during OAuth2 login");
        }
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

            // Find user by email
            UserEntity user = userv.findByEmail(email);
            if (user == null) {
                logger.warn("No user found with email: {}", email);
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid email or password"));
            }

            // Check password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("Invalid password for email: {}", email);
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid email or password"));
            }

            // Validate user role
            List<String> allowedRoles = List.of("ADMIN", "TEACHER", "STUDENT");
            if (user.getRole() == null || !allowedRoles.contains(user.getRole().toUpperCase())) {
                logger.warn("Unauthorized role for email: {}, role: {}", email, user.getRole());
                return ResponseEntity.status(403)
                        .body(Map.of("error", "Access denied: Unauthorized role"));
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
            if (user.getRole() == null || user.getRole().isEmpty()) {
                return ResponseEntity.badRequest().body("Role is required");
            }

            // Validate the role
            List<String> allowedRoles = List.of("ADMIN", "TEACHER", "STUDENT");
            String role = user.getRole().toUpperCase();
            if (!allowedRoles.contains(role)) {
                logger.warn("Invalid role provided: {}", role);
                return ResponseEntity.badRequest().body("Invalid role. Allowed roles are: ADMIN, TEACHER, STUDENT");
            }

            // Check if a user with the same email already exists
            UserEntity existingUser = userv.findByEmail(user.getEmail());
            if (existingUser != null) {
                logger.warn("User with email {} already exists", user.getEmail());
                return ResponseEntity.status(409).body("A user with this email already exists");
            }

            // Encrypt the password
            String encryptedPassword = passwordEncoder.encode(user.getPassword());
            user.setPassword(encryptedPassword);

            // Set default values for other fields
            user.setRole(role); // Assign the validated role
            user.setCreatedAt(new Date());
            user.setLastLogin(new Date());
            user.setIsActive(true);
            user.setFailedLoginAttempts(0);

            // Save the user to the database
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

    @GetMapping("/admin/dashboard")
    public ResponseEntity<String> adminDashboard() {
        return ResponseEntity.ok("Welcome to the Admin Dashboard");
    }

    @GetMapping("/teacher/dashboard")
    public ResponseEntity<String> teacherDashboard() {
        return ResponseEntity.ok("Welcome to the Teacher Dashboard");
    }

    @GetMapping("/student/dashboard")
    public ResponseEntity<String> studentDashboard() {
        return ResponseEntity.ok("Welcome to the Student Dashboard");
    }

    @GetMapping("/debug/roles")
    public ResponseEntity<?> debugRoles() {
        return ResponseEntity.ok(SecurityContextHolder.getContext().getAuthentication().getAuthorities());
    }

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
                .claim("role", user.getRole())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }  
}