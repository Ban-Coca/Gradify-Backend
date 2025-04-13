package com.capstone.gradify.Entity.records;

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

//    @ManyToOne
//    private Student student;

    @ManyToOne
    @JsonBackReference
    private ClassSpreadsheet classRecord;

//    @ElementCollection
//    @CollectionTable(name = "grade_values", joinColumns = @JoinColumn(name = "record_id"))
//    @MapKeyColumn(name = "header")
//    @Column(name = "value")
//    private Map<String, String> grades = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> grades;
}
