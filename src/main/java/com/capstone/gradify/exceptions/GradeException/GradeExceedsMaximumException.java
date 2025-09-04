package com.capstone.gradify.exceptions.TemplateValidation;

import com.capstone.gradify.exceptions.BaseException;

public class GradeExceedsMaximumException extends GradeValidationException {
    public GradeExceedsMaximumException(double grade, double maxGrade, int row, String studentId) {
        super(String.format("Grade %.1f exceeds maximum of %.1f", grade, maxGrade), row, studentId);
    }
}
