package com.capstone.gradify.Entity.records;

import com.capstone.gradify.Entity.user.TeacherEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> assessmentMaxValues;

    @OneToMany(mappedBy = "classRecord", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<GradeRecordsEntity> gradeRecords = new ArrayList<>();

}
