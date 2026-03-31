package com.microservices.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test data builder for AuthService tests.
 * Provides utility methods to build test data for authentication scenarios.
 *
 * Test Case ID: EPMCDMETST-36039
 *
 * @author Automated Test Generator
 */
public class TestDataBuilder {

    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long TOKEN_VALIDITY = 3600000; // 1 hour in milliseconds

    /**
     * Builds a valid login request with username and password.
     *
     * @param username User's email or username
     * @param password User's password
     * @return Map containing login credentials
     */
    public Map<String, String> buildValidLoginRequest(String username, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        return request;
    }

    /**
     * Builds an invalid login request with incorrect credentials.
     *
     * @param username User's email or username
     * @param password Incorrect password
     * @return Map containing invalid login credentials
     */
    public Map<String, String> buildInvalidLoginRequest(String username, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        return request;
    }

    /**
     * Builds a JWT token with specified claims and expiration.
     *
     * @param username Subject of the token
     * @param roles List of user roles
     * @param expirationMinutes Token expiration time in minutes
     * @return JWT token string
     */
    public String buildJWTToken(String username, List<String> roles, long expirationMinutes) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("username", username);
        claims.put("authorities", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Builds an expired JWT token for testing token expiration scenarios.
     *
     * @return Expired JWT token string
     */
    public String buildExpiredToken() {
        Instant now = Instant.now();
        Instant expiration = now.minus(1, ChronoUnit.HOURS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("USER"));
        claims.put("username", "testuser@example.com");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("testuser@example.com")
                .setIssuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .setExpiration(Date.from(expiration))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Builds a valid JWT token for a regular user.
     *
     * @return Valid JWT token with USER role
     */
    public String buildValidUserToken() {
        return buildJWTToken("testuser@example.com", List.of("USER"), 60);
    }

    /**
     * Builds a valid JWT token for an admin user.
     *
     * @return Valid JWT token with ADMIN and USER roles
     */
    public String buildValidAdminToken() {
        return buildJWTToken("admin@example.com", List.of("ADMIN", "USER"), 60);
    }

    /**
     * Builds a token validation request.
     *
     * @param token JWT token to validate
     * @return Map containing token validation request
     */
    public Map<String, String> buildTokenValidationRequest(String token) {
        Map<String, String> request = new HashMap<>();
        request.put("token", token);
        return request;
    }

    /**
     * Builds a refresh token request.
     *
     * @param refreshToken Refresh token string
     * @return Map containing refresh token request
     */
    public Map<String, String> buildRefreshTokenRequest(String refreshToken) {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", refreshToken);
        return request;
    }

    /**
     * Builds test user data with specific roles.
     *
     * @param username User's username
     * @param email User's email
     * @param roles List of user roles
     * @return Map containing user data
     */
    public Map<String, Object> buildUserData(String username, String email, List<String> roles) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("email", email);
        userData.put("roles", roles);
        userData.put("enabled", true);
        userData.put("accountNonExpired", true);
        userData.put("accountNonLocked", true);
        userData.put("credentialsNonExpired", true);
        return userData;
    }

    /**
     * Builds a complete authentication response.
     *
     * @param accessToken Access token
     * @param refreshToken Refresh token
     * @param expiresIn Token expiration time in seconds
     * @return Map containing authentication response
     */
    public Map<String, Object> buildAuthenticationResponse(
            String accessToken, 
            String refreshToken, 
            long expiresIn) {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", expiresIn);
        return response;
    }

    /**
     * Builds a malformed JWT token for negative testing.
     *
     * @return Malformed JWT token string
     */
    public String buildMalformedToken() {
        return "malformed.jwt.token.structure";
    }

    /**
     * Builds a token with invalid signature for security testing.
     *
     * @return JWT token with invalid signature
     */
    public String buildTokenWithInvalidSignature() {
        Key differentKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        
        return Jwts.builder()
                .setSubject("testuser@example.com")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(differentKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
