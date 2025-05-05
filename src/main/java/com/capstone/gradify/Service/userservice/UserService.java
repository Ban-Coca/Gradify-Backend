package com.capstone.gradify.Service.userservice;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import javax.naming.NameNotFoundException;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.user.UserRepository;

@Service
public class UserService {

	@Autowired
	UserRepository urepo;

	@Value("${app.upload.dir}")
  	private String uploadDir;
    @Autowired
    private TeacherRepository teacherRepository;
	@Autowired
	private StudentRepository studentRepository;

	public UserService() {
		super();
	}

	public UserEntity findByEmail(String email) {
		return urepo.findByEmail(email);
	}
	
	//Create of CRUD
	public UserEntity postUserRecord(UserEntity user) {
		if (user.getRole() == Role.TEACHER) {

			if (user instanceof TeacherEntity) {
				return teacherRepository.save((TeacherEntity) user);
			}

			else {
				TeacherEntity teacher = new TeacherEntity();

				teacher.setFirstName(user.getFirstName());
				teacher.setLastName(user.getLastName());
				teacher.setEmail(user.getEmail());
				teacher.setPassword(user.getPassword());
				teacher.setIsActive(user.isActive());
				teacher.setProvider(user.getProvider());
				teacher.setCreatedAt(user.getCreatedAt());
				teacher.setLastLogin(user.getLastLogin());
				teacher.setFailedLoginAttempts(user.getFailedLoginAttempts());
				teacher.setRole(Role.TEACHER);

				return teacherRepository.save(teacher);
			}
		} else if (user.getRole() == Role.STUDENT) {

			String studentNumber = null;
			if (user instanceof StudentEntity) {
				studentNumber = ((StudentEntity) user).getStudentNumber();
			}


			if (studentNumber != null && !studentNumber.isEmpty()) {
				Optional<StudentEntity> existingStudent = studentRepository.findByStudentNumber(studentNumber);

				if (existingStudent.isPresent()) {

					StudentEntity student = existingStudent.get();
					student.setFirstName(user.getFirstName());
					student.setLastName(user.getLastName());
					student.setEmail(user.getEmail());
					student.setPassword(user.getPassword());
					student.setIsActive(user.isActive());
					student.setProvider(user.getProvider());
					student.setCreatedAt(user.getCreatedAt());
					student.setLastLogin(user.getLastLogin());
					student.setFailedLoginAttempts(user.getFailedLoginAttempts());
					student.setRole(Role.STUDENT);

					// Preserve any student-specific fields if they're not in the incoming object
					if (user instanceof StudentEntity) {
						StudentEntity studentEntity = (StudentEntity) user;
						if (studentEntity.getMajor() != null) {
							student.setMajor(studentEntity.getMajor());
						}
						if (studentEntity.getYearLevel() != null) {
							student.setYearLevel(studentEntity.getYearLevel());
						}
					}

					return studentRepository.save(student);
				}
			}
			if (user instanceof StudentEntity) {
				return studentRepository.save((StudentEntity) user);
			} else {
				// Create a new StudentEntity and copy properties from the UserEntity
				StudentEntity student = new StudentEntity();

				student.setFirstName(user.getFirstName());
				student.setLastName(user.getLastName());
				student.setEmail(user.getEmail());
				student.setPassword(user.getPassword());
				student.setIsActive(user.isActive());
				student.setProvider(user.getProvider());
				student.setCreatedAt(user.getCreatedAt());
				student.setLastLogin(user.getLastLogin());
				student.setFailedLoginAttempts(user.getFailedLoginAttempts());
				student.setRole(Role.STUDENT);

				return studentRepository.save(student);
			}
		} else {
			return urepo.save(user);
		}
	}

	//find by ID
	public UserEntity findById(int userId) {
		return urepo.findById(userId).get();
	}
	
	//Read of CRUD
	public List<UserEntity> getAllUsers(){
		return urepo.findAll();
	}
	
	// public List<UserEntity> getUsersByRole(String role) {
	//     return urepo.findByRole(role);
	// }
	
	//Update of CRUD
	@SuppressWarnings("finally")
	public UserEntity putUserDetails (int userId, UserEntity newUserDetails) {
		UserEntity user = new UserEntity();
		
		try {
			user = urepo.findById(userId).get();
			
			user.setFirstName(newUserDetails.getFirstName());
			user.setLastName(newUserDetails.getLastName());
			user.setEmail(newUserDetails.getEmail());
			user.setPassword(newUserDetails.getPassword());
			user.setCreatedAt(newUserDetails.getCreatedAt());
            user.setLastLogin(newUserDetails.getLastLogin());
            user.setFailedLoginAttempts(newUserDetails.getFailedLoginAttempts());
			user.setRole(newUserDetails.getRole());
		}catch(NoSuchElementException nex){
			throw new NameNotFoundException("User "+ userId +"not found");
		}finally {
			return urepo.save(user);
		}
	}
	
	//Delete of CRUD
	public String deleteUser(int userId) {
		String msg = "";
		
		if(urepo.findById(userId).isPresent()) {
			urepo.deleteById(userId);
			msg = "User record successfully deleted!";
		}else {
			msg = "User ID "+ userId +" NOT FOUND!";
		}
		return msg;
	}

    @Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
    public void deactivateInactiveUsers() {
        List<UserEntity> users = urepo.findAll();
        Date now = new Date();

        for (UserEntity user : users) {
            if (user.getLastLogin() != null) {
                long diffInMillis = now.getTime() - user.getLastLogin().getTime();
                long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

                if (diffInDays > 30 && user.isActive()) {
                    user.setIsActive(false);
                    urepo.save(user);
                }
            }
        }
    }
}