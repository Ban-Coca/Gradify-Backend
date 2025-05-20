package com.capstone.gradify.Entity.user;

import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
public class StudentEntity extends UserEntity {
    private String studentNumber;
    private String major;
    private String yearLevel;
    private String institution;

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<GradeRecordsEntity> gradeRecords;

    @OneToMany(mappedBy = "student")
    @JsonManagedReference
    private List<ReportEntity> receivedReports = new ArrayList<>();

    @ManyToMany(mappedBy = "students")
    @JsonBackReference(value = "class-student")
    private Set<ClassEntity> classes = new HashSet<>();
}
