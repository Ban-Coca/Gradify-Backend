package com.capstone.gradify.Entity.user;

import java.util.*;

import com.capstone.gradify.Entity.NotificationEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@ToString(exclude = "notifications")
@EqualsAndHashCode(exclude = "notifications")
@Table(name = "users")
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    private int userId;
    private String firstName;
    private String lastName;
    @Column(unique = true)
    private String email;
    private String password;
    @Column(unique = true)
    private String azureId;
    private boolean isActive;
    private String provider;
    private Date createdAt;
    private Date lastLogin;
    private int failedLoginAttempts;
    private String FCMToken;
    private String phoneNumber;
    private String bio;
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationEntity> notifications = new ArrayList<>();

    private transient Map<String, Object> attributes = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Role role;
    
    public UserEntity() {

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
    

    public boolean isActive() {
        return isActive;
    }
    public boolean hasRole(String role) {
        return this.role != null && this.role.name().equalsIgnoreCase(role);
    }
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

}
