package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AuthorizationCodeCredential;
import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class MicrosoftGraphTokenService {
    private final UserTokenRepository userTokenRepository;
    private final AzureConfig azureConfig;

    public void storeUserToken(int userId, String authorizationCode) {
        // Store the authorization code in the database
        try{
            TokenResponse tokens = exchangeCodeForTokens(authorizationCode);
            userTokenRepository.deleteByUserId(userId);

            UserToken userToken = new UserToken();
            userToken.setUserId(userId);
            userToken.setAccessToken(tokens.getAccessToken());
            userToken.setRefreshToken(tokens.getRefreshToken());
            userToken.setExpiresAt(LocalDateTime.now().plusSeconds(tokens.getExpiresAt()));
            userToken.setCreatedAt(LocalDateTime.now());

            userTokenRepository.save(userToken);
        }catch (Exception e){
            throw new RuntimeException("Failed to store user token: " + e.getMessage(), e);
        }
    }
    @Transactional
    public void storeUserTokenDirect(int userId, AccessToken accessToken) {
        try {
            userTokenRepository.deleteByUserId(userId);

            UserToken userToken = new UserToken();
            userToken.setUserId(userId);
            userToken.setAccessToken(accessToken.getToken());
            userToken.setExpiresAt(LocalDateTime.ofInstant(accessToken.getExpiresAt().toInstant(), ZoneId.systemDefault()));
            userToken.setCreatedAt(LocalDateTime.now());

            userTokenRepository.save(userToken);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store user token: " + e.getMessage(), e);
        }
    }
    private TokenResponse exchangeCodeForTokens(String code) {
        AuthorizationCodeCredential credential = new AuthorizationCodeCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .clientSecret(azureConfig.getClientSecret())
                .tenantId(azureConfig.getTenantId())
                .authorizationCode(code)
                .redirectUrl(azureConfig.getRedirectUri())
                .build();

        TokenRequestContext context = new TokenRequestContext()
                .addScopes("https://graph.microsoft.com/Files.Read",
                        "https://graph.microsoft.com/Files.ReadWrite",
                        "https://graph.microsoft.com/User.Read");

        AccessToken token = credential.getToken(context).block();
        if (token == null) {
            throw new RuntimeException("Failed to obtain access token");
        }
        return new TokenResponse(token.getToken(), null,
                (int) Duration.between(Instant.now(), token.getExpiresAt()).getSeconds());
    }

}
