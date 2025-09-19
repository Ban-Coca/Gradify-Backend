package com.capstone.gradify.Entity.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "temp_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TempTokens {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String azureId;
    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;
    private int expiresIn;

    private LocalDateTime createdAt = LocalDateTime.now();
}
