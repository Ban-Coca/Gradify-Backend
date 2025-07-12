package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentTableData {
    private String studentName;
    private String studentNumber;
    private String grade;
    private double percentage;
    private String status;
    private int userId;
}
