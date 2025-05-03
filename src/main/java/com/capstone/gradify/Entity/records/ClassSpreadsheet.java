package com.capstone.gradify.Entity.records;

import com.capstone.gradify.Entity.user.TeacherEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class ClassSpreadsheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String className;
    private String fileName;

    @ManyToOne
    @JoinColumn(name = "userId")
    @JsonBackReference
    private TeacherEntity uploadedBy;

    @ManyToOne
    @JoinColumn(name = "classId")
    private ClassEntity classEntity;

    @OneToMany(mappedBy = "classRecord", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<GradeRecordsEntity> gradeRecords = new ArrayList<>();

}
