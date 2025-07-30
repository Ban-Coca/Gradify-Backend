package com.capstone.gradify.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpreadsheetResponse {
    private int id;
    private String className;
    private String fileName;
    private String filePath;
    private Map<String, Integer> assessmentMaxValues;
    private Set<String> visibleAssessments;
    private List<GradeRecordResponse> gradeRecords;
}
