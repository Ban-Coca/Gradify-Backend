package com.capstone.gradify.Service.userservice;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import javax.naming.NameNotFoundException;

import com.capstone.gradify.Controller.user.UserController;
import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.user.UserRepository;

@Service
@Transactional
public class UserService {
	private static final Logger logger = LoggerFactory.getLogger(UserService.class);
	@Autowired
	UserRepository urepo;

	@PersistenceContext
	private EntityManager entityManager;
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
		if (user.getRole() == Role.PENDING) {
			return urepo.save(user);
		}
		if (user.getRole() == Role.TEACHER) {

			if (user instanceof TeacherEntity) {
				TeacherEntity t = (TeacherEntity) user;
				logger.debug("Saving teacher: email={}, institution={}, department={}",
						t.getEmail(), t.getInstitution(), t.getDepartment());

				return teacherRepository.save(t);
			}
			else {
				TeacherEntity teacher = new TeacherEntity();

				copyUserProperties(user, teacher);
				teacher.setRole(Role.TEACHER);
				if (user.getAttribute("institution") != null) {
					teacher.setInstitution((String) user.getAttribute("institution"));
				}
				if (user.getAttribute("department") != null) {
					teacher.setDepartment((String) user.getAttribute("department"));
				}

				return teacherRepository.save(teacher);
			}
		} else if (user.getRole() == Role.STUDENT) {

			String studentNumber = null;
			if (user instanceof StudentEntity) {
				studentNumber = ((StudentEntity) user).getStudentNumber();
			}

			Optional<StudentEntity> existingStudent = studentRepository.findByStudentNumber(studentNumber);

			if (existingStudent.isPresent()) {
				StudentEntity student = existingStudent.get();
				copyUserProperties(user, student);
				student.setRole(Role.STUDENT);

				// Preserve any student-specific fields if they're not in the incoming object
				StudentEntity studentEntity = (StudentEntity) user;
				if (studentEntity.getMajor() != null) {
					student.setMajor(studentEntity.getMajor());
				}
				if (studentEntity.getYearLevel() != null) {
					student.setYearLevel(studentEntity.getYearLevel());
				}
				return studentRepository.save(student);
			}

			// If not found, save as new student
			if (user instanceof StudentEntity) {
				return studentRepository.save((StudentEntity) user);
			} else {
				StudentEntity student = new StudentEntity();
				copyUserProperties(user, student);
				student.setRole(Role.STUDENT);
				return studentRepository.save(student);
			}
		} else {
			return urepo.save(user);
		}
	}

	private void copyUserProperties(UserEntity source, UserEntity target) {
		// If the user ID is already set (for existing users), maintain it
		if (source.getUserId() > 0) {
			target.setUserId(source.getUserId());
		}

		target.setFirstName(source.getFirstName());
		target.setLastName(source.getLastName());
		target.setEmail(source.getEmail());
		target.setPassword(source.getPassword());
		target.setIsActive(source.isActive());
		target.setProvider(source.getProvider());
		target.setCreatedAt(source.getCreatedAt());
		target.setLastLogin(source.getLastLogin());
		target.setFailedLoginAttempts(source.getFailedLoginAttempts());
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

	@SuppressWarnings("finally")
	public UserEntity putUserDetails(int userId, UserEntity newUserDetails) {
		UserEntity user;

		try {
			user = urepo.findById(userId).orElseThrow(() ->
					new NameNotFoundException("User " + userId + " not found"));

			// Check if we need to convert user type based on role change
			if (newUserDetails.getRole() != user.getRole()) {
				// If role is changing, we may need to create a different entity type
				if (newUserDetails.getRole() == Role.TEACHER && !(user instanceof TeacherEntity)) {
					TeacherEntity teacher = new TeacherEntity();
					copyUserProperties(user, teacher);
					teacher.setRole(Role.TEACHER);
					user = teacher;
				}
				else if (newUserDetails.getRole() == Role.STUDENT && !(user instanceof StudentEntity)) {
					StudentEntity student = new StudentEntity();
					copyUserProperties(user, student);
					student.setRole(Role.STUDENT);
					user = student;
				}
			}

			// Update basic user properties
			user.setFirstName(newUserDetails.getFirstName());
			user.setLastLogin(newUserDetails.getLastLogin());
			user.setLastName(newUserDetails.getLastName());
			user.setEmail(newUserDetails.getEmail());

			// Only update password if provided (not null or empty)
			if (newUserDetails.getPassword() != null && !newUserDetails.getPassword().isEmpty()) {
				user.setPassword(newUserDetails.getPassword());
			}

			user.setCreatedAt(newUserDetails.getCreatedAt());
			user.setFailedLoginAttempts(newUserDetails.getFailedLoginAttempts());
			user.setRole(newUserDetails.getRole());
			user.setIsActive(newUserDetails.isActive());

			// Handle role-specific properties
			if (user instanceof StudentEntity && newUserDetails instanceof StudentEntity) {
				StudentEntity studentUser = (StudentEntity) user;
				StudentEntity newStudent = (StudentEntity) newUserDetails;

				if (newStudent.getStudentNumber() != null) {
					studentUser.setStudentNumber(newStudent.getStudentNumber());
				}
				if (newStudent.getMajor() != null) {
					studentUser.setMajor(newStudent.getMajor());
				}
				if (newStudent.getYearLevel() != null) {
					studentUser.setYearLevel(newStudent.getYearLevel());
				}
			}
			else if (user instanceof TeacherEntity && newUserDetails instanceof TeacherEntity) {
				TeacherEntity teacherUser = (TeacherEntity) user;
				TeacherEntity newTeacher = (TeacherEntity) newUserDetails;

				if (newTeacher.getDepartment() != null) {
					teacherUser.setDepartment(newTeacher.getDepartment());
				}
			}

			// Save the updated user
			return postUserRecord(user);

		} catch (NoSuchElementException | NameNotFoundException ex) {
			throw new RuntimeException("User " + userId + " not found", ex);
		}
	}

	@Transactional
	public UserEntity changeUserRole(int userId, Role newRole) {
		UserEntity user = findById(userId);
		if (user == null) {
			return null;
		}
		if (user.getRole() == newRole) {
			return user; // No change needed
		}
		// Instead of delete+save, use a more direct approach
		if (newRole == Role.TEACHER && !(user instanceof TeacherEntity)) {
			// Create new teacher and transfer ID + data
			TeacherEntity teacher = new TeacherEntity();
			BeanUtils.copyProperties(user, teacher);
			teacher.setRole(Role.TEACHER);
			return urepo.save(teacher);
		}
		else if (newRole == Role.STUDENT && !(user instanceof StudentEntity)) {
			// Create new student and transfer ID + data
			StudentEntity student = new StudentEntity();
			BeanUtils.copyProperties(user, student);
			student.setRole(Role.STUDENT);
			return urepo.save(student);
		}

		// If just updating role without changing entity type
		user.setRole(newRole);
		return urepo.save(user);
	}

	public UserEntity updateUser(UserEntity user) {
		return urepo.save(user);
	}

//	@Transactional
//	public void detachUser(int userId) {
//		// First find the user
//		UserEntity user = urepo.findById(userId).orElse(null);
//		if (user == null) {
//			return;
//		}
//
//		// Check the type of entity and delete accordingly
//		if (user instanceof StudentEntity) {
//			// Use native query to delete only from the student_entity table
//			// This preserves the base user record
//			entityManager.createNativeQuery("DELETE FROM student_entity WHERE user_id = ? ")
//					.setParameter(1, userId)
//					.executeUpdate();
//		} else if (user instanceof TeacherEntity) {
//			// Use native query to delete only from the teacher_entity table
//			// This preserves the base user record
//			entityManager.createNativeQuery("DELETE FROM teacher_entity WHERE user_id = ?")
//					.setParameter(1, userId)
//					.executeUpdate();
//		}
//
//		// Clear the persistence context to avoid any cached entities
//		entityManager.flush();
//		entityManager.clear();
//	}

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