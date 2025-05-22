package com.capstone.gradify.dto;

import lombok.Data;

@Data
public class DeviceRegistrationRequest {
    private String token;
    private int userId;
}
