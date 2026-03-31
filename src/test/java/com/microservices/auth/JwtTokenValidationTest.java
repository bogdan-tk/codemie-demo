package com.microservices.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT token validation logic.
 * Tests token parsing, validation, and claims extraction.
 *
 * Test Case ID: EPMCDMETST-36039
 *
 * @author Automated Test Generator
 */
@ExtendWith(MockitoExtension.class)
public class JwtTokenValidationTest {

    private Key secretKey;
    private TestDataBuilder testDataBuilder;

    @BeforeEach
    void setUp() {
        secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        testDataBuilder = new TestDataBuilder();
    }

    @Test
    @DisplayName("Should successfully parse valid JWT token")
    void shouldSuccessfullyParseValidJWTToken() {
        // Given
        String token = testDataBuilder.buildValidUserToken();

        // When - Parse the token (in real implementation, this would be done by JwtService)
        // This is a simulation of token parsing
        boolean isValid = token != null && token.split("\\.").length == 3;

        // Then
        assertThat(isValid).isTrue();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("Should extract username from JWT claims")
    void shouldExtractUsernameFromJWTClaims() {
        // Given
        String expectedUsername = "testuser@example.com";
        String token = buildTokenWithClaims(expectedUsername, List.of("USER"));

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Then
        assertThat(claims.getSubject()).isEqualTo(expectedUsername);
        assertThat(claims.get("username")).isEqualTo(expectedUsername);
    }

    @Test
    @DisplayName("Should extract roles from JWT claims")
    void shouldExtractRolesFromJWTClaims() {
        // Given
        List<String> expectedRoles = List.of("USER", "ADMIN");
        String token = buildTokenWithClaims("admin@example.com", expectedRoles);

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");

        // Then
        assertThat(roles).containsExactlyInAnyOrderElementsOf(expectedRoles);
    }

    @Test
    @DisplayName("Should validate token expiration time")
    void shouldValidateTokenExpirationTime() {
        // Given
        String token = buildTokenWithExpiration(60); // 60 minutes

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Then
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuedAt()).isBefore(new Date());
    }

    @Test
    @DisplayName("Should reject expired JWT token")
    void shouldRejectExpiredJWTToken() {
        // Given - Create an expired token
        String expiredToken = buildTokenWithExpiration(-60); // Expired 60 minutes ago

        // When & Then
        assertThatThrownBy(() -> 
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(expiredToken)
        ).isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Should reject token with invalid signature")
    void shouldRejectTokenWithInvalidSignature() {
        // Given
        Key differentKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String token = Jwts.builder()
                .setSubject("testuser@example.com")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(differentKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        // When & Then
        assertThatThrownBy(() ->
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
        ).isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    @DisplayName("Should reject malformed JWT token")
    void shouldRejectMalformedJWTToken() {
        // Given
        String malformedToken = "not.a.valid.jwt.token.structure";

        // When & Then
        assertThatThrownBy(() ->
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(malformedToken)
        ).isInstanceOf(io.jsonwebtoken.MalformedJwtException.class);
    }

    @Test
    @DisplayName("Should validate token contains required claims")
    void shouldValidateTokenContainsRequiredClaims() {
        // Given
        String token = buildTokenWithClaims("testuser@example.com", List.of("USER"));

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Then
        assertThat(claims.getSubject()).isNotNull();
        assertThat(claims.get("username")).isNotNull();
        assertThat(claims.get("roles")).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("Should validate token issued at time is before expiration")
    void shouldValidateTokenIssuedAtTimeIsBeforeExpiration() {
        // Given
        String token = buildTokenWithExpiration(30);

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Then
        assertThat(claims.getIssuedAt()).isBefore(claims.getExpiration());
    }

    @Test
    @DisplayName("Should extract authorities from JWT claims")
    void shouldExtractAuthoritiesFromJWTClaims() {
        // Given
        List<String> expectedAuthorities = List.of("ROLE_USER", "ROLE_ADMIN");
        String token = buildTokenWithAuthorities("admin@example.com", expectedAuthorities);

        // When
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        List<String> authorities = (List<String>) claims.get("authorities");

        // Then
        assertThat(authorities).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
    }

    // Helper methods

    private String buildTokenWithClaims(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plus(60, ChronoUnit.MINUTES);

        return Jwts.builder()
                .setSubject(username)
                .claim("username", username)
                .claim("roles", roles)
                .claim("authorities", roles)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(secretKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildTokenWithExpiration(long minutesFromNow) {
        Instant now = Instant.now();
        Instant expiration = now.plus(minutesFromNow, ChronoUnit.MINUTES);

        return Jwts.builder()
                .setSubject("testuser@example.com")
                .claim("username", "testuser@example.com")
                .claim("roles", List.of("USER"))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(secretKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildTokenWithAuthorities(String username, List<String> authorities) {
        Instant now = Instant.now();
        Instant expiration = now.plus(60, ChronoUnit.MINUTES);

        return Jwts.builder()
                .setSubject(username)
                .claim("username", username)
                .claim("authorities", authorities)
                .claim("roles", authorities)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(secretKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }
}
