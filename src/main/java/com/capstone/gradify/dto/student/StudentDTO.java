package com.capstone.gradify.dto.student;

import lombok.Data;

@Data
public class StudentDTO {
    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String major;
    private String yearLevel;
}
