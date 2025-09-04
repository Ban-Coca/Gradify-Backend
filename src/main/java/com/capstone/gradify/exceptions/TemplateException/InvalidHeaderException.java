package com.capstone.gradify.exceptions.GradeValidation;

public class InvalidHeaderException extends ExcelTemplateException{
    public InvalidHeaderException(String expected, String actual, int column) {
        super(String.format("Invalid header at column %d. Expected: '%s', Found: '%s'",
                column, expected, actual));
    }
}
