package com.capstone.gradify.Service.userservice;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.naming.NameNotFoundException;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.dto.request.UserUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.microsoft.graph.models.User;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.user.UserRepository;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	private final UserRepository urepo;
	private final TeacherRepository teacherRepository;
	private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    @PersistenceContext
	private EntityManager entityManager;

  	// Directory for file uploads, injected from application properties
  	// This is used to store user profile pictures or other uploaded files
	@Value("${app.upload.dir}")
  	private String uploadDir;

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
		return urepo.findById(userId)
				.orElseThrow(() -> new NoSuchElementException("User with ID " + userId + " not found"));
	}
	
	//Read of CRUD
	public List<UserEntity> getAllUsers(){
		return urepo.findAll();
	}
	
	// public List<UserEntity> getUsersByRole(String role) {
	//     return urepo.findByRole(role);
	// }

	@Transactional
	public UserEntity putUserDetails(int userId, UserUpdateRequest request) {
		UserEntity user = findById(userId);
		if (request.getRole() != null) {
			return handleRoleConversion(userId, request);
		}
		return user;
	}

	@Transactional
	public void changeUserRole(int userId, Role newRole) {
		UserEntity user = findById(userId);
		if (user == null) {
			return;
		}
		if (user.getRole() == newRole) {
			return; // No change needed
		}
		// Instead of delete+save, use a more direct approach
		if (newRole == Role.TEACHER && !(user instanceof TeacherEntity)) {
			// Create new teacher and transfer ID + data
			TeacherEntity teacher = new TeacherEntity();
			BeanUtils.copyProperties(user, teacher);
			teacher.setRole(Role.TEACHER);
			urepo.save(teacher);
			return;
		}
		else if (newRole == Role.STUDENT && !(user instanceof StudentEntity)) {
			// Create new student and transfer ID + data
			StudentEntity student = new StudentEntity();
			BeanUtils.copyProperties(user, student);
			student.setRole(Role.STUDENT);
			urepo.save(student);
			return;
		}

		// If just updating role without changing entity type
		user.setRole(newRole);
		urepo.save(user);
	}

	public UserEntity updateUser(UserEntity user) {
		return urepo.save(user);
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

	public UserEntity findOrCreateFromAzure(User azureUser) {
		String azureId = azureUser.getId();
		String email = azureUser.getMail() != null ? azureUser.getMail() : azureUser.getUserPrincipalName();

		// Check if user exists by Azure ID
		Optional<UserEntity> existingByAzureId = urepo.findByAzureId(azureId);
		if (existingByAzureId.isPresent()) {
			UserEntity user = existingByAzureId.get();
			// Update user info if needed
			user.setEmail(email);

			return urepo.save(user);
		}

		// Check if user exists by email (manual registration)
		Optional<UserEntity> existingByEmail = Optional.ofNullable(urepo.findByEmail(email));
		if (existingByEmail.isPresent()) {
			// Link Azure account to existing manual account
			UserEntity user = existingByEmail.get();
			user.setAzureId(azureId);
			return urepo.save(user);
		}

		// Create new Azure user
		UserEntity newUser = new UserEntity();
		newUser.setEmail(email);
		newUser.setAzureId(azureId);
		newUser.setFirstName(azureUser.getGivenName());
		newUser.setLastName(azureUser.getSurname());
		newUser.setRole(Role.PENDING);
		newUser.setProvider("Microsoft");
		// Password is null for Azure users
		return urepo.save(newUser);
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

	private UserEntity handleRoleConversion(int userId, UserUpdateRequest request) {
		Role targetRole = request.getRole();
		String currentUserType = userRepository.getUserType(userId);

		// Check if user can be converted (not already a teacher or student)
		if (!"PENDING".equals(currentUserType)) {
			throw new IllegalStateException("User already has a role: " + currentUserType);
		}

		switch (targetRole) {
			case TEACHER:
				return convertToTeacher(userId, request);
			// case STUDENT:
			//    return convertToStudent(userId, request);
			default:
				throw new IllegalArgumentException("Invalid role for conversion: " + targetRole);
		}
	}

	private UserEntity convertToTeacher(int userId, UserUpdateRequest request) {
		// Validate required fields
		if (request.getInstitution() == null || request.getDepartment() == null) {
			throw new IllegalArgumentException("Institution and department are required for teacher role");
		}

		// Update role in users table
		int updatedRows = userRepository.updateUserRoleToTeacher(userId);
		if (updatedRows == 0) {
			throw new EntityNotFoundException("User with ID " + userId + " not found");
		}

		// Create teacher record
		teacherRepository.createTeacherRecord(userId, request.getInstitution(), request.getDepartment());

		// Verify using count instead of findByUserId
		int teacherRecordCount = teacherRepository.teacherRecordExists(userId);
		if (teacherRecordCount == 0) {
			throw new EntityNotFoundException("Teacher entity not created properly");
		}

		// Return the updated UserEntity
		return findById(userId);
	}



//	private StudentEntity convertToStudent(int userId, UserUpdateRequest request) {
//		// Validate required fields
//		if (request.getStudentNumber() == null || request.getMajor() == null ||
//				request.getYearLevel() == null || request.getInstitution() == null) {
//			throw new IllegalArgumentException("Student number, major, year level, and institution are required for student role");
//		}
//
//		// Check if student number is unique
//		if (studentRepository.isStudentNumberTaken(request.getStudentNumber(), userId) > 0) {
//			throw new IllegalArgumentException("Student number already exists");
//		}
//
//		// Update role in users table
//		int updatedRows = userRepository.updateUserRoleToStudent(userId);
//		if (updatedRows == 0) {
//			throw new EntityNotFoundException("User with ID " + userId + " not found");
//		}
//
//		// Create student record
//		studentRepository.createStudentRecord(userId, request.getStudentNumber(),
//				request.getMajor(), request.getYearLevel(),
//				request.getInstitution());
//
//		// Return the complete student entity
//		return studentRepository.findById(userId)
//				.orElseThrow(() -> new EntityNotFoundException("Student entity not created properly"));
//	}
}