package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeNotification {
    private String id;
    private String subscriptionId;
    private OffsetDateTime subscriptionExpirationDateTime;
    private String clientState;
    private String changeType;
    private String resource;
    private ResourceData resourceData;
    private String tenantId;
}
