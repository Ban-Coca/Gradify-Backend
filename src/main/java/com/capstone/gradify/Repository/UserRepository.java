package com.capstone.gradify.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.capstone.gradify.Entity.UserEntity;



@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer>{
  UserEntity findByEmail(String email);
}