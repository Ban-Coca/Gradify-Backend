package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassResponse {
    private int classId;
    private String className;
    private String semester;
    private String schoolYear;
    private String classCode;
    private String section;
    private String schedule;
    private String room;
    private List<StudentGradesResponse> students;
}
