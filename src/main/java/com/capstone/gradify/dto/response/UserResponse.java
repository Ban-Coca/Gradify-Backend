package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String firstName;
    private String lastName;
    private Date createdAt;
    private Date lastLogin;
    private String role;
    private String provider;
    private boolean isActive;
    private int userId;
    private String email;
}
