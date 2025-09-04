package com.capstone.gradify.exceptions.GradeException;

public class GradeExceedsMaximumException extends GradeValidationException {
    public GradeExceedsMaximumException(double grade, double maxGrade, int row, String studentId) {
        super(String.format("Grade %.1f exceeds maximum of %.1f", grade, maxGrade), row, studentId);
    }
}
