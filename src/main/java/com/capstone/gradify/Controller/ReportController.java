package com.capstone.gradify.Controller;

import com.capstone.gradify.Service.AiServices.GenerateFeedbackAIService;
import com.capstone.gradify.Service.notification.EmailService;
import com.capstone.gradify.Service.notification.NotificationService;
import com.capstone.gradify.Service.userservice.StudentService;
import com.capstone.gradify.dto.request.ReportRequest;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.capstone.gradify.Service.ReportService;
import com.capstone.gradify.dto.response.ReportResponse;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final GenerateFeedbackAIService generateFeedbackAIService;
    private final StudentService studentService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    /**
     * Create a new report
     * Only teachers should be able to create reports
     */
    @PostMapping
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<ReportResponse> createReport(@Valid @RequestBody ReportRequest reportRequest) throws MessagingException {
        String defaultURL = "http://localhost:5173/feedback";
        int studentUserId = reportRequest.getStudentId();
        String email = studentService.getEmailById(studentUserId);

        ReportResponse createdReport = reportService.createReport(reportRequest);

        // Send email notification to the student
        emailService.sendFeedbackNotification(email, createdReport.getSubject(), createdReport.getMessage(), createdReport.getClassName(), createdReport.getStudentName(), defaultURL, createdReport.getReportDate());
        // Send in-app notification to the student

        notificationService.sendNotification(reportService.mapToReportEntity(createdReport));

        return new ResponseEntity<>(createdReport, HttpStatus.CREATED);
    }

    /**
     * Get a specific report by ID
     * Teachers can see their own reports, students can see reports sent to them
     */
    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyAuthority('TEACHER', 'STUDENT')")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable int reportId) {
        // Note: Additional security checks should be implemented in a real app
        // to ensure users can only access reports they're allowed to see
        ReportResponse report = reportService.getReportById(reportId);
        return ResponseEntity.ok(report);
    }

    /**
     * Get all reports for a student
     * Teachers can see reports for their students, students can see their own reports
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('TEACHER', 'STUDENT')")
    public ResponseEntity<List<ReportResponse>> getReportsByStudentId(@PathVariable int studentId) {
        // Note: Additional security checks should be implemented in a real app
        List<ReportResponse> reports = reportService.getReportsByStudentId(studentId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get all reports created by a teacher
     * Only for teachers to see their own reports
     */
    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<ReportResponse>> getReportsByTeacherId(@PathVariable int teacherId) {
        // Note: Additional security checks should be implemented to ensure teacher can only see their own reports
        List<ReportResponse> reports = reportService.getReportsByTeacherId(teacherId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get all reports for a specific class
     * Only teachers of that class should be able to access this
     */
    @GetMapping("/class/{classId}")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<ReportResponse>> getReportsByClassId(@PathVariable int classId) {
        // Note: Additional security checks should be implemented to ensure teacher has access to this class
        List<ReportResponse> reports = reportService.getReportsByClassId(classId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Update an existing report
     * Only the teacher who created the report should be able to update it
     */
    @PutMapping("/{reportId}")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<ReportResponse> updateReport(
            @PathVariable int reportId,
            @Valid @RequestBody ReportRequest reportRequest) {
        // Note: Additional security checks should be implemented to ensure only the report creator can update it
        ReportResponse updatedReport = reportService.updateReport(reportId, reportRequest);
        return ResponseEntity.ok(updatedReport);
    }

    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<Void> deleteReport(@PathVariable int reportId) {
        // Note: Additional security checks should be implemented to ensure only the report creator can delete it
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generate an AI-suggested feedback report based on a student's grades
     * This is an advanced feature that would integrate with an AI service
     */
    @GetMapping("/generate-suggestion/student/{studentId}/class/{classId}")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<AIGeneratedReport> generateReportSuggestion(
            @PathVariable int studentId,
            @PathVariable int classId) {
        try {
            // Call the AI service to generate personalized feedback
            String aiGeneratedFeedback = generateFeedbackAIService.generateFeedbackAI(studentId, classId);

            // Create the report DTO with the AI-generated feedback
            AIGeneratedReport report = new AIGeneratedReport();
            report.setMessage(aiGeneratedFeedback);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            // Log the error
            System.err.println("Error generating AI feedback: " + e.getMessage());

            // Return a basic response with an error message
            AIGeneratedReport errorReport = new AIGeneratedReport();
            errorReport.setMessage("Unable to generate AI feedback at this time. Please try again later.");

            return ResponseEntity.ok(errorReport);
        }
    }

    // DTO FOR AI GENERATED REPORT
    @Data
    public static class AIGeneratedReport{
        private String message;
    }

}
