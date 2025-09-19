package com.capstone.gradify.dto.response;

import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.report.ReportEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentDetails {
    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String major;
    private String yearLevel;

    private List<GradeRecordsEntity> grades;
    private List<ReportEntity> reports;
}
