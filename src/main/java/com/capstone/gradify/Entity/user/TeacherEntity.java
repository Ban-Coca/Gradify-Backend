package com.capstone.gradify.Entity.user;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.Setter;

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
    private List<ClassSpreadsheet> classesRecord = new ArrayList<>();
    public TeacherEntity() {

    }
}
