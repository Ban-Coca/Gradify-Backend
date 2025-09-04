package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeErrorDetail {
    private String message;
    private int rowNumber;
    private String studentNumber;
    private String assessmentName;
    private double actualValue;
    private double maxValue;
}
