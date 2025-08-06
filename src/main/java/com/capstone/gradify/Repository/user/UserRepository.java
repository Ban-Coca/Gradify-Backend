package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.capstone.gradify.Entity.user.UserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer>{
  UserEntity findByEmail(String email);
  List<UserEntity> findByRole(String role);
  Optional<UserEntity> findByAzureId(String azureId);

  @Modifying
  @Query(value = "UPDATE users SET role = 'TEACHER' WHERE user_id = :userId", nativeQuery = true)
  int updateUserRoleToTeacher(@Param("userId") int userId);

  @Modifying
  @Query(value = "UPDATE users SET role = 'STUDENT' WHERE user_id = :userId", nativeQuery = true)
  int updateUserRoleToStudent(@Param("userId") int userId);

  @Query(value = "SELECT " +
          "  CASE " +
          "    WHEN t.user_id IS NOT NULL THEN 'TEACHER' " +
          "    WHEN s.user_id IS NOT NULL THEN 'STUDENT' " +
          "    WHEN u.role = 'PENDING' THEN 'PENDING' " +
          "    ELSE 'USER' " +
          "  END as user_type " +
          "FROM users u " +
          "LEFT JOIN teacher_entity t ON u.user_id = t.user_id " +
          "LEFT JOIN student_entity s ON u.user_id = s.user_id " +
          "WHERE u.user_id = :userId",
          nativeQuery = true)
  String getUserType(@Param("userId") int userId);
}