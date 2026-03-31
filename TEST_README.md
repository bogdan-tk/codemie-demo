# AuthService JWT Integration Tests

**Test Case ID:** EPMCDMETST-36039

## Overview

This test suite provides comprehensive automated tests for the AuthService JWT token issuance and validation functionality. The tests cover authentication, authorization, security, and token management scenarios.

## Test Scenarios

The test suite implements the following scenarios from the Jira ticket:

### 1. Client Submits Credentials to AuthService
- Valid credential submission
- Invalid credential handling
- Missing credential validation
- Input sanitization

### 2. AuthService Issues JWT Token
- Token generation with valid credentials
- Token structure validation
- Role inclusion in JWT claims
- Token expiration configuration

### 3. API Gateway Token Validation
- Valid token validation
- Invalid token rejection
- Expired token handling
- Signature verification

### 4. Internal Services Validate User Roles
- Role extraction from JWT claims
- ADMIN role validation
- USER role validation
- Authority-based access control

## Test Files

### Core Test Classes

1. **AuthServiceIntegrationTest.java**
   - Full integration tests for AuthService
   - Tests token issuance, validation, and refresh
   - Uses Testcontainers for Redis
   - Covers all four main test scenarios

2. **JwtTokenValidationTest.java**
   - Unit tests for JWT token validation logic
   - Token parsing and claims extraction
   - Expiration and signature validation
   - Security vulnerability tests

3. **AuthServiceSecurityTest.java**
   - Security-focused tests
   - SQL injection prevention
   - XSS protection
   - CSRF protection
   - Rate limiting
   - Input validation

### Support Classes

4. **TestDataBuilder.java**
   - Utility class for building test data
   - JWT token generation helpers
   - Request/response builders
   - Test user data creation

### Configuration Files

5. **application-test.properties**
   - Test-specific configuration
   - JWT settings
   - Database configuration
   - Security settings

6. **docker-compose-test.yml**
   - Docker Compose setup for testing
   - Redis container
   - PostgreSQL container
   - Network configuration

7. **pom.xml**
   - Maven dependencies
   - Test framework configuration
   - Build plugins

## Running the Tests

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker (for Testcontainers)

### Execute All Tests

```bash
mvn clean test
```

### Execute Specific Test Class

```bash
mvn test -Dtest=AuthServiceIntegrationTest
mvn test -Dtest=JwtTokenValidationTest
mvn test -Dtest=AuthServiceSecurityTest
```

### Execute with Docker Compose

```bash
# Start test environment
docker-compose -f docker-compose-test.yml up -d

# Run tests
mvn test

# Stop test environment
docker-compose -f docker-compose-test.yml down
```

## Test Coverage

The test suite covers:

- ✅ JWT token issuance with valid credentials
- ✅ JWT token validation for API Gateway
- ✅ Role-based authorization
- ✅ Token expiration handling
- ✅ Refresh token functionality
- ✅ Security vulnerabilities (SQL injection, XSS, CSRF)
- ✅ Input validation
- ✅ Rate limiting
- ✅ Token signature verification

## Expected Results

All tests should pass with the following validations:

1. **Token Issuance**
   - REST endpoint `/api/v1/auth/login` issues JWT tokens
   - Tokens contain `accessToken`, `refreshToken`, `tokenType`, and `expiresIn`
   - Response returns 200 OK for valid credentials
   - Response returns 401 Unauthorized for invalid credentials

2. **Token Validation**
   - REST endpoint `/api/v1/auth/validate` validates JWT tokens
   - Returns `valid: true` for valid tokens
   - Returns `valid: false` for invalid/expired tokens
   - Includes username and roles in validation response

3. **Role-Based Authorization**
   - JWT claims include user roles/authorities
   - ADMIN role grants privileged access
   - USER role grants basic access
   - Unauthorized access returns 403 Forbidden

## Affected Areas

- **AuthService**: Core authentication service
- **API Gateway**: Edge authentication using token validation
- **Inter-service Authorization**: Role-based access control

## Acceptance Criteria

✅ REST endpoint exists to issue JWT token  
✅ REST endpoint exists to validate JWT token (for gateway usage)  
✅ JWT includes user roles/authorities in claims  
✅ AuthService is documented with OpenAPI 3.0 (Swagger)  

## CI/CD Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run AuthService Tests
  run: mvn clean test
  
- name: Publish Test Results
  uses: dorny/test-reporter@v1
  with:
    name: AuthService Test Results
    path: target/surefire-reports/*.xml
    reporter: java-junit
```

## Troubleshooting

### Testcontainers Issues

If Testcontainers fail to start:

```bash
# Check Docker is running
docker ps

# Pull required images manually
docker pull redis:7.0-alpine
docker pull postgres:15-alpine
```

### Port Conflicts

If tests fail due to port conflicts:

```bash
# Check which process is using the port
lsof -i :6379
lsof -i :5432

# Kill the process or change port in application-test.properties
```

## Contributing

When adding new tests:

1. Follow the existing test structure
2. Use descriptive test names with `@DisplayName`
3. Follow Given-When-Then pattern
4. Add appropriate assertions
5. Update this README with new test scenarios

## References

- Jira Ticket: [EPMCDMETST-36039](https://jiraeu.epam.com/browse/EPMCDMETST-36039)
- Spring Boot Testing: https://spring.io/guides/gs/testing-web/
- Testcontainers: https://www.testcontainers.org/
- JWT: https://jwt.io/
