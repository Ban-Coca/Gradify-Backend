package com.capstone.gradify.exceptions.TemplateValidation;

import com.capstone.gradify.exceptions.BaseException;

public class GradeValidationException extends BaseException {
    private final int row;
    private final String studentId;

    public GradeValidationException(String message, int row, String studentId) {
        super("GRADE_VALIDATION_ERROR", message);
        this.row = row;
        this.studentId = studentId;
    }
}
