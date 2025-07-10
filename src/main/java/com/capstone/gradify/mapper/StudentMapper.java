package com.capstone.gradify.mapper;

import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.dto.response.StudentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.*;

@Mapper(componentModel = "spring")
public interface StudentMapper {
    StudentMapper INSTANCE = Mappers.getMapper(StudentMapper.class);

    StudentResponse toDto(StudentEntity student);

    List<StudentResponse> toDtoList(List<StudentEntity> students);

    List<StudentResponse> toDtoList(Set<StudentEntity> students);

    StudentEntity toEntity(StudentResponse studentResponse);
}
