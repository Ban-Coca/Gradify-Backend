package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Entity.user.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationRepository extends JpaRepository<VerificationCode, Long> {
    VerificationCode findByUser(UserEntity user);
}
