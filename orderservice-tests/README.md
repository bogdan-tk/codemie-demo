# OrderService Automated Tests

**Jira Ticket:** [EPMCDMETST-36038](https://jiraeu.epam.com/browse/EPMCDMETST-36038)

## Overview

This test suite provides comprehensive automated testing for the OrderService microservice, covering:
- Application startup and Spring Boot context loading
- REST API endpoints for order retrieval
- Service layer business logic
- Repository layer database operations
- PostgreSQL database schema validation
- Database-per-Service pattern compliance

## Test Architecture

The test suite follows TDD principles and microservices best practices:

```
orderservice-tests/
├── src/test/java/com/example/orderservice/
│   ├── OrderServiceApplicationTests.java          # Application startup tests
│   ├── controller/
│   │   └── OrderControllerIntegrationTest.java   # REST API integration tests
│   ├── service/
│   │   └── OrderServiceTest.java                 # Service layer unit tests
│   ├── repository/
│   │   └── OrderRepositoryTest.java              # Repository layer tests
│   └── database/
│       └── OrderDatabaseSchemaTest.java          # Database schema validation
├── src/test/resources/
│   └── application-test.properties                # Test configuration
├── docker-compose-test.yml                        # Test environment setup
├── setup-test-env.sh                              # Environment setup script
└── cleanup-test-env.sh                            # Cleanup script
```

## Technology Stack

- **Java 21** - Programming language
- **Spring Boot 3.x** - Application framework
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework
- **Testcontainers** - Container-based integration testing
- **PostgreSQL 15** - Database
- **AssertJ** - Fluent assertions
- **Spring MockMvc** - REST API testing

## Prerequisites

- Java 21 or higher
- Docker and Docker Compose
- Maven 3.8+ or Gradle 8+
- 4GB RAM minimum for Testcontainers

## Test Coverage

### 1. OrderServiceApplicationTests
Verifies Spring Boot application startup and bean initialization:
- ✅ Application context loads successfully
- ✅ PostgreSQL container is running
- ✅ OrderController bean exists
- ✅ OrderService bean exists
- ✅ OrderRepository bean exists
- ✅ Flyway migration executed

### 2. OrderControllerIntegrationTest
Tests REST API endpoints with real database:
- ✅ GET /api/orders - Retrieve all orders
- ✅ GET /api/orders/{id} - Retrieve order by ID
- ✅ GET /api/orders/{id} - Handle non-existent order (404)
- ✅ GET /api/orders/customer/{customerId} - Retrieve orders by customer
- ✅ GET /api/orders/customer/{customerId} - Handle customer with no orders
- ✅ Content-Type validation
- ✅ Database connectivity verification

### 3. OrderServiceTest
Unit tests for service layer business logic:
- ✅ Retrieve order by ID - Success
- ✅ Retrieve order by ID - Not found exception
- ✅ Retrieve all orders
- ✅ Retrieve all orders - Empty database
- ✅ Retrieve orders by customer ID
- ✅ Retrieve orders by customer ID - No orders
- ✅ Create new order
- ✅ Null parameter validation

### 4. OrderRepositoryTest
Data JPA tests with Testcontainers PostgreSQL:
- ✅ Save order with auto-generated ID
- ✅ Find order by ID
- ✅ Find order by ID - Non-existent
- ✅ Find all orders
- ✅ Find orders by customer ID
- ✅ Find orders by customer ID - No orders
- ✅ Find orders by status
- ✅ Update order
- ✅ Delete order
- ✅ Database constraints enforcement
- ✅ PostgreSQL container health check
- ✅ Transaction rollback

### 5. OrderDatabaseSchemaTest
Database schema validation:
- ✅ Orders table exists
- ✅ Table structure and columns
- ✅ Primary key constraint
- ✅ order_id column properties
- ✅ customer_id column properties
- ✅ order_number column properties (UNIQUE)
- ✅ total_amount column properties (NUMERIC)
- ✅ Timestamp columns (created_at, updated_at, order_date)
- ✅ Indexes for performance
- ✅ Flyway migration history
- ✅ Database isolation (Database-per-Service pattern)
- ✅ PostgreSQL version validation
- ✅ CRUD operations on schema

## Running the Tests

### Option 1: Using Testcontainers (Recommended)

Tests automatically start PostgreSQL containers:

```bash
# Maven
./mvnw test

# Gradle
./gradlew test
```

### Option 2: Using Docker Compose

Manually start test environment:

```bash
# Setup test environment
chmod +x setup-test-env.sh
./setup-test-env.sh

# Run tests
./mvnw test

# Cleanup
chmod +x cleanup-test-env.sh
./cleanup-test-env.sh
```

### Run Specific Test Classes

```bash
# Run application startup tests
./mvnw test -Dtest=OrderServiceApplicationTests

# Run REST API tests
./mvnw test -Dtest=OrderControllerIntegrationTest

# Run service layer tests
./mvnw test -Dtest=OrderServiceTest

# Run repository tests
./mvnw test -Dtest=OrderRepositoryTest

# Run database schema tests
./mvnw test -Dtest=OrderDatabaseSchemaTest
```

## Test Configuration

Test-specific configuration is in `src/test/resources/application-test.properties`:

```properties
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
logging.level.com.example.orderservice=DEBUG
```

## Database Schema

The tests validate the following schema:

```sql
CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    total_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    order_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
```

## Continuous Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run Tests
  run: ./mvnw test
  env:
    TESTCONTAINERS_RYUK_DISABLED: false
```

## Troubleshooting

### Docker Issues

```bash
# Check Docker is running
docker info

# Check Testcontainers logs
export TESTCONTAINERS_RYUK_DISABLED=false
```

### Port Conflicts

```bash
# Check if port 5432 is in use
lsof -i :5432

# Stop conflicting containers
docker ps
docker stop <container_id>
```

### Memory Issues

```bash
# Increase Docker memory allocation
# Docker Desktop -> Settings -> Resources -> Memory (4GB minimum)
```

## Test Reports

Test reports are generated in:
- Maven: `target/surefire-reports/`
- Gradle: `build/reports/tests/test/`

## Dependencies

Key test dependencies (add to pom.xml or build.gradle):

```xml
<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Acceptance Criteria Validation

✅ **Service skeleton created with modules**: Tests verify controller, service, repository, and DTO layers exist

✅ **REST API for order retrieval implemented**: Integration tests cover all order retrieval endpoints

✅ **Uses PostgreSQL schema dedicated to OrderService**: Schema tests validate Database-per-Service pattern

✅ **Includes Flyway migrations baseline**: Tests verify Flyway migration history and schema creation

## Contributing

When adding new tests:
1. Follow existing naming conventions
2. Add comprehensive JavaDoc comments
3. Include Jira ticket reference
4. Ensure tests are isolated and repeatable
5. Update this README with new test coverage

## Related Documentation

- [Jira Ticket EPMCDMETST-36038](https://jiraeu.epam.com/browse/EPMCDMETST-36038)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

## License

Internal use only - EPAM Systems
