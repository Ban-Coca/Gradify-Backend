package com.capstone.gradify.Service.userservice;

import com.azure.identity.AuthorizationCodeCredential;
import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.capstone.gradify.Config.AzureConfig;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AzureConfig azureConfig;

    public String getAuthorizationUrl() {
        return String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?" +
                "client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s&response_mode=query",
                azureConfig.getTenantId(),
                azureConfig.getClientId(),
                URLEncoder.encode(azureConfig.getRedirectUri(), StandardCharsets.UTF_8),
                URLEncoder.encode(azureConfig.getScope(), StandardCharsets.UTF_8),
                generateSecurityState()
        );
    }

    public User authenticateWithCode(String code) {
        try {
            AuthorizationCodeCredential credential = new AuthorizationCodeCredentialBuilder()
                    .clientId(azureConfig.getClientId())
                    .clientSecret(azureConfig.getClientSecret())
                    .tenantId(azureConfig.getTenantId())
                    .authorizationCode(code)
                    .redirectUrl(azureConfig.getRedirectUri())
                    .build();

            GraphServiceClient graphClient = new GraphServiceClient(credential);

            return graphClient.me().get();
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    public GraphServiceClient createGraphClient(String code) {
        AuthorizationCodeCredential credential = new AuthorizationCodeCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .clientSecret(azureConfig.getClientSecret())
                .tenantId(azureConfig.getTenantId())
                .authorizationCode(code)
                .redirectUrl(azureConfig.getRedirectUri())
                .build();

        return new GraphServiceClient(credential);
    }

    private String generateSecurityState() {
        return UUID.randomUUID().toString();
    }
}
