package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Entity.user.VerificationCode;
import com.capstone.gradify.Repository.UserVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    private final UserVerificationRepository verificationRepo;
    private static final int EXPIRATION_MINUTES = 30;

    public void createVerificationCode(UserEntity user, String code) {
        // Delete any existing codes for this user first
        deleteByUser(user);

        // Create new verification code
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setUser(user);
        verificationCode.setCode(code);
        verificationCode.setCreatedAt(LocalDateTime.now());
        verificationRepo.save(verificationCode);
    }

    public Optional<VerificationCode> findByUser(UserEntity user) {
        return Optional.ofNullable(verificationRepo.findByUser(user));
    }

    public boolean isCodeValid(VerificationCode verificationCode) {
        LocalDateTime expirationTime = verificationCode.getCreatedAt().plusMinutes(EXPIRATION_MINUTES);
        return LocalDateTime.now().isBefore(expirationTime);
    }

    public void deleteByUser(UserEntity user) {
        VerificationCode existingCode = verificationRepo.findByUser(user);
        if (existingCode != null) {
            verificationRepo.delete(existingCode);
        }
    }

    public VerificationCode save(VerificationCode verificationCode) {
        return verificationRepo.save(verificationCode);
    }
}