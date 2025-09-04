package com.capstone.gradify.handler;

import com.capstone.gradify.dto.response.ErrorResponse;
import com.capstone.gradify.dto.response.GradeErrorDetail;
import com.capstone.gradify.dto.response.GradeValidationErrorResponse;
import com.capstone.gradify.exceptions.GradeException.GradeExceedsMaximumException;
import com.capstone.gradify.exceptions.GradeException.GradeValidationException;
import com.capstone.gradify.exceptions.TemplateException.ExcelTemplateException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GradeValidationException.class)
    public ResponseEntity<GradeValidationErrorResponse> handleGradeValidationException(GradeValidationException ex) {
        GradeValidationErrorResponse errorResponse = new GradeValidationErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setMessage("Grade validation failed");
        errorResponse.setErrors(ex.getValidationErrors().stream()
                .map(error -> new GradeErrorDetail(
                        error.getMessage(),
                        error.getRowNumber(),
                        error.getStudentNumber(),
                        error.getAssessmentName(),
                        error.getActualValue(),
                        error.getMaxValue()
                ))
                .collect(Collectors.toList()));
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(GradeExceedsMaximumException.class)
    public ResponseEntity<GradeValidationErrorResponse> handleGradeExceedsMaximumException(GradeExceedsMaximumException ex) {
        GradeValidationErrorResponse response = new GradeValidationErrorResponse();
        response.setSuccess(false);
        response.setMessage("Grade exceeds maximum allowed value");

        GradeErrorDetail error = new GradeErrorDetail();
        error.setMessage(ex.getMessage());
        // Add other relevant details from the exception

        response.setErrors(Collections.singletonList(error));

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ExcelTemplateException.class)
    public ResponseEntity<?> handleTemplateException(ExcelTemplateException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred: " + ex.getMessage()
        );
        return ResponseEntity.internalServerError().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                ex.getMessage()
        );
        return ResponseEntity.badRequest().body(errorResponse);
    }
}
