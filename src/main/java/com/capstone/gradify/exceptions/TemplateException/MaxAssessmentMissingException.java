package com.capstone.gradify.exceptions.TemplateException;

public class MaxAssessmentMissingException extends ExcelTemplateException {
    public MaxAssessmentMissingException(String message) {
        super(String.format("Max values for assessment are missing: %s", message));
    }
}
