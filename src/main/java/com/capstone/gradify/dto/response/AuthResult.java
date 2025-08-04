package com.capstone.gradify.dto.response;

import com.azure.core.credential.AccessToken;
import com.microsoft.graph.models.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AuthResult {
    private final User user;
    private final AccessToken token;
}
