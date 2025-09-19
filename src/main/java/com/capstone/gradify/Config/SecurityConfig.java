package com.capstone.gradify.Config;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.Entity.user.UserToken;
import com.capstone.gradify.Repository.user.UserTokenRepository;
import com.capstone.gradify.Service.userservice.TempTokenService;
import com.capstone.gradify.Service.userservice.UserService;
import com.capstone.gradify.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserTokenRepository userTokenRepository;
    private final TempTokenService tempTokenService;
    @Value("${frontend.base-url}")
    private String frontendBaseUrl;
    @Value("${spring.web.cors.allowed-origins}")
    private String allowedOrigins;
    @Value("${spring.web.cors.allowed-methods}")
    private String allowedMethods;
    @Value("${spring.web.cors.allowed-headers}")
    private String allowedHeaders;
    @Value("${spring.web.cors.allow-credentials}")
    private boolean allowCredentials;

    private String serializeUser(UserEntity user) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", user.getUserId());
            map.put("email", user.getEmail());
            map.put("firstName", user.getFirstName());
            map.put("lastName", user.getLastName());
            map.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            map.put("lastLogin", user.getLastLogin() != null ? user.getLastLogin().toString() : null);
            map.put("role", user.getRole() != null ? user.getRole().name() : null);
            map.put("provider", user.getProvider());
            map.put("isActive", user.isActive());

            map.put("phoneNumber", user.getPhoneNumber());
            map.put("bio", user.getBio());
            map.put("profilePictureUrl", user.getProfilePictureUrl());

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(map);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize user", e);
            return "{}";
        }
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
                                "/api/user/verify-email", "/api/user/request-password-reset", "/api/user/verify-reset-code", "/api/user/oauth2/callback/google", "/api/user/email-exists", "/api/user/resend-code").permitAll()
                        .requestMatchers("/api/auth/**", "/api/google/**").permitAll()
                        .requestMatchers("/api/graph/notification/**").authenticated()
                        .requestMatchers("/api/graph/drive/folder/**", "/api/graph/subscription","/api/graph/subscription/**", "/api/graph/tracked-files", "/api/graph/sync-excel-sheet").hasAnyAuthority("TEACHER")
                        .requestMatchers("/api/teacher/**", "/api/spreadsheet/**", "/api/classes/**", "/api/grading/**", "/api/graph/drive/**", "/api/graph/extract/**", "/api/graph/save/**").hasAnyAuthority("TEACHER")
                        .requestMatchers("/api/student/**").hasAnyAuthority("STUDENT", "TEACHER")
                        .requestMatchers("/api/reports/**").hasAnyAuthority("TEACHER", "STUDENT")
                        .requestMatchers(
                                "/api/reports/teacher/**",
                                "/api/reports/class/**"
                        ).hasAuthority("TEACHER")
                        .requestMatchers("/api/user/update-profile", "/api/user/update-role", "/api/user/{userId}", "/api/notification/**", "/api/fcm/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization") // This creates /oauth2/authorization/google
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/oauth2/callback/*") // This handles /oauth2/callback/google
                        )
                        .successHandler(this::handleOAuth2Success)
                        .failureHandler(this::handleOAuth2Failure)
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of(allowedMethods.split(",")));
        configuration.setAllowedHeaders(List.of(allowedHeaders.split(",")));
        configuration.setAllowCredentials(allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS settings to all endpoints
        return source;
    }

    private void handleOAuth2Success(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Authentication authentication) throws IOException {
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = token.getPrincipal();
            String email = oauth2User.getAttribute("email");

            // Get the OAuth2AuthorizedClient to access tokens
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    token.getAuthorizedClientRegistrationId(), token.getName());

            UserEntity existingUser = userService.findByEmail(email);
            logger.info("User found with email: {}", email);

            if (existingUser == null) {
                // Store temporary tokens for new users during onboarding
                tempTokenService.storeTokens(email, authorizedClient);

                String firstName = oauth2User.getAttribute("given_name");
                String lastName = oauth2User.getAttribute("family_name");

                logger.debug("Redirecting to frontend for onboarding");
                response.sendRedirect(String.format("%s/oauth2/callback?onboardingRequired=true&firstName=%s&lastName=%s&email=%s",
                        frontendBaseUrl, firstName, lastName, email));
            } else {
                // Save Google OAuth tokens for existing users
                saveGoogleTokens(existingUser, authorizedClient);

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
    }

    private void handleOAuth2Failure(HttpServletRequest request,
                                     HttpServletResponse response,
                                     AuthenticationException exception) throws IOException {
        logger.error("OAuth2 authentication failed", exception);
        response.sendRedirect(frontendBaseUrl + "/login?error=oauth_failed");
    }

    private void saveGoogleTokens(UserEntity user, OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

            // Check if tokens already exist for this user
            UserToken existingToken = userTokenRepository.findByUserId(user.getUserId()).orElse(null);

            if (existingToken != null) {
                // Update existing tokens
                existingToken.setAccessToken(accessToken.getTokenValue());
                if (refreshToken != null) {
                    existingToken.setRefreshToken(refreshToken.getTokenValue());
                }
                existingToken.setExpiresAt(accessToken.getExpiresAt() != null ?
                        accessToken.getExpiresAt().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
                userTokenRepository.save(existingToken);
            } else {
                // Create new token record
                UserToken userToken = new UserToken();
                userToken.setUserId(user.getUserId());
                userToken.setAccessToken(accessToken.getTokenValue());
                if (refreshToken != null) {
                    userToken.setRefreshToken(refreshToken.getTokenValue());
                }
                userToken.setExpiresAt(accessToken.getExpiresAt() != null ?
                        accessToken.getExpiresAt().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
                userToken.setCreatedAt(LocalDateTime.now());
                logger.debug("Saving user token: {}", userToken);
                userTokenRepository.save(userToken);
            }

            logger.info("Google OAuth tokens saved for user: {}", user.getEmail());
        }
    }

}