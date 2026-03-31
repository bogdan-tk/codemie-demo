package com.codemie.demo.auth.service;

import com.codemie.demo.auth.config.JwtProperties;
import com.codemie.demo.auth.dto.AuthRequest;
import com.codemie.demo.auth.dto.AuthResponse;
import com.codemie.demo.auth.exception.AuthenticationException;
import com.codemie.demo.auth.model.User;
import com.codemie.demo.auth.model.Role;
import com.codemie.demo.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test suite for AuthService JWT token issuance functionality.
 * 
 * Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
 * 
 * This test class verifies:
 * - JWT token generation with valid credentials
 * - Token structure and claims validation
 * - Role and permission inclusion in tokens
 * - Token expiration settings
 * - Error handling for invalid credentials
 * 
 * @author Test Automation Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - JWT Token Issuance Tests")
class AuthServiceJwtIssuanceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-256-bits-long";
    private static final String TEST_ISSUER = "auth-service";
    private static final long TEST_EXPIRATION_MS = 3600000; // 1 hour
    
    private User testUser;
    private AuthRequest validAuthRequest;

    @BeforeEach
    void setUp() {
        // Configure JWT properties mock
        when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        when(jwtProperties.getIssuer()).thenReturn(TEST_ISSUER);
        when(jwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);

        // Create test user with roles
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("testuser@example.com")
                .password("$2a$10$encodedPassword")
                .roles(Set.of(
                        Role.builder().name("ROLE_USER").build(),
                        Role.builder().name("ROLE_ADMIN").build()
                ))
                .enabled(true)
                .build();

        validAuthRequest = AuthRequest.builder()
                .username("testuser")
                .password("plainPassword123")
                .build();
    }

    @Test
    @DisplayName("Should issue JWT token with valid credentials")
    void shouldIssueJwtTokenWithValidCredentials() {
        // Given
        when(userRepository.findByUsername(validAuthRequest.getUsername()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(validAuthRequest.getPassword(), testUser.getPassword()))
                .thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo(testUser.getUsername());
        assertThat(response.getExpiresIn()).isEqualTo(TEST_EXPIRATION_MS / 1000);

        verify(userRepository).findByUsername(validAuthRequest.getUsername());
        verify(passwordEncoder).matches(validAuthRequest.getPassword(), testUser.getPassword());
    }

    @Test
    @DisplayName("Should include user ID in JWT claims")
    void shouldIncludeUserIdInJwtClaims() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        assertThat(claims.get("userId", Long.class)).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should include username in JWT subject")
    void shouldIncludeUsernameInJwtSubject() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        assertThat(claims.getSubject()).isEqualTo(testUser.getUsername());
    }

    @Test
    @DisplayName("Should include all user roles in JWT claims")
    void shouldIncludeAllUserRolesInJwtClaims() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles)
                .isNotNull()
                .hasSize(2)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should include email in JWT claims")
    void shouldIncludeEmailInJwtClaims() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        assertThat(claims.get("email", String.class)).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Should set correct issuer in JWT")
    void shouldSetCorrectIssuerInJwt() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        assertThat(claims.getIssuer()).isEqualTo(TEST_ISSUER);
    }

    @Test
    @DisplayName("Should set correct expiration time in JWT")
    void shouldSetCorrectExpirationTimeInJwt() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        Instant beforeIssuance = Instant.now();

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());
        Instant afterIssuance = Instant.now();

        // Then
        Date expiration = claims.getExpiration();
        Date issuedAt = claims.getIssuedAt();
        
        assertThat(issuedAt).isBetween(
                Date.from(beforeIssuance),
                Date.from(afterIssuance)
        );
        
        assertThat(expiration).isBetween(
                Date.from(beforeIssuance.plus(TEST_EXPIRATION_MS, ChronoUnit.MILLIS)),
                Date.from(afterIssuance.plus(TEST_EXPIRATION_MS, ChronoUnit.MILLIS))
        );
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userRepository).findByUsername(validAuthRequest.getUsername());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when password is incorrect")
    void shouldThrowExceptionWhenPasswordIsIncorrect() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userRepository).findByUsername(validAuthRequest.getUsername());
        verify(passwordEncoder).matches(validAuthRequest.getPassword(), testUser.getPassword());
    }

    @Test
    @DisplayName("Should throw exception when user is disabled")
    void shouldThrowExceptionWhenUserIsDisabled() {
        // Given
        testUser.setEnabled(false);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("User account is disabled");

        verify(userRepository).findByUsername(validAuthRequest.getUsername());
    }

    @Test
    @DisplayName("Should generate unique tokens for multiple authentications")
    void shouldGenerateUniqueTokensForMultipleAuthentications() throws InterruptedException {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response1 = authService.authenticate(validAuthRequest);
        Thread.sleep(10); // Ensure different issuance time
        AuthResponse response2 = authService.authenticate(validAuthRequest);

        // Then
        assertThat(response1.getToken()).isNotEqualTo(response2.getToken());
    }

    @ParameterizedTest
    @MethodSource("provideInvalidAuthRequests")
    @DisplayName("Should reject invalid authentication requests")
    void shouldRejectInvalidAuthRequests(AuthRequest request, String expectedErrorMessage) {
        // When & Then
        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedErrorMessage);
    }

    private static Stream<Arguments> provideInvalidAuthRequests() {
        return Stream.of(
                Arguments.of(
                        AuthRequest.builder().username(null).password("password").build(),
                        "Username cannot be null"
                ),
                Arguments.of(
                        AuthRequest.builder().username("").password("password").build(),
                        "Username cannot be empty"
                ),
                Arguments.of(
                        AuthRequest.builder().username("user").password(null).build(),
                        "Password cannot be null"
                ),
                Arguments.of(
                        AuthRequest.builder().username("user").password("").build(),
                        "Password cannot be empty"
                )
        );
    }

    @Test
    @DisplayName("Should handle users with no roles")
    void shouldHandleUsersWithNoRoles() {
        // Given
        User userWithoutRoles = User.builder()
                .id(2L)
                .username("noroleuser")
                .email("norole@example.com")
                .password("$2a$10$encodedPassword")
                .roles(Set.of())
                .enabled(true)
                .build();
        
        when(userRepository.findByUsername("noroleuser")).thenReturn(Optional.of(userWithoutRoles));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthRequest request = AuthRequest.builder()
                .username("noroleuser")
                .password("password")
                .build();

        // When
        AuthResponse response = authService.authenticate(request);
        Claims claims = parseToken(response.getToken());

        // Then
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should include custom claims for authorization")
    void shouldIncludeCustomClaimsForAuthorization() {
        // Given
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        AuthResponse response = authService.authenticate(validAuthRequest);
        Claims claims = parseToken(response.getToken());

        // Then
        assertThat(claims.get("userId")).isNotNull();
        assertThat(claims.get("email")).isNotNull();
        assertThat(claims.get("roles")).isNotNull();
        assertThat(claims.getSubject()).isNotNull();
        assertThat(claims.getIssuer()).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    /**
     * Helper method to parse JWT token and extract claims.
     */
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
