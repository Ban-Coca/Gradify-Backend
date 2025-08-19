package com.capstone.gradify.Repository;

import com.capstone.gradify.Entity.TempTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TempTokensRepository extends JpaRepository<TempTokens, Integer> {
    Optional<TempTokens> findByAzureId(String azureId);
    void deleteByAzureId(String azureId);
}
