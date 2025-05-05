package com.capstone.gradify.Entity.records;

import com.capstone.gradify.Entity.user.StudentEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Setter
@Getter
public class GradeRecordsEntity {
    @Id
    @GeneratedValue
    private Long id;

    private String studentNumber;

    @ManyToOne(cascade = CascadeType.MERGE)
    private StudentEntity student;

    @ManyToOne
    @JsonBackReference
    private ClassSpreadsheet classRecord;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> grades;
}
