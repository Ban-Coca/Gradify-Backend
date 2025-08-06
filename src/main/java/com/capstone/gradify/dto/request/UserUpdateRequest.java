package com.capstone.gradify.dto.request;

import com.capstone.gradify.Entity.user.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.N;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String password; // Optional, can be null if not updating password
    private Role role;
    private boolean isActive;

    /* Teacher-specific fields */
    private String institution;
    private String department;

    /* Student-specific fields */
    private String studentNumber;
    private String major;
    private String yearLevel;
//    private String profilePictureUrl; Optional, can be null if not updating profile picture
}
