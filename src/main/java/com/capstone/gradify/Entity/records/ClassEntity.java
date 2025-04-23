package com.capstone.gradify.Entity.records;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ClassEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int classid;

    private String className;
    private String semester;
    private String schoolYear;
    private String classCode;
    private Date createdAt;
    private Date updatedAt;
    private String section;
    private String room;
    private String schedule;
}
