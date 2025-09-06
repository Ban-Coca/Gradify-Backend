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
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "class_spreadsheets")
public class ClassSpreadsheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String className;
    private String fileName;

    // Microsoft Graph item ID for the spreadsheet
    // This is used to identify the file in OneDrive or SharePoint
    // This is for the spreadsheet file itself, not the class record
    // this is for the microsoft subscription implementation
    // Could be null if the file is not stored in Microsoft Graph
    private String itemId;
    private String folderName;
    private String folderId;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<String> visibleAssessments;

    @OneToMany(mappedBy = "classRecord", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<GradeRecordsEntity> gradeRecords = new ArrayList<>();

}
