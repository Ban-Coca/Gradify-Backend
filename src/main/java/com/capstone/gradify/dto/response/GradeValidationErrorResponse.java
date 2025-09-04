package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeValidationErrorResponse {
    private boolean success;
    private String message;
    private List<GradeErrorDetail> errors;
}
