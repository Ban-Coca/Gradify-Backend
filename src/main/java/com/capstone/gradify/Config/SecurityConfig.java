package com.capstone.gradify.Config;

import com.capstone.gradify.Entity.user.Role;
import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.util.JwtUtil;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
@RequiredArgsConstructor
public class SecurityConfig {
    private final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserService userService;
    private final JwtUtil jwtUtil;
    @Value("${frontend.base-url}")
    private String frontendBaseUrl;
    private String serializeUser(UserEntity user) {
        return String.format("{\"userId\":%d,\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"%s\"}",
                user.getUserId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole().name());
    }
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF protection
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/user/login", "api/user/reset-password", "/api/user/register",
                                "/api/user/verify-email", "/api/user/request-password-reset", "/api/user/verify-reset-code", "/api/user/oauth2/callback/google").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/graph/**").permitAll()
                        .requestMatchers("/api/teacher/**", "/api/spreadsheet/**", "/api/classes/**", "/api/grading/**").hasAnyAuthority("TEACHER")
                        .requestMatchers("/api/student/**").hasAnyAuthority("STUDENT")
                        .requestMatchers("/api/reports/**").hasAnyAuthority("TEACHER", "STUDENT")
                        .requestMatchers(
                                "/api/reports/teacher/**",
                                "/api/reports/class/**"
                        ).hasAuthority("TEACHER")
                        .requestMatchers("/api/user/update-profile", "/api/user/update-role", "/api/user/getuserdetails/", "/api/notification/**", "/api/fcm/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization") // This creates /oauth2/authorization/google
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/oauth2/callback/*") // This handles /oauth2/callback/google
                        )
                        .successHandler((request, response, authentication) -> {
                            try {
                                OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
                                OAuth2User oauth2User = token.getPrincipal();
                                String email = oauth2User.getAttribute("email");
                                UserEntity existingUser = userService.findByEmail(email);
                                logger.info(String.format("User found with email: %s", email));
                                if(existingUser == null) {
                                    String firstName = oauth2User.getAttribute("given_name");
                                    String lastName = oauth2User.getAttribute("family_name");

                                    logger.debug("Redirecting to frontend for onboarding: {}/oauth2/callback?onboardingRequired=true&firstName={}&lastName={}&email={}", frontendBaseUrl, firstName, lastName, email);

                                    response.sendRedirect(String.format("%s/oauth2/callback?onboardingRequired=true&firstName=%s&lastName=%s&email=%s",
                                            frontendBaseUrl, firstName, lastName, email));

                                }
                                // if user is not null, it means the user already exists
                                else {

                                    // Generate JWT
                                    String jwtToken = jwtUtil.generateToken(existingUser);

                                    // Redirect to frontend with token
                                    String serializedUser = serializeUser(existingUser);
                                    String encodedToken = URLEncoder.encode(jwtToken, StandardCharsets.UTF_8);
                                    String encodedUser = URLEncoder.encode(serializedUser, StandardCharsets.UTF_8);

                                    response.sendRedirect(String.format("%s/oauth2/callback?onboardingRequired=false&token=%s&user=%s",
                                            frontendBaseUrl, encodedToken, encodedUser));
                                }

                            } catch (Exception e) {
                                logger.error("OAuth2 success handler error", e);
                                response.sendRedirect(frontendBaseUrl + "/login?error=oauth_processing_failed");
                            }
                        })
                        .failureHandler((request, response, exception) -> {
                            logger.error("OAuth2 authentication failed", exception);
                            response.sendRedirect(frontendBaseUrl + "/login?error=oauth_failed");
                        })
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