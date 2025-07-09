package com.capstone.gradify.dto.response;

import lombok.Data;

@Data
public class StudentResponse {
    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String major;
    private String yearLevel;
}
