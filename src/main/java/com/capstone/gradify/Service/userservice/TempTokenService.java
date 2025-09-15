package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.user.TempTokens;
import com.capstone.gradify.Repository.user.TempTokensRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TempTokenService {
    private static final Logger logger = LoggerFactory.getLogger(TempTokenService.class);
    private final TempTokensRepository tempTokensRepository;

    // Clean up tokens older than 30 minutes
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        tempTokensRepository.deleteByCreatedAtBefore(cutoff);
        logger.debug("Cleaned up expired temporary tokens older than {}", cutoff);
    }

    @Transactional
    public void storeTokens(String email, OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            // Remove existing tokens for this email/azureId
            tempTokensRepository.findByAzureId(email.toLowerCase())
                    .ifPresent(tempTokensRepository::delete);

            TempTokens tempTokens = new TempTokens();
            tempTokens.setAzureId(email.toLowerCase());
            tempTokens.setAccessToken(authorizedClient.getAccessToken().getTokenValue());

            if (authorizedClient.getRefreshToken() != null) {
                tempTokens.setRefreshToken(authorizedClient.getRefreshToken().getTokenValue());
            }

            // Convert Instant to seconds for expiresIn field
            if (authorizedClient.getAccessToken().getExpiresAt() != null) {
                long expiresInSeconds = authorizedClient.getAccessToken().getExpiresAt().getEpochSecond() -
                        System.currentTimeMillis() / 1000;
                tempTokens.setExpiresIn((int) expiresInSeconds);
            }

            tempTokens.setCreatedAt(LocalDateTime.now());

            tempTokensRepository.save(tempTokens);
            logger.info("Stored temporary tokens for email: {}", email);
        }
    }

    @Transactional
    public TempTokens retrieveAndRemoveTokens(String email) {
        return tempTokensRepository.findByAzureId(email.toLowerCase())
                .map(tokens -> {
                    tempTokensRepository.deleteByAzureId(email.toLowerCase());
                    logger.info("Retrieved and removed temporary tokens for email: {}", email);
                    return tokens;
                })
                .orElse(null);
    }

}
