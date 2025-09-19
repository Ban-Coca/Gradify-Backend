package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.TempTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TempTokensRepository extends JpaRepository<TempTokens, Integer> {
    Optional<TempTokens> findByAzureId(String azureId);
    void deleteByAzureId(String azureId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TempTokens t WHERE t.createdAt < :cutoff")
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
