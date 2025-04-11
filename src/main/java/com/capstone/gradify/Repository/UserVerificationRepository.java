package com.capstone.gradify.Repository;

import com.capstone.gradify.Entity.UserEntity;
import com.capstone.gradify.Entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationRepository extends JpaRepository<VerificationCode, Long> {
    VerificationCode findByUser(UserEntity user);
}
