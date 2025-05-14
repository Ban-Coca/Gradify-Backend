package com.capstone.gradify.Service.spreadsheet;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface CloudSpreadsheetInterface {
    ClassSpreadsheet processSharedSpreadsheet(String sharedLink, TeacherEntity teacher)
            throws IOException, GeneralSecurityException;

    boolean canProcessLink(String link);
}
