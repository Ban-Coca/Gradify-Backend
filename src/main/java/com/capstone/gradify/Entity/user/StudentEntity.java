package com.capstone.gradify.Entity.user;

import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
}
