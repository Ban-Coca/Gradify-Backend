package com.capstone.gradify.Entity.records;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
    private String schedule;
    private String room;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    @JsonBackReference(value = "teacher-class")
    private TeacherEntity teacher;

    @ManyToMany
    @JoinTable(
            name = "class_student",
            joinColumns = @JoinColumn(name = "class_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    @JsonManagedReference(value = "class-student")
    private Set<StudentEntity> students = new HashSet<>();
}
