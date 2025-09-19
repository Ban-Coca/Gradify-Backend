package com.capstone.gradify.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private String phoneNumber;
    private String bio;
    private String profilePictureUrl;
    private String institution; // For Teacher
    private String department; // For Teacher
    private String studentNumber; // For Student
}
