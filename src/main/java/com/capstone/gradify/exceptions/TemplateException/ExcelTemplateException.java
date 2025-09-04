package com.capstone.gradify.exceptions.TemplateException;

import com.capstone.gradify.exceptions.BaseException;

public class ExcelTemplateException extends BaseException {
    public ExcelTemplateException(String message) {
        super("TEMPLATE_ERROR", message);
    }
}
