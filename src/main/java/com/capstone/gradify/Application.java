package com.capstone.gradify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Objects;

@SpringBootApplication
@EnableScheduling

public class Application {
    public static void main(String[] args) {
        // Load environment variables from .env file
        Dotenv dotenv = Dotenv.load();
//        System.setProperty("DB_URL", Objects.requireNonNull(dotenv.get("DB_URL")));
//        System.setProperty("DB_USERNAME", Objects.requireNonNull(dotenv.get("DB_USERNAME")));
//        System.setProperty("DB_PASSWORD", Objects.requireNonNull(dotenv.get("DB_PASSWORD")));
        System.setProperty("POSTGRES_URL", Objects.requireNonNull(dotenv.get("POSTGRES_URL")));
        System.setProperty("POSTGRES_USERNAME", Objects.requireNonNull(dotenv.get("POSTGRES_USERNAME")));
        System.setProperty("POSTGRES_PASSWORD", Objects.requireNonNull(dotenv.get("POSTGRES_PASSWORD")));
        System.setProperty("POSTGRES_DRIVER", Objects.requireNonNull(dotenv.get("POSTGRES_DRIVER")));
        System.setProperty("MICROSOFT_CLIENT_ID", Objects.requireNonNull(dotenv.get("MICROSOFT_CLIENT_ID")));
        System.setProperty("MICROSOFT_CLIENT_SECRET", Objects.requireNonNull(dotenv.get("MICROSOFT_CLIENT_SECRET")));
        System.setProperty("GOOGLE_CLIENT_ID", Objects.requireNonNull(dotenv.get("GOOGLE_CLIENT_ID")));
        System.setProperty("GOOGLE_CLIENT_SECRET", Objects.requireNonNull(dotenv.get("GOOGLE_CLIENT_SECRET")));
        System.setProperty("JWT_SECRET", Objects.requireNonNull(dotenv.get("JWT_SECRET")));
        System.setProperty("JWT_EXPIRATION", Objects.requireNonNull(dotenv.get("JWT_EXPIRATION")));
        System.setProperty("google.redirect-uri", Objects.requireNonNull(dotenv.get("GOOGLE_REDIRECT_URI")));
        System.setProperty("microsoft.redirect-uri", Objects.requireNonNull(dotenv.get("MICROSOFT_REDIRECT_URI")));
        System.setProperty("EMAIL_HOST", Objects.requireNonNull(dotenv.get("EMAIL_HOST")));
        System.setProperty("EMAIL_PORT", Objects.requireNonNull(dotenv.get("EMAIL_PORT")));
        System.setProperty("EMAIL_USERNAME", Objects.requireNonNull(dotenv.get("EMAIL_USERNAME")));
        System.setProperty("EMAIL_PASSWORD", Objects.requireNonNull(dotenv.get("EMAIL_PASSWORD")));
        SpringApplication.run(Application.class, args);
    }
}