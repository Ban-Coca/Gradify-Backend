package com.capstone.gradify.Entity.user;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
public class StudentEntity extends UserEntity {
    private String studentNumber;
    private String major;
    private String yearLevel;
    private String institution;
}
