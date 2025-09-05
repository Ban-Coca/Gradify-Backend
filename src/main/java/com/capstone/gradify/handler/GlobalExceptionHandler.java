package com.capstone.gradify.handler;

import com.capstone.gradify.dto.response.ErrorResponse;
import com.capstone.gradify.dto.response.GradeErrorDetail;
import com.capstone.gradify.dto.response.GradeValidationErrorResponse;
import com.capstone.gradify.exceptions.GradeException.GradeExceedsMaximumException;
import com.capstone.gradify.exceptions.GradeException.GradeValidationException;
import com.capstone.gradify.exceptions.TemplateException.ExcelTemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(GradeValidationException.class)
    public ResponseEntity<ErrorResponse> handleGradeValidationException(GradeValidationException ex) {
        log.error("Grade validation failed: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(ex.getErrorCode());

        if (ex.getValidationErrors() != null && !ex.getValidationErrors().isEmpty()) {
            // Multiple validation errors
            errorResponse.setMessage(String.format("Grade validation failed with %d error(s)",
                    ex.getValidationErrors().size()));

            // Structure the error details better
            Map<String, Object> details = new HashMap<>();
            details.put("errorCount", ex.getValidationErrors().size());
            details.put("errors", ex.getValidationErrors().stream()
                    .map(error -> {
                        Map<String, Object> errorDetail = new HashMap<>();
                        errorDetail.put("row", error.getRowNumber());
                        errorDetail.put("studentNumber", error.getStudentNumber());
                        errorDetail.put("assessmentName", error.getAssessmentName());
                        errorDetail.put("message", error.getMessage());
                        errorDetail.put("actualValue", error.getActualValue());
                        errorDetail.put("maxValue", error.getMaxValue());
                        return errorDetail;
                    })
                    .collect(Collectors.toList()));

            errorResponse.setDetails(details);

        } else {
            // Single validation error
            errorResponse.setMessage(String.format("Row %d, Student %s: %s",
                    ex.getRow(), ex.getStudentId(), ex.getMessage()));

            Map<String, Object> details = new HashMap<>();
            details.put("errorCount", 1);
            details.put("row", ex.getRow());
            details.put("studentId", ex.getStudentId());
            errorResponse.setDetails(details);
        }

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
