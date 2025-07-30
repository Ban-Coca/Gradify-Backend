package com.capstone.gradify.mapper;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.dto.response.SpreadsheetResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SpreadsheetMapper {

    SpreadsheetResponse toSpreadsheetResponse(ClassSpreadsheet spreadsheet);
    List<SpreadsheetResponse> toSpreadsheetResponseList(List<ClassSpreadsheet> spreadsheets);
}
