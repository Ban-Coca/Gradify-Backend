package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentGradesResponse {
    private int userId;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String major;
    private String yearLevel;
    private List<GradeRecordResponse> gradeRecords;
}
