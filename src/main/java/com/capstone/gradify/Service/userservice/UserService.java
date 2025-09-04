package com.capstone.gradify.Service.userservice;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.StudentEntity;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Repository.user.StudentRepository;
import com.capstone.gradify.Repository.user.TeacherRepository;
import com.capstone.gradify.dto.request.RegisterRequest;
import com.capstone.gradify.dto.request.UserUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.microsoft.graph.models.User;
import org.springframework.http.MediaType;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Repository.user.UserRepository;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final TeacherRepository teacherRepository;
	private final StudentRepository studentRepository;


  	// Directory for file uploads, injected from application properties
  	// This is used to store user profile pictures or other uploaded files
	@Value("${app.upload.dir}")
  	private String uploadDir;

	public UserEntity findByEmail(String email) {
		return userRepository.findByEmail(email);
	}
	
	//Create of CRUD
	public UserEntity postUserRecord(UserEntity user) {
		if (user.getRole() == Role.PENDING) {
			return userRepository.save(user);
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

				// Additional check: If updating an existing student, verify it's the same user
				if (!user.getEmail().equals(student.getEmail())) {
					boolean isPlaceholderEmail = student.getEmail() != null &&
							student.getEmail().endsWith("@temp.edu") &&
							student.getPassword() != null &&
							student.getPassword().equals("PLACEHOLDER");

					if (!isPlaceholderEmail) {
						throw new IllegalArgumentException(
								"This student number is already registered to a different email address. " +
										"Please contact support if this is your student number."
						);
					}
				}

				if (user.getUserId() > 0 && user.getUserId() != student.getUserId()) {
					throw new IllegalArgumentException("Cannot update student record belonging to different user");
				}

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
			return userRepository.save(user);
		}
	}
	public boolean isEmailAvailable(String email){
		UserEntity existingUser = userRepository.findByEmail(email);
        return existingUser == null;
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
		target.setActive(source.isActive());
		target.setProvider(source.getProvider());
		target.setCreatedAt(source.getCreatedAt());
		target.setLastLogin(source.getLastLogin());
		target.setFailedLoginAttempts(source.getFailedLoginAttempts());
	}

	//find by ID
	public UserEntity findById(int userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new NoSuchElementException("User with ID " + userId + " not found"));
	}
	
	//Read of CRUD
	public List<UserEntity> getAllUsers(){
		return userRepository.findAll();
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
			userRepository.save(teacher);
			return;
		}
		else if (newRole == Role.STUDENT && !(user instanceof StudentEntity)) {
			// Create new student and transfer ID + data
			StudentEntity student = new StudentEntity();
			BeanUtils.copyProperties(user, student);
			student.setRole(Role.STUDENT);
			userRepository.save(student);
			return;
		}

		// If just updating role without changing entity type
		user.setRole(newRole);
		userRepository.save(user);
	}

	public UserEntity updateUser(UserEntity user) {
		return userRepository.save(user);
	}

	//Delete of CRUD
	public String deleteUser(int userId) {
		String msg = "";
		
		if(userRepository.findById(userId).isPresent()) {
			userRepository.deleteById(userId);
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
		Optional<UserEntity> existingByAzureId = userRepository.findByAzureId(azureId);
		if (existingByAzureId.isPresent()) {
			UserEntity user = existingByAzureId.get();
			// Update user info if needed
			user.setEmail(email);
			if(!user.isActive()){
				user.setActive(true); // Reactivate if previously inactive
			}
			return userRepository.save(user);
		}

		// Check if user exists by email (manual registration)
		Optional<UserEntity> existingByEmail = Optional.ofNullable(userRepository.findByEmail(email));
		if (existingByEmail.isPresent()) {
			// Link Azure account to existing manual account
			UserEntity user = existingByEmail.get();
			user.setAzureId(azureId);
			return userRepository.save(user);
		}

		// Create new Azure user
		UserEntity newUser = new UserEntity();
		newUser.setEmail(email);
		newUser.setAzureId(azureId);
		newUser.setFirstName(azureUser.getGivenName());
		newUser.setLastName(azureUser.getSurname());
		newUser.setRole(Role.PENDING);
		newUser.setActive(true); // Activate new users by default
		newUser.setCreatedAt(new Date());
		newUser.setProvider("Microsoft");
		// Password is null for Azure users
		return userRepository.save(newUser);
	}

    public TeacherEntity createTeacherFromOAuth(RegisterRequest request){
        TeacherEntity newUser = new TeacherEntity();
        newUser.setEmail(request.getEmail());
        newUser.setAzureId(request.getAzureId());
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setRole(Role.TEACHER);
        newUser.setActive(true);
        newUser.setCreatedAt(new Date());
        newUser.setProvider("Microsoft");

		newUser.setDepartment(request.getDepartment());
		newUser.setInstitution(request.getInstitution());
        // Password is null for Azure users
        return teacherRepository.save(newUser);
    }

	public StudentEntity createStudentFromOAuth(RegisterRequest request){
		Optional<StudentEntity> existingStudent = studentRepository.findByStudentNumber(request.getStudentNumber());
		// If student already exists, update their information
		if (existingStudent.isPresent()) {
			StudentEntity student = existingStudent.get();
			student.setEmail(request.getEmail());
			student.setAzureId(request.getAzureId());
			student.setFirstName(request.getFirstName());
			student.setLastName(request.getLastName());
			student.setRole(Role.STUDENT);
			student.setActive(true);
			student.setCreatedAt(new Date());
			student.setProvider("Microsoft");
			student.setMajor(request.getMajor());
			student.setYearLevel(request.getYearLevel());
			student.setStudentNumber(request.getStudentNumber());

			return studentRepository.save(student);
		}

		// If student does not exist, create a new one
		StudentEntity newUser = new StudentEntity();
		newUser.setEmail(request.getEmail());
		newUser.setAzureId(request.getAzureId());
		newUser.setFirstName(request.getFirstName());
		newUser.setLastName(request.getLastName());
		newUser.setRole(Role.STUDENT);
		newUser.setActive(true);
		newUser.setCreatedAt(new Date());
		newUser.setProvider("Microsoft");

		newUser.setMajor(request.getMajor());
		newUser.setYearLevel(request.getYearLevel());
		newUser.setStudentNumber(request.getStudentNumber());

		return studentRepository.save(newUser);
	}
    public Optional<UserEntity> findByAzureId(String azureId) {
        return userRepository.findByAzureId(azureId);
    }
	public UserEntity findOrCreateUserFromGoogle(OAuth2User googleUser) {
		String email = googleUser.getAttribute("email");
		String firstName = googleUser.getAttribute("given_name");
		String lastName = googleUser.getAttribute("family_name");
		String provider = "Google";

		// Try to find existing user
		UserEntity existingUser = findByEmail(email);

		if (existingUser != null) {
			// Update last login for existing user
			existingUser.setLastLogin(new Date());
			return postUserRecord(existingUser);
		} else {
			// Create new user
			UserEntity newUser = new UserEntity();
			newUser.setEmail(email);
			newUser.setFirstName(firstName);
			newUser.setLastName(lastName);
			newUser.setRole(Role.PENDING); // Default role
			newUser.setActive(true);
			newUser.setCreatedAt(new Date());
			newUser.setLastLogin(new Date());
			newUser.setProvider("Google");

			return postUserRecord(newUser);
		}
	}

	@Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
    public void deactivateInactiveUsers() {
        List<UserEntity> users = userRepository.findAll();
        Date now = new Date();

        for (UserEntity user : users) {
            if (user.getLastLogin() != null) {
                long diffInMillis = now.getTime() - user.getLastLogin().getTime();
                long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

                if (diffInDays > 30 && user.isActive()) {
                    user.setActive(false);
                    userRepository.save(user);
                }
            }
        }
    }

	private UserEntity handleRoleConversion(int userId, UserUpdateRequest request) {
		Role targetRole = request.getRole();
		String currentUserType = userRepository.getUserType(userId);

		// Check if user can be converted (not already a teacher or student)
		if (!Role.PENDING.name().equals(currentUserType)) {
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