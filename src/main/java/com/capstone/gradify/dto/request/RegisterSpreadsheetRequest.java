package com.capstone.gradify.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterSpreadsheetRequest {
    private Long spreadsheetId;
    private String itemId;
}
