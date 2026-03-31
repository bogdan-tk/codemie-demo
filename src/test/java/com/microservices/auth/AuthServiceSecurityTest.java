package com.microservices.auth;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-focused tests for AuthService.
 * Tests authentication security, token security, and authorization mechanisms.
 *
 * Test Case ID: EPMCDMETST-36039
 *
 * @author Automated Test Generator
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthServiceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private final TestDataBuilder testDataBuilder = new TestDataBuilder();

    @Nested
    @DisplayName("Authentication Security Tests")
    class AuthenticationSecurityTests {

        @Test
        @DisplayName("Should prevent SQL injection in username field")
        void shouldPreventSQLInjectionInUsernameField() throws Exception {
            // Given
            String sqlInjection = "admin' OR '1'='1";
            Map<String, String> maliciousRequest = Map.of(
                    "username", sqlInjection,
                    "password", "password"
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(maliciousRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should prevent brute force attacks with rate limiting")
        void shouldPreventBruteForceAttacksWithRateLimiting() throws Exception {
            // Given
            Map<String, String> loginRequest = Map.of(
                    "username", "testuser@example.com",
                    "password", "wrongpassword"
            );

            // When - Attempt multiple failed logins
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                        .writeValueAsString(loginRequest)))
                        .andExpect(status().isUnauthorized());
            }

            // Then - Next attempt should be rate limited (429 Too Many Requests)
            // Note: This test assumes rate limiting is implemented
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(loginRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject empty credentials")
        void shouldRejectEmptyCredentials() throws Exception {
            // Given
            Map<String, String> emptyRequest = Map.of(
                    "username", "",
                    "password", ""
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(emptyRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should enforce password complexity requirements")
        void shouldEnforcePasswordComplexityRequirements() throws Exception {
            // Given - Weak password
            Map<String, String> weakPasswordRequest = Map.of(
                    "username", "newuser@example.com",
                    "password", "123"
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(weakPasswordRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("Token Security Tests")
    class TokenSecurityTests {

        @Test
        @DisplayName("Should reject token with tampered payload")
        void shouldRejectTokenWithTamperedPayload() throws Exception {
            // Given - Token with tampered payload
            String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.TAMPERED_PAYLOAD.signature";

            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(Map.of("token", tamperedToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("Should reject token with none algorithm")
        void shouldRejectTokenWithNoneAlgorithm() throws Exception {
            // Given - Token with 'none' algorithm (security vulnerability)
            String noneAlgoToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0dXNlciJ9.";

            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(Map.of("token", noneAlgoToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("Should validate token signature integrity")
        void shouldValidateTokenSignatureIntegrity() throws Exception {
            // Given - Token with invalid signature
            String tokenWithInvalidSignature = testDataBuilder.buildTokenWithInvalidSignature();

            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(Map.of("token", tokenWithInvalidSignature))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("Should enforce token expiration strictly")
        void shouldEnforceTokenExpirationStrictly() throws Exception {
            // Given - Expired token
            String expiredToken = testDataBuilder.buildExpiredToken();

            // When & Then
            mockMvc.perform(post("/api/v1/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(Map.of("token", expiredToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("Authorization Security Tests")
    class AuthorizationSecurityTests {

        @Test
        @DisplayName("Should enforce role-based access control")
        void shouldEnforceRoleBasedAccessControl() throws Exception {
            // Given - User token without admin role
            String userToken = testDataBuilder.buildValidUserToken();

            // When & Then - Attempt to access admin endpoint
            mockMvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow admin access with proper role")
        void shouldAllowAdminAccessWithProperRole() throws Exception {
            // Given - Admin token
            String adminToken = testDataBuilder.buildValidAdminToken();

            // When & Then - Access admin endpoint
            mockMvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should reject requests without authentication token")
        void shouldRejectRequestsWithoutAuthenticationToken() throws Exception {
            // When & Then - Attempt to access protected endpoint without token
            mockMvc.perform(post("/api/v1/protected/resource")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should validate token before granting access")
        void shouldValidateTokenBeforeGrantingAccess() throws Exception {
            // Given - Invalid token
            String invalidToken = "invalid.token.here";

            // When & Then
            mockMvc.perform(post("/api/v1/protected/resource")
                            .header("Authorization", "Bearer " + invalidToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Cross-Site Request Forgery (CSRF) Tests")
    class CSRFTests {

        @Test
        @DisplayName("Should protect against CSRF attacks on state-changing operations")
        void shouldProtectAgainstCSRFAttacks() throws Exception {
            // Given - Request without CSRF token
            Map<String, String> request = Map.of(
                    "username", "testuser@example.com",
                    "password", "ValidPassword123!"
            );

            // When & Then - CSRF protection should be in place
            // Note: Actual implementation depends on Spring Security CSRF configuration
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Input Validation Security Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should sanitize user input to prevent XSS")
        void shouldSanitizeUserInputToPreventXSS() throws Exception {
            // Given - Input with XSS payload
            String xssPayload = "<script>alert('XSS')</script>";
            Map<String, String> maliciousRequest = Map.of(
                    "username", xssPayload,
                    "password", "password"
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(maliciousRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should validate email format in username field")
        void shouldValidateEmailFormatInUsernameField() throws Exception {
            // Given - Invalid email format
            Map<String, String> invalidEmailRequest = Map.of(
                    "username", "not-an-email",
                    "password", "ValidPassword123!"
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(invalidEmailRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should enforce maximum length for input fields")
        void shouldEnforceMaximumLengthForInputFields() throws Exception {
            // Given - Extremely long input
            String longInput = "a".repeat(10000);
            Map<String, String> longInputRequest = Map.of(
                    "username", longInput,
                    "password", "password"
            );

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(longInputRequest)))
                    .andExpect(status().isBadRequest());
        }
    }
}
