package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class SpreadsheetValidationResult {
    private final List<String> allHeaders;
    private final List<String> assessmentHeaders;
    private final Map<String, Integer> maxAssessmentValues;
}
