package com.capstone.gradify.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDTO {

    private int reportId;
    private String notificationType;
    private String subject;
    private String message;
    private String reportDate;

    // Teacher information
    private int teacherId;
    private String teacherName;

    // Student information
    private int studentId;
    private String studentName;
    private String studentNumber;

    // Class information
    private Integer classId;
    private String className;

    // Grade record ID if applicable
    private Long gradeRecordId;
}

