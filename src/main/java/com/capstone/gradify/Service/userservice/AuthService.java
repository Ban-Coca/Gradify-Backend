package com.capstone.gradify.Service.userservice;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AuthorizationCodeCredential;
import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.dto.response.AuthResult;
import com.microsoft.aad.msal4j.AuthorizationCodeParameters;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AzureConfig azureConfig;

    public String getAuthorizationUrl() {
        return String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?" +
                "client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s&response_mode=query"+
                "&prompt=consent",
                azureConfig.getTenantId(),
                azureConfig.getClientId(),
                URLEncoder.encode(azureConfig.getRedirectUri(), StandardCharsets.UTF_8),
                URLEncoder.encode(azureConfig.getScope(), StandardCharsets.UTF_8),
                generateSecurityState()
        );
    }

    public AuthResult authenticateWithCode(String code) {
        try {
            ConfidentialClientApplication app = ConfidentialClientApplication.builder(
                    azureConfig.getClientId(),
                    ClientCredentialFactory.createFromSecret(azureConfig.getClientSecret()))
                    .authority("https://login.microsoftonline.com/" + azureConfig.getTenantId())
                    .build();

            AuthorizationCodeParameters parameters =AuthorizationCodeParameters.builder(
                    code,
                    URI.create(azureConfig.getRedirectUri()))
                    .scopes(Set.of(
                            "https://graph.microsoft.com/Files.ReadWrite",
                            "https://graph.microsoft.com/User.Read"))
                    .build();

            IAuthenticationResult result = app.acquireToken(parameters).get();

            AccessToken accessToken = new AccessToken(
                    result.accessToken(),
                    result.expiresOnDate().toInstant().atOffset(ZoneOffset.UTC)
            );

            GraphServiceClient graphClient = new GraphServiceClient(
                    new TokenCredential() {
                        @Override
                        public Mono<AccessToken> getToken(TokenRequestContext tokenRequestContext) {
                            return Mono.just(accessToken);
                        }
                    }
            );

            User user = graphClient.me().get();
            return new AuthResult(user, accessToken);
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
