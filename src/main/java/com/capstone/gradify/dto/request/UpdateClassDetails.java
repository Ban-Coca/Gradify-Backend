package com.capstone.gradify.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateClassDetails {
    private String className;
    private String semester;
    private String classCode;
    private String schoolYear;
    private String section;
    private String schedule;
    private String room;
}
