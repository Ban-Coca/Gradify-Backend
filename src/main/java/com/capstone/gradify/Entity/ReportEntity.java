package com.capstone.gradify.Entity;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int reportId;

    @Column(columnDefinition = "TEXT")
    private String notificationType;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(columnDefinition = "TEXT")
    private String subject;
    @Column(columnDefinition = "TEXT")
    private String reportDate;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    @JsonBackReference(value = "teacher-report")
    private TeacherEntity teacher;

    @ManyToOne
    @JoinColumn(name = "student_id")
    @JsonBackReference
    private StudentEntity student;

    @ManyToOne
    @JoinColumn(name = "class_id")
    @JsonBackReference(value = "class-report")
    private ClassEntity relatedClass;

    @ManyToOne
    @JoinColumn(name = "grade_record_id")
    private GradeRecordsEntity gradeRecord;
}
