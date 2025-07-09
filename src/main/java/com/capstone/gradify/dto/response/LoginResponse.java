package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.catalina.User;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private UserResponse userResponse;
    private String token;
}
