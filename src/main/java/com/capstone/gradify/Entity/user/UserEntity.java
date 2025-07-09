package com.capstone.gradify.Entity.user;

import java.util.*;

import com.capstone.gradify.Entity.NotificationEntity;
import jakarta.persistence.*;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    private int userId;
    
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private boolean isActive;
    private String provider;
    private Date createdAt;
    private Date lastLogin;
    private int failedLoginAttempts;
    @Version
    private Long version;
    private String FCMToken;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationEntity> notifications = new ArrayList<>();

    private transient Map<String, Object> attributes = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private Role role;
    
    public UserEntity() {
        this.version = 0L; // Ensure version is always initialized
        this.attributes = new HashMap<>();
    }
    
    public UserEntity(int userId, String firstName, String lastName, String email, String password, boolean isActive,
            Date createdAt, Date lastLogin, int failedLoginAttempts, String role) {
        this();  // Call default constructor to initialize version
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.failedLoginAttempts = failedLoginAttempts;
        this.role = role != null ? Role.valueOf(role.toUpperCase()) : null;
    }
    
    // Getters and setters
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    public boolean isActive() {
        return isActive;
    }
    
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }
    public String getProvider() {
        return provider;
    }
    public void setProvider(String provider) {
        this.provider = provider;
    }
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    public Role getRole() {
        return role;
    }
    public void setRole(Role role) {
        this.role = role;
    }
    public boolean hasRole(String role) {
        return this.role != null && this.role.name().equalsIgnoreCase(role);
    }
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    public String getFCMToken() {
        return FCMToken;
    }
    public void setFCMToken(String FCMToken) {
        this.FCMToken = FCMToken;
    }
}
