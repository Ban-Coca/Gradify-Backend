package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentResponse {
    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String major;
    private String yearLevel;
}
