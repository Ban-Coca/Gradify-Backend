package com.capstone.gradify.exceptions.GradeException;

import com.capstone.gradify.dto.response.GradeErrorDetail;
import com.capstone.gradify.exceptions.BaseException;
import lombok.Getter;

import java.util.List;

@Getter
public class GradeValidationException extends BaseException {
    private final int row;
    private final String studentId;
    private final List<GradeErrorDetail> validationErrors;

    public GradeValidationException(String message, int row, String studentId) {
        super("GRADE_VALIDATION_ERROR", message);
        this.row = row;
        this.studentId = studentId;
        this.validationErrors = null;
    }

    public GradeValidationException(String message, List<GradeErrorDetail> validationErrors) {
        super("GRADE_VALIDATION_ERROR", message);
        this.row = -1;
        this.studentId = null;
        this.validationErrors = validationErrors;
    }
}
