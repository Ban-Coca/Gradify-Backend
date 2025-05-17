package com.capstone.gradify.Entity.user;

import com.capstone.gradify.Entity.ReportEntity;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.Setter;
import net.minidev.json.annotate.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
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
    private List<ReportEntity> sentReports = new ArrayList<>();

    public TeacherEntity() {

    }
}
