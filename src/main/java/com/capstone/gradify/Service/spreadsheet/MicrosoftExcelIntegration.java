package com.capstone.gradify.Service.spreadsheet;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.capstone.gradify.Config.AzureConfig;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.dto.response.DriveItemResponse;
import com.capstone.gradify.dto.response.ExtractedExcelResponse;
import com.capstone.gradify.dto.response.TokenResponse;
import com.capstone.gradify.mapper.DriveItemMapper;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.capstone.gradify.Repository.records.ClassRepository;
import com.azure.core.credential.TokenCredential;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MicrosoftExcelIntegration {
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftExcelIntegration.class);
    private final ClassSpreadsheetService classSpreadsheetService;
    private final ClassRepository classRepository;
    private final UserTokenRepository userTokenRepository;
    private final AzureConfig azureConfig;
    private final MicrosoftGraphTokenService microsoftGraphTokenService;
    private final DriveItemMapper driveItemMapper;
    private final WebClient webClient;
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
                    if (requestConfiguration.queryParameters != null) {
                        requestConfiguration.queryParameters.select = new String []{"id", "name", "size", "lastModifiedDateTime", "folder", "file", "webUrl"};
                    }
                }
        );
        return driveItemMapper.toDTO(response);
    }

    public ExtractedExcelResponse getUsedRange(String folderName, String fileName, int userId) {
        UserToken userToken = getUserToken(userId);

        String encodedFolder = Arrays.stream(folderName.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));

        String encodedFile = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        logger.debug("Encoded Folder: {}, Encoded File: {}", encodedFolder, encodedFile);

        return webClient.get()
                .uri("/me/drive/root:/{folder}/{file}:/workbook/worksheets/Sheet1/usedRange(valuesOnly=true)?$select=address,addressLocal,values",
                        encodedFolder, encodedFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken.getAccessToken())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .bodyToMono(ExtractedExcelResponse.class)
                .block();
    }



    public List<DriveItemResponse> getFolderFiles(int userId, String folderId) {
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
        return driveItemMapper.toDTO(response);
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