package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeVisibilityResponse {
    private Map<String, Integer> assessmentMaxValues;
    private Map<String, String> grades;
}
