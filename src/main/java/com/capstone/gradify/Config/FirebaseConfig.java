package com.capstone.gradify.Config;

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
public class FirebaseConfig {

    @Value("${fcm.service.key}")
    private String firebaseCredentials;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        GoogleCredentials credentials = null;

        // First try to use credentials from environment variable
        if (firebaseCredentials != null && !firebaseCredentials.isEmpty()) {
            byte[] decodedCredentials = Base64.getDecoder().decode(firebaseCredentials);
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decodedCredentials));
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        // Initialize the app if it doesn't exist
        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        } else {
            return FirebaseApp.getInstance();
        }
    }
}
