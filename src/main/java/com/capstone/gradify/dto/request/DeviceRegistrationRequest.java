package com.capstone.gradify.dto.request;

import lombok.Data;

@Data
public class DeviceRegistrationRequest {
    private String token;
    private int userId;
}
