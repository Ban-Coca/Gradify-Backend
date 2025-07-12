package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeacherAssessmentPerformance {
    private String assessmentType;
    private double overallAverage;
    private double topQuartileAverage;
    private double bottomQuartileAverage;
    private int totalStudents;
}
