package com.capstone.gradify.Entity;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
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

    private String notificationType;
    private String message;
    private String subject;
    private String reportDate;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private TeacherEntity teacher;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    @ManyToOne
    @JoinColumn(name = "class_id")
    private ClassEntity relatedClass;

    @ManyToOne
    @JoinColumn(name = "grade_record_id")
    private GradeRecordsEntity gradeRecord;
}
