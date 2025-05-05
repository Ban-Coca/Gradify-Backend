package com.capstone.gradify.Repository.records;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.capstone.gradify.Entity.records.GradingSchemes;
@Repository
public interface GradingSchemeRepository extends JpaRepository<GradingSchemes, Long> {

}
