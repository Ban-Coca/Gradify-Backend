package com.capstone.gradify.Entity.records;

import java.util.Date;

import com.capstone.gradify.Entity.user.TeacherEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ClassEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int classId;
    private String className;
    private String semester;
    private String schoolYear;
    private String classCode;
    private Date createdAt;
    private Date updatedAt;
    private String section;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    @JsonBackReference(value = "teacher-class")
    private TeacherEntity teacher;

}
