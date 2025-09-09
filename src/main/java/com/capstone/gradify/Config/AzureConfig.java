package com.capstone.gradify.Config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "azure")
@Data
public class AzureConfig {
    private String clientId;
    private String clientSecret;
    private String tenantId;
    private String redirectUri;
    private String scope;

    @Value("${azure.storage.connection-string}")
    private String storageConnectionString;

    @Value("${azure.storage.container.profile-pictures}")
    private String profilePicturesContainer;

    @Bean
    public String authorizationUrl(){
        return String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?" +
                        "client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s",
                tenantId, clientId, redirectUri, scope, "security-state");
    }

    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
    }
}
