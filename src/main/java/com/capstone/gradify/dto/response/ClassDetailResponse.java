package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassDetailResponse {
    private int id;
    private String className;
    private String fileName;
    private Map<String, Integer> assessmentMaxValues;
    private List<GradeRecordResponse> gradeRecords;
}
