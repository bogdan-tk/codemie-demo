package com.codemie.demo.auth.service;

import com.codemie.demo.auth.config.JwtProperties;
import com.codemie.demo.auth.dto.ValidationRequest;
import com.codemie.demo.auth.dto.ValidationResponse;
import com.codemie.demo.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test suite for AuthService JWT token validation functionality.
 * 
 * Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
 * 
 * This test class verifies:
 * - JWT token validation for API Gateway edge authentication
 * - Token signature verification
 * - Token expiration checking
 * - Claims extraction and validation
 * - Error handling for malformed or expired tokens
 * 
 * @author Test Automation Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - JWT Token Validation Tests")
class AuthServiceJwtValidationTest {

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-256-bits-long";
    private static final String TEST_ISSUER = "auth-service";
    private static final long TEST_EXPIRATION_MS = 3600000; // 1 hour

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        when(jwtProperties.getIssuer()).thenReturn(TEST_ISSUER);
        when(jwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
        
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Should successfully validate valid JWT token")
    void shouldSuccessfullyValidateValidJwtToken() {
        // Given
        String validToken = createValidToken("testuser", 1L, List.of("ROLE_USER"));
        ValidationRequest request = ValidationRequest.builder()
                .token(validToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isValid()).isTrue();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getRoles()).containsExactly("ROLE_USER");
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should extract all claims from valid token")
    void shouldExtractAllClaimsFromValidToken() {
        // Given
        String validToken = createValidToken(
                "adminuser",
                100L,
                List.of("ROLE_USER", "ROLE_ADMIN"),
                "admin@example.com"
        );
        ValidationRequest request = ValidationRequest.builder()
                .token(validToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.getUsername()).isEqualTo("adminuser");
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getEmail()).isEqualTo("admin@example.com");
        assertThat(response.getRoles())
                .hasSize(2)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should reject expired JWT token")
    void shouldRejectExpiredJwtToken() {
        // Given
        String expiredToken = createExpiredToken("testuser", 1L, List.of("ROLE_USER"));
        ValidationRequest request = ValidationRequest.builder()
                .token(expiredToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("expired");
        assertThat(response.getUsername()).isNull();
    }

    @Test
    @DisplayName("Should reject token with invalid signature")
    void shouldRejectTokenWithInvalidSignature() {
        // Given
        String invalidSecret = "different-secret-key-that-will-cause-signature-mismatch-validation-failure";
        SecretKey wrongKey = Keys.hmacShaKeyFor(invalidSecret.getBytes(StandardCharsets.UTF_8));
        
        String tokenWithWrongSignature = Jwts.builder()
                .setSubject("testuser")
                .claim("userId", 1L)
                .claim("roles", List.of("ROLE_USER"))
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(wrongKey)
                .compact();

        ValidationRequest request = ValidationRequest.builder()
                .token(tokenWithWrongSignature)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("signature");
    }

    @Test
    @DisplayName("Should reject malformed JWT token")
    void shouldRejectMalformedJwtToken() {
        // Given
        ValidationRequest request = ValidationRequest.builder()
                .token("this.is.not.a.valid.jwt.token")
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("malformed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "null", "undefined"})
    @DisplayName("Should reject empty or invalid token strings")
    void shouldRejectEmptyOrInvalidTokenStrings(String invalidToken) {
        // Given
        ValidationRequest request = ValidationRequest.builder()
                .token(invalidToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("Should reject token with missing required claims")
    void shouldRejectTokenWithMissingRequiredClaims() {
        // Given - Token without userId claim
        String tokenWithoutUserId = Jwts.builder()
                .setSubject("testuser")
                .claim("roles", List.of("ROLE_USER"))
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();

        ValidationRequest request = ValidationRequest.builder()
                .token(tokenWithoutUserId)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("missing required claim");
    }

    @Test
    @DisplayName("Should reject token with incorrect issuer")
    void shouldRejectTokenWithIncorrectIssuer() {
        // Given
        String tokenWithWrongIssuer = Jwts.builder()
                .setSubject("testuser")
                .claim("userId", 1L)
                .claim("roles", List.of("ROLE_USER"))
                .setIssuer("malicious-issuer")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();

        ValidationRequest request = ValidationRequest.builder()
                .token(tokenWithWrongIssuer)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("issuer");
    }

    @Test
    @DisplayName("Should validate token issued at exact current time")
    void shouldValidateTokenIssuedAtExactCurrentTime() {
        // Given
        String freshToken = Jwts.builder()
                .setSubject("testuser")
                .claim("userId", 1L)
                .claim("roles", List.of("ROLE_USER"))
                .claim("email", "test@example.com")
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(TEST_EXPIRATION_MS, ChronoUnit.MILLIS)))
                .signWith(signingKey)
                .compact();

        ValidationRequest request = ValidationRequest.builder()
                .token(freshToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should validate token about to expire")
    void shouldValidateTokenAboutToExpire() {
        // Given - Token expiring in 1 second
        String almostExpiredToken = Jwts.builder()
                .setSubject("testuser")
                .claim("userId", 1L)
                .claim("roles", List.of("ROLE_USER"))
                .claim("email", "test@example.com")
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.SECONDS)))
                .signWith(signingKey)
                .compact();

        ValidationRequest request = ValidationRequest.builder()
                .token(almostExpiredToken)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should handle token validation for API Gateway edge auth")
    void shouldHandleTokenValidationForApiGatewayEdgeAuth() {
        // Given - Simulating API Gateway validation request
        String gatewayToken = createValidToken(
                "gateway-user",
                999L,
                List.of("ROLE_API_USER", "ROLE_GATEWAY_ACCESS")
        );
        
        ValidationRequest gatewayRequest = ValidationRequest.builder()
                .token(gatewayToken)
                .requiredRole("ROLE_GATEWAY_ACCESS")
                .build();

        // When
        ValidationResponse response = authService.validateToken(gatewayRequest);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.getRoles()).contains("ROLE_GATEWAY_ACCESS");
        assertThat(response.hasRole("ROLE_GATEWAY_ACCESS")).isTrue();
    }

    @Test
    @DisplayName("Should reject token when required role is missing")
    void shouldRejectTokenWhenRequiredRoleIsMissing() {
        // Given
        String userToken = createValidToken("user", 1L, List.of("ROLE_USER"));
        ValidationRequest request = ValidationRequest.builder()
                .token(userToken)
                .requiredRole("ROLE_ADMIN")
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).contains("required role");
    }

    @Test
    @DisplayName("Should provide detailed validation response for gateway")
    void shouldProvideDetailedValidationResponseForGateway() {
        // Given
        String token = createValidToken(
                "service-user",
                500L,
                List.of("ROLE_SERVICE", "ROLE_INTERNAL"),
                "service@internal.com"
        );
        ValidationRequest request = ValidationRequest.builder()
                .token(token)
                .build();

        // When
        ValidationResponse response = authService.validateToken(request);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.getUsername()).isEqualTo("service-user");
        assertThat(response.getUserId()).isEqualTo(500L);
        assertThat(response.getEmail()).isEqualTo("service@internal.com");
        assertThat(response.getRoles()).containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_INTERNAL");
        assertThat(response.getIssuer()).isEqualTo(TEST_ISSUER);
        assertThat(response.getIssuedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();
    }

    /**
     * Helper method to create a valid JWT token for testing.
     */
    private String createValidToken(String username, Long userId, List<String> roles) {
        return createValidToken(username, userId, roles, username + "@example.com");
    }

    /**
     * Helper method to create a valid JWT token with email for testing.
     */
    private String createValidToken(String username, Long userId, List<String> roles, String email) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("email", email)
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(TEST_EXPIRATION_MS, ChronoUnit.MILLIS)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Helper method to create an expired JWT token for testing.
     */
    private String createExpiredToken(String username, Long userId, List<String> roles) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("email", username + "@example.com")
                .setIssuer(TEST_ISSUER)
                .setIssuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .setExpiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }
}
