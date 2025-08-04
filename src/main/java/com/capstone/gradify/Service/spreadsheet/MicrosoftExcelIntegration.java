package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
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

@Service
@RequiredArgsConstructor
public class MicrosoftExcelIntegration {

    private final ClassSpreadsheetService classSpreadsheetService;
    private final ClassRepository classRepository;
    private final UserTokenRepository userTokenRepository;
    private final AzureConfig azureConfig;

//    public List<DriveItem> getRootFiles(int userId) {
//        UserToken userToken = getUserToken(userId);
//        GraphServiceClient client = createGraphClient(userToken.getAccessToken());
//
//
//        return List;
//    }

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

    public DriveItemCollectionResponse getRootFiles(int userId){
        UserToken userToken = getUserToken(userId);
        GraphServiceClient client = createGraphClient(userToken.getAccessToken(), userToken.getExpiresAt());

        DriveItemCollectionResponse response = client.drives().byDriveId(getUserDriveIds(userId)).items().byDriveItemId(getRootDriveItemId(userId)).children().get(
                requestConfiguration -> {
                    if (requestConfiguration
                            .queryParameters != null) {
                        requestConfiguration
                                .queryParameters.select = new String[]{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl"};
                    }
                }
        );
        return response;
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
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            // TODO: Implement token refresh
            throw new RuntimeException("Token expired, please re-authenticate");
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

    /**
     * Extract filename from URL for display purposes
     */
    private String extractFileName(String url) {
        try {
            // Try to extract filename from URL path
            String[] pathParts = url.split("/");
            for (int i = pathParts.length - 1; i >= 0; i--) {
                if (pathParts[i].contains(".xlsx") || pathParts[i].contains(".xls")) {
                    return pathParts[i];
                }
            }
            return "Shared Excel File";
        } catch (Exception e) {
            return "Shared Excel File";
        }
    }

    public static class DriveIds {
        private final String driveId;
        private final String rootDriveItemId;

        public DriveIds(String driveId, String rootDriveItemId) {
            this.driveId = driveId;
            this.rootDriveItemId = rootDriveItemId;
        }

        public String getDriveId() { return driveId; }
        public String getRootDriveItemId() { return rootDriveItemId; }
    }

//    public DriveIds getUserDriveIds(int userId) {
//        UserToken userToken = getUserToken(userId);
//        GraphServiceClient client = createGraphClient(userToken.getAccessToken());
//
//        Drive drive = client.me().drive().get();
//        String driveId = drive.id;
//        String rootDriveItemId = drive.root != null ? drive.root.id : null;
//
//        return new DriveIds(driveId, rootDriveItemId);
//    }
}