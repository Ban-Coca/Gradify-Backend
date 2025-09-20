package com.capstone.gradify.mapper;

import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.dto.response.ClassDetailResponse;
import com.capstone.gradify.dto.response.ClassResponse;
import com.capstone.gradify.dto.response.GradeRecordResponse;
import com.capstone.gradify.dto.response.StudentGradesResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClassMapper {
    @Mapping(target = "id", source = "id")
    @Mapping(target = "className", source = "className")
    @Mapping(target = "fileName", source = "fileName")
    @Mapping(target = "assessmentMaxValues", source = "assessmentMaxValues")
    @Mapping(target = "gradeRecords", source = "gradeRecords")
    ClassDetailResponse toClassDetailResponse(ClassSpreadsheet classSpreadsheet);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "studentNumber", source = "studentNumber")
    @Mapping(target = "grades", source = "grades")
    GradeRecordResponse toGradeRecordResponse(GradeRecordsEntity gradeRecord);

    @Mapping(target = "classId", source = "classId")
    @Mapping(target = "className", source = "className")
    @Mapping(target = "semester", source = "semester")
    @Mapping(target = "schoolYear", source = "schoolYear")
    @Mapping(target = "classCode", source = "classCode")
    @Mapping(target = "section", source = "section")
    @Mapping(target = "schedule", source = "schedule")
    @Mapping(target = "room", source = "room")
    @Mapping(target = "students", source = "students")
    ClassResponse toClassResponse(ClassEntity classEntity);

    List<ClassResponse> toClassResponseList(List<ClassEntity> classes);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "studentNumber", source = "studentNumber")
    @Mapping(target = "major", source = "major")
    @Mapping(target = "yearLevel", source = "yearLevel")
    @Mapping(target = "gradeRecords", source = "gradeRecords")
    StudentGradesResponse toStudentGradesResponse(StudentEntity student);

    // List mappings
    List<GradeRecordResponse> toGradeRecordResponseList(List<GradeRecordsEntity> gradeRecords);
}
