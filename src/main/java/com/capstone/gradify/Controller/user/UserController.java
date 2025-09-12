package com.capstone.gradify.Controller.user;

import java.util.*;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Entity.user.VerificationCode;
import com.capstone.gradify.Service.notification.EmailService;
import com.capstone.gradify.Service.userservice.VerificationCodeService;
import com.capstone.gradify.dto.request.UserUpdateRequest;
import com.capstone.gradify.dto.response.LoginResponse;
import com.capstone.gradify.dto.response.UserResponse;
import com.capstone.gradify.mapper.UserMapper;
import com.capstone.gradify.util.VerificationCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.capstone.gradify.util.JwtUtil;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.dto.request.LoginRequest;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userv;
    private final VerificationCodeService codeService;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @GetMapping("/oauth2/failure")
    public ResponseEntity<?> oauth2LoginFailure() {
        logger.warn("OAuth2 login failed");
        return ResponseEntity.status(401).body(Map.of("error", "OAuth2 login failed. Please try again."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            logger.info("Login request received: {}", loginRequest);
            String email = loginRequest.getEmail();
            String password = loginRequest.getPassword();

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
            String token = jwtUtil.generateToken(user);
            logger.info("Successfully generated token for user: {}", email);
            UserResponse userDTO = userMapper.toResponseDTO(user);
            LoginResponse loginResponse = new LoginResponse(userDTO, token);

            return ResponseEntity.ok(loginResponse);

        } catch (Exception e) {
            logger.error("Login error: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> postUserRecord(@RequestBody Map<String, Object> userMap) {
        try {
            String roleStr = (String) userMap.get("role");
            Role role = Role.valueOf(roleStr);

            UserEntity user;

            if (role == Role.TEACHER) {
                TeacherEntity teacher = new TeacherEntity();
                teacher.setInstitution((String) userMap.get("institution"));
                teacher.setDepartment((String) userMap.get("department"));
                user = teacher;
                logger.debug("Creating a new teacher entity: {}, {}", teacher.getDepartment(), teacher.getInstitution());
            }else if (role == Role.STUDENT) {
                StudentEntity student = new StudentEntity();
                student.setStudentNumber((String) userMap.get("studentNumber"));
                student.setMajor((String) userMap.get("major"));
                student.setYearLevel((String) userMap.get("yearLevel"));
                student.setInstitution((String) userMap.get("institution"));
                user = student;
                logger.debug("Creating a new student entity: {}, {}, {}", student.getStudentNumber(), student.getMajor(), student.getYearLevel());
            } 
            else {
                user = new UserEntity();
            }

            user.setFirstName((String) userMap.get("firstName"));
            user.setLastName((String) userMap.get("lastName"));
            user.setEmail((String) userMap.get("email"));
            user.setPassword((String) userMap.get("password"));
            user.setProvider((String) userMap.get("provider"));
            user.setRole(role);

            logger.info("Received registration request for email: {}", user.getEmail());
            logger.info("Received registration request for user: {}", user);
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }

            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                String encryptedPassword = passwordEncoder.encode(user.getPassword());
                user.setPassword(encryptedPassword);
            }
            user.setCreatedAt(new Date());
            user.setLastLogin(new Date());
            user.setActive(true);
            user.setProvider(user.getProvider() != null ? user.getProvider() : "Email");
            user.setFailedLoginAttempts(0);
            user.setRole(user.getRole() != null ? user.getRole() : Role.PENDING);

            UserEntity savedUser = userv.postUserRecord(user);

            String token = jwtUtil.generateToken(savedUser);
            savedUser.setPassword(null);

            UserResponse userDTO = userMapper.toResponseDTO(savedUser);
            LoginResponse loginResponse = new LoginResponse(userDTO, token);
            return ResponseEntity.ok(loginResponse);

        } catch (Exception e) {
            logger.error("Error creating user: ", e);
            return ResponseEntity.badRequest().body("Error creating user: " + e.getMessage());
        }
    }
    
    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserUpdateRequest userUpdateRequest, @RequestParam("userId") int userId) {
        try {
            logger.info("Received profile update request for user: {}", userId);

            // Validate user
            UserEntity user = userv.findById(userId);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            // Update user details
            user.setFirstName(userUpdateRequest.getFirstName());
            user.setLastName(userUpdateRequest.getLastName());
            user.setRole(userUpdateRequest.getRole());
            user.setActive(userUpdateRequest.isActive());

            // Save updated user
            UserEntity updatedUser = userv.postUserRecord(user);
            UserResponse userDTO = userMapper.toResponseDTO(updatedUser);

            return ResponseEntity.ok(userDTO);

        } catch (Exception e) {
            logger.error("Error updating profile: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public List<UserEntity> getAllUsers() {
        return userv.getAllUsers();
    }

    @DeleteMapping("/{userId}")
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
            user.setFailedLoginAttempts(0);

            userv.postUserRecord(user);

            // Delete the verification code record to prevent reuse
            codeService.deleteByUser(user);

            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            logger.error("Error in password reset: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error in password reset: " + e.getMessage()));
        }
    }

    @PutMapping("/update-role/{userId}")
    public ResponseEntity<?> updateRole(@PathVariable int userId, @RequestParam String role) {
        try {
            // Validate user
            UserEntity user = userv.findById(userId);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            Role newRole;
            try {
                newRole = Role.valueOf(role);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role value"));
            }

            // Update user role
            user.setRole(newRole);
            userv.changeUserRole(userId, user.getRole());

            UserEntity updatedUser = userv.findById(userId);
            UserResponse userResponse = userMapper.toResponseDTO(user);

            String token = jwtUtil.generateToken(updatedUser);

            return ResponseEntity.ok(new LoginResponse(userResponse, token));

        } catch (Exception e) {
            logger.error("Error updating role: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Role update failed: " + e.getMessage()));
        }
    }
    @PutMapping(value = "/update-user/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUser(@PathVariable int userId, @ModelAttribute UserUpdateRequest request){
        try {
            UserEntity updateUser = userv.putUserDetails(userId, request);
            if (updateUser == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            UserResponse userResponse = userMapper.toResponseDTO(updateUser);
            String token = jwtUtil.generateToken(updateUser);
            return ResponseEntity.ok(new LoginResponse(userResponse, token));
        } catch (Exception e){
            logger.error("Error updating user: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "User update failed"));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserDetails(@PathVariable int userId) {
        try {
            UserEntity user = userv.findById(userId);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            UserResponse response = userMapper.toResponseDTO(user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching user details: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error fetching user details: " + e.getMessage()));
        }
    }

    @GetMapping("/email-exists")
    public ResponseEntity<?> checkEmailExists(@RequestParam String email){
        try {
            boolean exists = userv.isEmailAvailable(email);
            return ResponseEntity.ok(Map.of("exists", exists));
        } catch (Exception e) {
            logger.error("Error checking email existence: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error checking email existence: " + e.getMessage()));
        }
    }
}