package com.capstone.gradify.Entity.user;

import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import net.minidev.json.annotate.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
@Table(name = "teachers")
public class TeacherEntity extends UserEntity {
    private String institution;
    private String department;

    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<ClassSpreadsheet> classesRecord = new ArrayList<>();

    @OneToMany(mappedBy = "teacher")
    @JsonManagedReference(value = "teacher-class")
    private List<ClassEntity> classes = new ArrayList<>();

    @OneToMany(mappedBy = "teacher")
    @JsonManagedReference(value = "teacher-report")
    private List<ReportEntity> sentReports = new ArrayList<>();

    public TeacherEntity() {

    }
}
