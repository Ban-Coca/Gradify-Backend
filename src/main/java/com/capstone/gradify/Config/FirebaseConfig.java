package com.capstone.gradify.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${fcm.service.key}")
    private String firebaseCredentials;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        GoogleCredentials credentials = null;

        // First try to use credentials from environment variable
        if (firebaseCredentials != null && !firebaseCredentials.isEmpty()) {
            byte[] decodedCredentials = Base64.getDecoder().decode(firebaseCredentials);
            credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(decodedCredentials))
                    .createScoped("https://www.googleapis.com/auth/firebase");
        }
        log.info("firebaseCredentials is {}", credentials);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        // Initialize the app if it doesn't exist
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                log.info("Initializing Firebase App with provided credentials");
                return FirebaseApp.initializeApp(options);
            } catch (Exception e) {
                log.error("Failed to initialize Firebase App: {}", e.getMessage(), e);
                throw new RuntimeException("Firebase initialization failed", e);
            }
        } else {
            try {
                log.info("Firebase App already initialized, returning existing instance");
                return FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                log.error("Failed to retrieve Firebase App instance: {}", e.getMessage(), e);
                throw new RuntimeException("Firebase instance retrieval failed", e);
            }
        }
    }
}
