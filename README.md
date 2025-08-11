
# Gradify Backend

Gradify is a comprehensive backend system for managing academic records, grades, and notifications, built with Spring Boot. It provides robust APIs for handling user authentication, grade management, reporting, and real-time notifications, integrating with modern cloud and productivity services.

---

## Table of Contents
- [Purpose](#purpose)
- [Key Features](#key-features)
- [Technologies & Integrations](#technologies--integrations)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

---

## Purpose

Gradify aims to streamline the management of academic data for educational institutions, teachers, and students. It automates grade processing, reporting, and notification delivery, ensuring secure and efficient handling of sensitive information.

---

## Key Features

- **User Authentication & Authorization**: Secure login and role-based access using JWT.
- **Grade Management**: CRUD operations for classes, students, and grades.
- **Automated Reporting**: Generate and deliver academic reports, including AI-generated insights.
- **Spreadsheet Integration**: Import/export grades and records via Microsoft Excel/Graph API.
- **Notification System**: Real-time notifications using Firebase Cloud Messaging (FCM).
- **Email Services**: Automated email notifications for feedback and verification.
- **Analytics**: Insights and analytics on academic records.

---

## Technologies & Integrations

- **Spring Boot** (Java)
- **Spring Security** (JWT-based authentication)
- **Spring Data JPA** (Database access)
- **Thymeleaf** (Email templating)
- **Microsoft Graph API** (Excel/OneDrive integration)
- **Firebase Cloud Messaging (FCM)** (Push notifications)
- **Azure** (Cloud services integration)
- **Lombok** (Boilerplate code reduction)
- **Maven** (Build tool)

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) Microsoft Azure and Firebase accounts for integrations

### Setup Steps

1. **Clone the repository:**
	 ```sh
	 git clone https://github.com/Ban-Coca/Gradify-Backend.git
	 cd Gradify-Backend
	 ```

2. **Configure application properties:**
	 - Copy `src/main/resources/application.properties.example` (if available) to `application.properties`.
	 - Set up the following properties:
		 - Database connection (e.g., `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`)
		 - JWT secret and expiration
		 - Firebase credentials path
		 - Microsoft Graph API credentials
		 - Email SMTP settings

3. **Build the project:**
	 ```sh
	 mvn clean install
	 ```

4. **Run the application:**
	 ```sh
	 mvn spring-boot:run
	 ```
	 The server will start on `http://localhost:8080` by default.

---

## Configuration

Edit `src/main/resources/application.properties` to set up:

- **Database**: MySQL/PostgreSQL or your preferred RDBMS
- **JWT**: Secret key and expiration
- **Firebase**: Service account JSON path
- **Microsoft Graph**: Client ID, secret, and tenant info
- **Email**: SMTP host, port, username, and password

---

## Project Structure

```
src/
	main/
		java/com/capstone/gradify/
			Application.java
			Config/           # Configuration classes (Security, Azure, Firebase, etc.)
			Controller/       # REST controllers (API endpoints)
			dto/              # Data Transfer Objects
			Entity/           # JPA Entities
			mapper/           # MapStruct mappers
			Repository/       # Spring Data repositories
			Service/          # Business logic and integrations
			util/             # Utility classes
		resources/
			application.properties
			templates/        # Thymeleaf email templates
	test/
		java/com/capstone/gradify/
			ApplicationTests.java
```

---

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request. For major changes, open an issue first to discuss your ideas.

---

## License

This project is licensed under the MIT License.
