package com.capstone.gradify.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating or updating a report
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {

    @NotNull(message = "Teacher ID is required")
    private Integer teacherId;

    @NotNull(message = "Student ID is required")
    private Integer studentId;

    // Class ID is optional as some reports might be general
    private Integer classId;

    // Grade record ID is optional as some reports might not relate to specific grades
    private Long gradeRecordId;

    @NotBlank(message = "Notification type is required")
    @Size(max = 50, message = "Notification type cannot exceed 50 characters")
    private String notificationType;

    @NotBlank(message = "Subject is required")
    @Size(max = 100, message = "Subject cannot exceed 100 characters")
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;
}
