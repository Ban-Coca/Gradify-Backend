package com.capstone.gradify.Config;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.userservice.UserService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private UserService userService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {


        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF protection
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/login", "api/user/reset-password", "/api/user/postuserrecord",
                                "/api/user/verify-email", "/api/user/request-password-reset", "/api/user/verify-reset-code").permitAll()
                        .requestMatchers("/api/teacher/**", "/api/spreadsheet/**", "/api/class/**", "/api/grading/**").hasAnyAuthority("TEACHER")
                        .requestMatchers("/api/student/**").hasAnyAuthority("STUDENT")
                        .requestMatchers("/api/reports/**").hasAnyAuthority("TEACHER", "STUDENT")
                        .requestMatchers(
                                "/api/reports/teacher/**",
                                "/api/reports/class/**"
                        ).hasAuthority("TEACHER")
                        .requestMatchers("/api/user/update-profile", "/api/user/update-role", "/api/user/getuserdetails/").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(authorization -> authorization
                        .baseUri("/oauth2/authorization") // Custom base URI for OAuth2 authorization
                    )
                    .successHandler((request, response, authentication) -> {
                        // Custom success handler to process user registration
                        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
                        String email = oAuth2User.getAttribute("email");
                        String firstName = oAuth2User.getAttribute("given_name");
                        String lastName = oAuth2User.getAttribute("family_name");

                        String provider = request.getRequestURI().contains("google") ? "Google" : "Microsoft";

                        UserEntity user = userService.findByEmail(email);
                        if (user == null) {
                            // Create new user if not exists
                            user = new UserEntity();
                            user.setEmail(email);
                            user.setFirstName(firstName);
                            user.setLastName(lastName);
                            user.setRole(Role.STUDENT); // Default role
                            user.setIsActive(true);
                            user.setCreatedAt(new Date());
                            user.setProvider(provider);
                        }

                        // Update last login
                        user.setLastLogin(new Date());
                        userService.postUserRecord(user);

                        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
                        String token = Jwts.builder()
                                .setSubject(email)
                                .claim("firstName", firstName)
                                .claim("lastName", lastName)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour expiration
                                .signWith(key, SignatureAlgorithm.HS512) // Replace with your secret
                                .compact();

                        // Prepare the response body
                        String responseBody = String.format(
                                "{\"token\":\"%s\",\"user\":{\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\"}}",
                                token, email, firstName, lastName);

                        // Set response headers and body
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(responseBody);
                    })
                    .failureUrl("/api/user/oauth2/failure") // Redirect after failed login
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // Add allowed origins
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Add allowed HTTP methods
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type")); // Add allowed headers
        configuration.setAllowCredentials(true); // Allow credentials (e.g., cookies)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS settings to all endpoints
        return source;
    }
}