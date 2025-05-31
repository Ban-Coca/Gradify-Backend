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
        SpringApplication.run(Application.class, args);
    }
}