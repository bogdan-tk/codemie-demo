package com.microservices.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AuthService JWT token issuance and validation.
 *
 * Test Case ID: EPMCDMETST-36039
 * Test Scenarios:
 * 1. Client submits credentials to AuthService
 * 2. AuthService issues JWT token
 * 3. API Gateway uses AuthService validation to authenticate requests
 * 4. Internal services validate user roles based on JWT claims
 *
 * @author Automated Test Generator
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class AuthServiceIntegrationTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0-alpine")
            .withExposedPorts(6379)
            .withReuse(true);
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private TestDataBuilder testDataBuilder;
    
    @BeforeEach
    void setUp() {
        testDataBuilder = new TestDataBuilder();
    }

    /**
     * Test Scenario 1: Client submits valid credentials to AuthService
     * Expected: JWT token is issued with roles in claims
     */
    @Nested
    @DisplayName("JWT Token Issuance Tests")
    class TokenIssuanceTests {
        
        @Test
        @DisplayName("Should issue JWT token with valid credentials")
        void shouldIssueJWTTokenWithValidCredentials() throws Exception {
            // Given
            Map<String, String> loginRequest = testDataBuilder.buildValidLoginRequest(
                    "testuser@example.com", 
                    "ValidPassword123!"
            );
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
        }
        
        @Test
        @DisplayName("Should return 401 with invalid credentials")
        void shouldReturnUnauthorizedWithInvalidCredentials() throws Exception {
            // Given
            Map<String, String> invalidRequest = testDataBuilder.buildInvalidLoginRequest(
                    "testuser@example.com", 
                    "wrongpassword"
            );
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }
        
        @Test
        @DisplayName("Should include user roles in JWT claims")
        void shouldIncludeUserRolesInJWTClaims() throws Exception {
            // Given
            Map<String, String> adminLogin = testDataBuilder.buildValidLoginRequest(
                    "admin@example.com", 
                    "AdminPassword123!"
            );
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles").isArray())
                .andExpect(jsonPath("$.user.roles", hasSize(notNullValue())));
        }
        
        @Test
        @DisplayName("Should return 400 with missing credentials")
        void shouldReturnBadRequestWithMissingCredentials() throws Exception {
            // Given
            Map<String, String> incompleteRequest = Map.of("username", "test@test.com");
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(incompleteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        }
    }
    
    /**
     * Test Scenario 3: API Gateway uses AuthService validation
     * Expected: Token validation endpoint validates JWT tokens
     */
    @Nested
    @DisplayName("JWT Token Validation Tests")
    class TokenValidationTests {
        
        @Test
        @DisplayName("Should validate valid JWT token")
        void shouldValidateValidJWTToken() throws Exception {
            // Given - First login to get a token
            Map<String, String> loginRequest = testDataBuilder.buildValidLoginRequest(
                    "testuser@example.com", 
                    "ValidPassword123!"
            );
            
            String response = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
                
            String token = objectMapper.readTree(response).get("accessToken").asText();
            
            // When & Then - Validate the token
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.roles").isArray());
        }
        
        @Test
        @DisplayName("Should reject invalid JWT token")
        void shouldRejectInvalidJWTToken() throws Exception {
            // Given
            String invalidToken = "invalid.jwt.token";
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", invalidToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        }
        
        @Test
        @DisplayName("Should reject expired JWT token")
        void shouldRejectExpiredJWTToken() throws Exception {
            // Given - Create an expired token
            String expiredToken = testDataBuilder.buildExpiredToken();
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", expiredToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").value("Token expired"));
        }
    }
    
    /**
     * Test Scenario 4: Internal services validate user roles
     * Expected: JWT contains role information for authorization checks
     */
    @Nested
    @DisplayName("Role-Based Authorization Tests")
    class RoleBasedAuthorizationTests {
        
        @Test
        @DisplayName("Should extract roles from JWT claims")
        void shouldExtractRolesFromJWTClaims() throws Exception {
            // Given - Login with multiple roles
            Map<String, String> loginRequest = testDataBuilder.buildValidLoginRequest(
                    "admin@example.com", 
                    "AdminPassword123!"
            );
            
            String response = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
                
            String token = objectMapper.readTree(response).get("accessToken").asText();
            
            // When & Then - Validate and check roles
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[*]").isNotEmpty());
        }
        
        @Test
        @DisplayName("Should validate ADMIN role for privileged operations")
        void shouldValidateAdminRoleForPrivilegedOperations() throws Exception {
            // Given
            Map<String, String> adminLogin = testDataBuilder.buildValidLoginRequest(
                    "admin@example.com", 
                    "AdminPassword123!"
            );
            
            String response = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
                
            String token = objectMapper.readTree(response).get("accessToken").asText();
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roles[\"ADMIN\"]").exists());
        }
        
        @Test
        @DisplayName("Should validate USER role for basic operations")
        void shouldValidateUserRoleForBasicOperations() throws Exception {
            // Given
            Map<String, String> userLogin = testDataBuilder.buildValidLoginRequest(
                    "testuser@example.com", 
                    "ValidPassword123!"
            );
            
            String response = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
                
            String token = objectMapper.readTree(response).get("accessToken").asText();
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roles[\"USER\"]").exists());
        }
    }
    
    /**
     * Refresh token functionality tests
     */
    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {
        
        @Test
        @DisplayName("Should refresh access token with valid refresh token")
        void shouldRefreshAccessTokenWithValidRefreshToken() throws Exception {
            // Given - First login to get refresh token
            Map<String, String> loginRequest = testDataBuilder.buildValidLoginRequest(
                    "testuser@example.com", 
                    "ValidPassword123!"
            );
            
            String response = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
                
            String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();
            
            // When & Then - Refresh the token
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }
        
        @Test
        @DisplayName("Should reject invalid refresh token")
        void shouldRejectInvalidRefreshToken() throws Exception {
            // Given
            String invalidRefreshToken = "invalid.refresh.token";
            
            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", invalidRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
        }
    }
}
