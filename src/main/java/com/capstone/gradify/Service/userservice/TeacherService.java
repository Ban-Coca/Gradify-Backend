package com.capstone.gradify.Service.userservice;

import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TeacherService {

    @Autowired
    private TeacherRepository teacherRepository;

    public TeacherService() {
        super();
    }

    public TeacherEntity saveTeacherDetails(TeacherEntity teacher) {
        return teacherRepository.save(teacher);
    }
}
