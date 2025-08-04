package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByUserId(int userId);
    void deleteByUserId(int userId);
}
