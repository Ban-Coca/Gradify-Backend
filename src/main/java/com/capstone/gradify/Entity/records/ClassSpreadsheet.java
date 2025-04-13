package com.capstone.gradify.Entity.records;

import com.capstone.gradify.Entity.user.TeacherEntity;
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
    private TeacherEntity uploadedBy;

    @OneToMany(mappedBy = "classRecord", cascade = CascadeType.ALL)
    private List<GradeRecordsEntity> gradeRecords = new ArrayList<>();

}
