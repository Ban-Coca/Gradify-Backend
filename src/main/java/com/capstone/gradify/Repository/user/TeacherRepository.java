package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherRepository extends JpaRepository<TeacherEntity, Integer> {
    TeacherEntity findByUserId(int userId);

    @Modifying
    @Query(value = "INSERT INTO teacher_entity (user_id, institution, department) " +
            "VALUES (:userId, :institution, :department)",
            nativeQuery = true)
    void createTeacherRecord(@Param("userId") int userId,
                             @Param("institution") String institution,
                             @Param("department") String department);

    // Check if teacher record exists
    @Query(value = "SELECT COUNT(*) FROM teacher_entity WHERE user_id = :userId",
            nativeQuery = true)
    int teacherRecordExists(@Param("userId") int userId);

    // Update teacher-specific fields
    @Modifying
    @Query(value = "UPDATE teacher_entity SET institution = :institution, department = :department " +
            "WHERE user_id = :userId",
            nativeQuery = true)
    int updateTeacherDetails(@Param("userId") int userId,
                             @Param("institution") String institution,
                             @Param("department") String department);
}
