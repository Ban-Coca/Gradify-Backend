package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.TokenResponse;
import com.capstone.gradify.mapper.DriveItemMapper;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.azure.core.credential.TokenCredential;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MicrosoftExcelIntegration {

    private final ClassSpreadsheetService classSpreadsheetService;
    private final ClassRepository classRepository;
    private final UserTokenRepository userTokenRepository;
    private final AzureConfig azureConfig;
    private final MicrosoftGraphTokenService microsoftGraphTokenService;
    private final DriveItemMapper driveItemMapper;
    public String getUserDriveIds(int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        Drive drive = client.me().drive().get();

        // Get the root item to get its ID
        return drive != null ? drive.getId() : null;
    }
    public String getRootDriveItemId(int userId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItem driveItemId = client.drives().byDriveId(getUserDriveIds(userId)).root().get();

        return driveItemId != null ? driveItemId.getId() : null;
    }

    public List<DriveItemResponse> getRootFiles(int userId){
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItemCollectionResponse response = client.drives().byDriveId(getUserDriveIds(userId)).items().byDriveItemId(getRootDriveItemId(userId)).children().get(
                requestConfiguration -> {
                    assert requestConfiguration.queryParameters != null;
                    requestConfiguration.queryParameters.select = new String []{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl"};
                }
        );
        return driveItemMapper.toDTO(response);
    }

    public DriveItemCollectionResponse getFolderFiles(int userId, String folderId) {
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItemCollectionResponse response = client.drives().byDriveId(getUserDriveIds(userId))
                .items().byDriveItemId(folderId).children().get(
                requestConfiguration -> {
                    if (requestConfiguration.queryParameters != null) {
                        requestConfiguration.queryParameters.select = new String[]{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl", "@microsoft.graph.downloadUrl"};
                    }
                }
        );
        return response;
    }

    private UserToken getUserToken(int userId) {
        UserToken userToken = userTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not authenticated with Microsoft Graph"));

        // Check if token is expired and refresh if needed
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) { // Add 5min buffer
            if (userToken.getRefreshToken() == null || userToken.getRefreshToken().isEmpty()) {
                throw new RuntimeException("Token expired and no refresh token available, please re-authenticate");
            }

            try {
                // Use the correct refresh method
                TokenResponse refreshed = microsoftGraphTokenService.refreshAccessToken(userToken.getRefreshToken());

                // Update the token
                userToken.setAccessToken(refreshed.getAccessToken());
                if (refreshed.getRefreshToken() != null && !refreshed.getRefreshToken().isEmpty()) {
                    userToken.setRefreshToken(refreshed.getRefreshToken());
                }
                userToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshed.getExpiresIn()));
                userTokenRepository.save(userToken);

                // Return the refreshed token - don't throw exception!
                return userToken;

            } catch (Exception e) {
                throw new RuntimeException("Failed to refresh token, please re-authenticate", e);
            }
        }

        return userToken;
    }

    private GraphServiceClient createGraphClient(String accessToken, LocalDateTime expiresAt) {
        TokenCredential credential = new TokenCredential() {
            @Override
            public Mono<AccessToken> getToken(TokenRequestContext request) {
                return Mono.just(new AccessToken(
                        accessToken,
                        expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                ));
            }
        };
        return new GraphServiceClient(credential);
    }

}