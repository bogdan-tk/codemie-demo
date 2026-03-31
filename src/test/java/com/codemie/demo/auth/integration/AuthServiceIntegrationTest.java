package com.codemie.demo.auth.integration;

import com.codemie.demo.auth.dto.AuthRequest;
import com.codemie.demo.auth.dto.AuthResponse;
import com.codemie.demo.auth.dto.ValidationRequest;
import com.codemie.demo.auth.dto.ValidationResponse;
import com.codemie.demo.auth.model.Role;
import com.codemie.demo.auth.model.User;
import com.codemie.demo.auth.repository.RoleRepository;
import com.codemie.demo.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthService end-to-end authentication flow.
 * 
 * Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
 * 
 * This test class verifies:
 * - Complete authentication flow from credential submission to token validation
 * - Integration between AuthService and API Gateway
 * - Database persistence of users and roles
 * - Real JWT token generation and validation
 * - Role-based authorization checks
 * 
 * @author Test Automation Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthService - End-to-End Integration Tests")
class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        // Clean up
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create roles
        userRole = roleRepository.save(Role.builder().name("ROLE_USER").build());
        adminRole = roleRepository.save(Role.builder().name("ROLE_ADMIN").build());

        // Create test user
        testUser = User.builder()
                .username("integrationuser")
                .email("integration@example.com")
                .password(passwordEncoder.encode("testPassword123"))
                .roles(Set.of(userRole, adminRole))
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should complete full authentication flow: login -> token -> validation")
    void shouldCompleteFullAuthenticationFlow() throws Exception {
        // Step 1: Client submits credentials to AuthService
        AuthRequest authRequest = AuthRequest.builder()
                .username("integrationuser")
                .password("testPassword123")
                .build();

        MvcResult authResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andReturn();

        String authResponseJson = authResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(authResponseJson, AuthResponse.class);
        String token = authResponse.getToken();

        assertThat(token).isNotBlank();

        // Step 2: API Gateway validates JWT via AuthService
        ValidationRequest validationRequest = ValidationRequest.builder()
                .token(token)
                .build();

        MvcResult validationResult = mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.roles").isArray())
                .andReturn();

        String validationResponseJson = validationResult.getResponse().getContentAsString();
        ValidationResponse validationResponse = objectMapper.readValue(validationResponseJson, ValidationResponse.class);

        // Step 3: Verify downstream services can use role information
        assertThat(validationResponse.getRoles())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(validationResponse.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(validationResponse.hasRole("ROLE_USER")).isTrue();
    }

    @Test
    @DisplayName("Should reject authentication with invalid credentials")
    void shouldRejectAuthenticationWithInvalidCredentials() throws Exception {
        // Given
        AuthRequest invalidRequest = AuthRequest.builder()
                .username("integrationuser")
                .password("wrongPassword")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Should reject validation with invalid token")
    void shouldRejectValidationWithInvalidToken() throws Exception {
        // Given
        ValidationRequest invalidRequest = ValidationRequest.builder()
                .token("invalid.jwt.token")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("Should support API Gateway edge authentication")
    void shouldSupportApiGatewayEdgeAuthentication() throws Exception {
        // Given - Authenticate user
        AuthRequest authRequest = AuthRequest.builder()
                .username("integrationuser")
                .password("testPassword123")
                .build();

        MvcResult authResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // When - Gateway validates token with role requirement
        ValidationRequest gatewayValidation = ValidationRequest.builder()
                .token(authResponse.getToken())
                .requiredRole("ROLE_ADMIN")
                .build();

        // Then
        mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gatewayValidation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Should reject gateway validation when required role is missing")
    void shouldRejectGatewayValidationWhenRequiredRoleIsMissing() throws Exception {
        // Given - Create user with only USER role
        User limitedUser = User.builder()
                .username("limiteduser")
                .email("limited@example.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(userRole))
                .enabled(true)
                .build();
        userRepository.save(limitedUser);

        AuthRequest authRequest = AuthRequest.builder()
                .username("limiteduser")
                .password("password")
                .build();

        MvcResult authResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // When - Gateway requires ADMIN role
        ValidationRequest gatewayValidation = ValidationRequest.builder()
                .token(authResponse.getToken())
                .requiredRole("ROLE_ADMIN")
                .build();

        // Then
        mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gatewayValidation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("required role")));
    }

    @Test
    @DisplayName("Should handle multiple concurrent authentication requests")
    void shouldHandleMultipleConcurrentAuthenticationRequests() throws Exception {
        // Given
        AuthRequest request1 = AuthRequest.builder()
                .username("integrationuser")
                .password("testPassword123")
                .build();

        // When - Multiple concurrent requests
        MvcResult result1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn();

        // Then - Both should succeed with different tokens
        AuthResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(),
                AuthResponse.class
        );
        AuthResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(),
                AuthResponse.class
        );

        assertThat(response1.getToken()).isNotEqualTo(response2.getToken());

        // Both tokens should be valid
        ValidationRequest validation1 = ValidationRequest.builder()
                .token(response1.getToken())
                .build();
        ValidationRequest validation2 = ValidationRequest.builder()
                .token(response2.getToken())
                .build();

        mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validation1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validation2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("Should include all required claims for downstream authorization")
    void shouldIncludeAllRequiredClaimsForDownstreamAuthorization() throws Exception {
        // Given
        AuthRequest authRequest = AuthRequest.builder()
                .username("integrationuser")
                .password("testPassword123")
                .build();

        MvcResult authResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // When
        ValidationRequest validationRequest = ValidationRequest.builder()
                .token(authResponse.getToken())
                .build();

        MvcResult validationResult = mockMvc.perform(post("/api/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andReturn();

        ValidationResponse validationResponse = objectMapper.readValue(
                validationResult.getResponse().getContentAsString(),
                ValidationResponse.class
        );

        // Then - Verify all required claims for downstream services
        assertThat(validationResponse.getUserId()).isNotNull();
        assertThat(validationResponse.getUsername()).isEqualTo("integrationuser");
        assertThat(validationResponse.getEmail()).isEqualTo("integration@example.com");
        assertThat(validationResponse.getRoles()).isNotEmpty();
        assertThat(validationResponse.getIssuer()).isNotBlank();
        assertThat(validationResponse.getIssuedAt()).isNotNull();
        assertThat(validationResponse.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject disabled user authentication")
    void shouldRejectDisabledUserAuthentication() throws Exception {
        // Given - Disable user
        testUser.setEnabled(false);
        userRepository.save(testUser);

        AuthRequest authRequest = AuthRequest.builder()
                .username("integrationuser")
                .password("testPassword123")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("disabled")));
    }
}
