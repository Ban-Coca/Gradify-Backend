package com.capstone.gradify.Config;

import lombok.Data;
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

    @Bean
    public String authorizationUrl(){
        return String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?" +
                        "client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s",
                tenantId, clientId, redirectUri, scope, "security-state");
    }
}
