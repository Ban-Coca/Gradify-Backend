package com.capstone.gradify.Service;

import com.capstone.gradify.Service.userservice.StudentService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.ReportRepository;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.capstone.gradify.Repository.records.GradeRecordRepository;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.dto.report.ReportDTO;
import com.capstone.gradify.dto.report.ReportResponseDTO;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GradeRecordRepository gradeRecordsRepository;

    @Autowired
    private StudentService studentService;

    /**
     * Create a new report from a teacher to a student
     */
    @Transactional
    public ReportResponseDTO createReport(ReportDTO reportDTO) {
        // Find the teacher
        TeacherEntity teacher = teacherRepository.findById(reportDTO.getTeacherId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found with id: " + reportDTO.getTeacherId()));

        // Find the student
        StudentEntity student = studentRepository.findById(reportDTO.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found with id: " + reportDTO.getStudentId()));

        // Find the class if provided
        ClassEntity classEntity = null;
        if (reportDTO.getClassId() != null) {
            classEntity = classRepository.findById(reportDTO.getClassId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found with id: " + reportDTO.getClassId()));
        }

        // Find the grade record if provided
        GradeRecordsEntity gradeRecord = null;
        if (reportDTO.getGradeRecordId() != null) {
            gradeRecord = gradeRecordsRepository.findById(Math.toIntExact(reportDTO.getGradeRecordId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade record not found with id: " + reportDTO.getGradeRecordId()));
        }

        // Create new report entity
        ReportEntity report = new ReportEntity();
        report.setTeacher(teacher);
        report.setStudent(student);
        report.setRelatedClass(classEntity);
        report.setGradeRecord(gradeRecord);
        report.setNotificationType(reportDTO.getNotificationType());
        report.setSubject(reportDTO.getSubject());
        report.setMessage(reportDTO.getMessage());

        // Set report date to current timestamp
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        report.setReportDate(LocalDateTime.now().format(formatter));

        // Save the report
        ReportEntity savedReport = reportRepository.save(report);

        // Convert to response DTO
        return convertToResponseDTO(savedReport);
    }

    /**
     * Get all reports for a specific student
     */
    public List<ReportResponseDTO> getReportsByStudentId(int studentId) {
        // Verify student exists
        studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found with id: " + studentId));

        List<ReportEntity> reports = reportRepository.findByStudentUserId(studentId);
        return reports.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all reports created by a specific teacher
     */
    public List<ReportResponseDTO> getReportsByTeacherId(int teacherId) {
        // Verify teacher exists
        teacherRepository.findById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found with id: " + teacherId));

        List<ReportEntity> reports = reportRepository.findByTeacherUserId(teacherId);
        return reports.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all reports for a specific class
     */
    public List<ReportResponseDTO> getReportsByClassId(int classId) {
        // Verify class exists
        classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found with id: " + classId));

        List<ReportEntity> reports = reportRepository.findByRelatedClassClassId(classId);
        return reports.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific report by ID
     */
    public ReportResponseDTO getReportById(int reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + reportId));

        return convertToResponseDTO(report);
    }

    /**
     * Delete a report
     */
    @Transactional
    public void deleteReport(int reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + reportId);
        }
        reportRepository.deleteById(reportId);
    }

    /**
     * Update an existing report
     */
    @Transactional
    public ReportResponseDTO updateReport(int reportId, ReportDTO reportDTO) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found with id: " + reportId));

        // Update only non-null fields
        if (reportDTO.getNotificationType() != null) {
            report.setNotificationType(reportDTO.getNotificationType());
        }

        if (reportDTO.getSubject() != null) {
            report.setSubject(reportDTO.getSubject());
        }

        if (reportDTO.getMessage() != null) {
            report.setMessage(reportDTO.getMessage());
        }

        // Update class reference if provided
        if (reportDTO.getClassId() != null) {
            ClassEntity classEntity = classRepository.findById(reportDTO.getClassId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found with id: " + reportDTO.getClassId()));
            report.setRelatedClass(classEntity);
        }

        // Update grade record reference if provided
        if (reportDTO.getGradeRecordId() != null) {
            GradeRecordsEntity gradeRecord = gradeRecordsRepository.findById(Math.toIntExact(reportDTO.getGradeRecordId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade record not found with id: " + reportDTO.getGradeRecordId()));
            report.setGradeRecord(gradeRecord);
        }

        ReportEntity updatedReport = reportRepository.save(report);
        return convertToResponseDTO(updatedReport);
    }

    /**
     * Helper method to convert Report entity to DTO
     */
    private ReportResponseDTO convertToResponseDTO(ReportEntity report) {
        ReportResponseDTO dto = new ReportResponseDTO();
        dto.setReportId(report.getReportId());
        dto.setNotificationType(report.getNotificationType());
        dto.setSubject(report.getSubject());
        dto.setMessage(report.getMessage());
        dto.setReportDate(report.getReportDate());

        // Include related entity IDs
        if (report.getTeacher() != null) {
            dto.setTeacherId(report.getTeacher().getUserId());
            dto.setTeacherName(report.getTeacher().getFirstName() + " " + report.getTeacher().getLastName());
        }

        if (report.getStudent() != null) {
            dto.setStudentId(report.getStudent().getUserId());
            dto.setStudentName(report.getStudent().getFirstName() + " " + report.getStudent().getLastName());
            dto.setStudentNumber(report.getStudent().getStudentNumber());
        }

        if (report.getRelatedClass() != null) {
            dto.setClassId(report.getRelatedClass().getClassId());
            dto.setClassName(report.getRelatedClass().getClassName());
        }

        if (report.getGradeRecord() != null) {
            dto.setGradeRecordId(report.getGradeRecord().getId());
        }

        return dto;
    }
    public ReportEntity mapToReportEntity(ReportResponseDTO reportResponseDTO) {
        ReportEntity report = new ReportEntity();
        report.setReportId(reportResponseDTO.getReportId());
        report.setStudent(studentService.findByUserId(reportResponseDTO.getStudentId()));
        report.setSubject(reportResponseDTO.getSubject());
        report.setMessage(reportResponseDTO.getMessage());
        report.setNotificationType("REPORT_CREATED");
        return report;
    }
}

