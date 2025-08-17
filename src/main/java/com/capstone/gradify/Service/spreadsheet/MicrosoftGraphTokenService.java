package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.Controller.notification.NotificationController;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MicrosoftGraphTokenService {
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphTokenService.class);
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
            userToken.setExpiresAt(LocalDateTime.now().plusSeconds(tokens.getExpiresIn()));
            userToken.setCreatedAt(LocalDateTime.now());

            userTokenRepository.save(userToken);
        }catch (Exception e){
            throw new RuntimeException("Failed to store user token: " + e.getMessage(), e);
        }
    }
    @Transactional
    public void storeUserTokenDirect(int userId, String accessToken, String refreshToken, int expiresAt) {
        try {
            userTokenRepository.deleteByUserId(userId);

            UserToken userToken = new UserToken();
            userToken.setUserId(userId);
            userToken.setAccessToken(accessToken);
            userToken.setRefreshToken(refreshToken);
            userToken.setExpiresAt(LocalDateTime.now().plusSeconds(expiresAt));
            userToken.setCreatedAt(LocalDateTime.now());

            userTokenRepository.save(userToken);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store user token: " + e.getMessage(), e);
        }
    }

    public TokenResponse exchangeCodeForTokens(String code) {

        String tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", azureConfig.getClientId());
        params.add("client_secret", azureConfig.getClientSecret());
        params.add("code", code);
        params.add("redirect_uri", azureConfig.getRedirectUri());
        params.add("grant_type", "authorization_code");
        params.add("scope", "https://graph.microsoft.com/.default offline_access");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);

            Map body = response.getBody();
            if (body == null || !body.containsKey("access_token")) {
                throw new RuntimeException("Failed to obtain access token: " + body);
            }

            return new TokenResponse(
                    (String) body.get("access_token"),
                    (String) body.get("refresh_token"),
                    ((Number) body.get("expires_in")).intValue()
            );
        } catch (HttpClientErrorException e) {
            // Log the error response for debugging
            logger.error("Token exchange failed with status: {}", e.getStatusCode());
            logger.error("Error response body: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Token exchange failed: " + e.getMessage() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during token exchange: " + e.getMessage());
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }


    public TokenResponse refreshAccessToken(String refreshToken) {
        String tokenEndpoint = "https://login.microsoftonline.com/" + azureConfig.getTenantId() + "/oauth2/v2.0/token";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", azureConfig.getClientId());
        params.add("client_secret", azureConfig.getClientSecret());
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");
        params.add("scope", "https://graph.microsoft.com/Files.Read https://graph.microsoft.com/Files.ReadWrite https://graph.microsoft.com/User.Read offline_access");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
            Map body = response.getBody();

            if (body == null) {
                throw new RuntimeException("Empty response from token endpoint");
            }

            if (body.containsKey("error")) {
                String error = (String) body.get("error");
                String errorDescription = (String) body.get("error_description");
                throw new RuntimeException("Token refresh failed: " + error + " - " + errorDescription);
            }

            if (!body.containsKey("access_token")) {
                throw new RuntimeException("No access_token in response: " + body);
            }

            return new TokenResponse(
                    (String) body.get("access_token"),
                    (String) body.get("refresh_token"),
                    ((Number) body.get("expires_in")).intValue()
            );
        } catch (RestClientException e) {
            throw new RuntimeException("Network error during token refresh: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during token refresh: " + e.getMessage(), e);
        }

    }

}
