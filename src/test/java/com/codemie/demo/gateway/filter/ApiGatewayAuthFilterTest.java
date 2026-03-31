package com.codemie.demo.gateway.filter;

import com.codemie.demo.auth.dto.ValidationRequest;
import com.codemie.demo.auth.dto.ValidationResponse;
import com.codemie.demo.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for API Gateway authentication filter integration with AuthService.
 * 
 * Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
 * 
 * This test class verifies:
 * - API Gateway edge authentication using AuthService
 * - JWT token extraction from Authorization header
 * - Token validation before routing to downstream services
 * - Request rejection for invalid or missing tokens
 * - Security context population with user details
 * 
 * @author Test Automation Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API Gateway - Authentication Filter Tests")
class ApiGatewayAuthFilterTest {

    @Mock
    private AuthService authService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiGatewayAuthFilter authFilter;

    @Captor
    private ArgumentCaptor<ValidationRequest> validationRequestCaptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciJ9.test";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should allow request with valid JWT token")
    void shouldAllowRequestWithValidJwtToken() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        
        ValidationResponse validResponse = ValidationResponse.builder()
                .valid(true)
                .username("testuser")
                .userId(1L)
                .roles(List.of("ROLE_USER"))
                .build();
        
        when(authService.validateToken(any(ValidationRequest.class))).thenReturn(validResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(authService).validateToken(validationRequestCaptor.capture());
        assertThat(validationRequestCaptor.getValue().getToken()).isEqualTo(VALID_TOKEN);
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("Should reject request without Authorization header")
    void shouldRejectRequestWithoutAuthorizationHeader() throws ServletException, IOException {
        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(authService, never()).validateToken(any());
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getErrorMessage()).contains("Missing Authorization header");
    }

    @Test
    @DisplayName("Should reject request with invalid token format")
    void shouldRejectRequestWithInvalidTokenFormat() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "InvalidFormat token");

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(authService, never()).validateToken(any());
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getErrorMessage()).contains("Invalid Authorization header format");
    }

    @Test
    @DisplayName("Should reject request when token validation fails")
    void shouldRejectRequestWhenTokenValidationFails() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        
        ValidationResponse invalidResponse = ValidationResponse.builder()
                .valid(false)
                .errorMessage("Token expired")
                .build();
        
        when(authService.validateToken(any(ValidationRequest.class))).thenReturn(invalidResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(authService).validateToken(any());
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getErrorMessage()).contains("Token expired");
    }

    @Test
    @DisplayName("Should populate security context with user details")
    void shouldPopulateSecurityContextWithUserDetails() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        
        ValidationResponse validResponse = ValidationResponse.builder()
                .valid(true)
                .username("adminuser")
                .userId(100L)
                .email("admin@example.com")
                .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
                .build();
        
        when(authService.validateToken(any(ValidationRequest.class))).thenReturn(validResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        assertThat(request.getAttribute("userId")).isEqualTo(100L);
        assertThat(request.getAttribute("username")).isEqualTo("adminuser");
        assertThat(request.getAttribute("email")).isEqualTo("admin@example.com");
        assertThat(request.getAttribute("roles")).isEqualTo(List.of("ROLE_USER", "ROLE_ADMIN"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should forward validated user info to downstream services")
    void shouldForwardValidatedUserInfoToDownstreamServices() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        request.setRequestURI("/api/protected/resource");
        
        ValidationResponse validResponse = ValidationResponse.builder()
                .valid(true)
                .username("serviceuser")
                .userId(200L)
                .roles(List.of("ROLE_SERVICE"))
                .build();
        
        when(authService.validateToken(any(ValidationRequest.class))).thenReturn(validResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        
        // Verify headers are added for downstream services
        assertThat(request.getHeader("X-User-Id")).isEqualTo("200");
        assertThat(request.getHeader("X-Username")).isEqualTo("serviceuser");
        assertThat(request.getHeader("X-User-Roles")).isEqualTo("ROLE_SERVICE");
    }

    @Test
    @DisplayName("Should handle AuthService exceptions gracefully")
    void shouldHandleAuthServiceExceptionsGracefully() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        when(authService.validateToken(any())).thenThrow(new RuntimeException("Service unavailable"));

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.getErrorMessage()).contains("Authentication service error");
    }

    @Test
    @DisplayName("Should validate token for each request")
    void shouldValidateTokenForEachRequest() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        
        ValidationResponse validResponse = ValidationResponse.builder()
                .valid(true)
                .username("testuser")
                .userId(1L)
                .roles(List.of("ROLE_USER"))
                .build();
        
        when(authService.validateToken(any())).thenReturn(validResponse);

        // When - Multiple requests
        authFilter.doFilter(request, response, filterChain);
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(authService, times(2)).validateToken(any());
        verify(filterChain, times(2)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should support role-based routing decisions")
    void shouldSupportRoleBasedRoutingDecisions() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        request.setRequestURI("/api/admin/users");
        
        ValidationResponse adminResponse = ValidationResponse.builder()
                .valid(true)
                .username("admin")
                .userId(1L)
                .roles(List.of("ROLE_ADMIN"))
                .build();
        
        when(authService.validateToken(any())).thenReturn(adminResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(request.getAttribute("roles")).asList().contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should reject request when required role is not present")
    void shouldRejectRequestWhenRequiredRoleIsNotPresent() throws ServletException, IOException {
        // Given
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        request.setRequestURI("/api/admin/users");
        request.setAttribute("requiredRole", "ROLE_ADMIN");
        
        ValidationResponse userResponse = ValidationResponse.builder()
                .valid(true)
                .username("user")
                .userId(2L)
                .roles(List.of("ROLE_USER"))
                .build();
        
        when(authService.validateToken(any())).thenReturn(userResponse);

        // When
        authFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getErrorMessage()).contains("Insufficient permissions");
    }
}
