package com.capstone.gradify.Repository.user;

import com.capstone.gradify.Entity.user.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<TeacherEntity, Integer> {
}
