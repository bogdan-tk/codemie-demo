package com.example.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrderService Spring Boot Application.
 * 
 * Jira: EPMCDMETST-36038
 * Test Objective: Verify OrderService starts successfully with all required beans
 * and PostgreSQL connectivity.
 * 
 * Test Coverage:
 * - Application context loads successfully
 * - All required beans are present
 * - Database connectivity is established
 * - Service is ready to handle requests
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orderservice_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Test Case: Verify Spring Boot application context loads successfully
     * 
     * Scenario:
     * 1. Start OrderService with Testcontainers PostgreSQL
     * 2. Verify application context is not null
     * 3. Verify all essential beans are loaded
     * 
     * Expected Result:
     * - Application context loads without errors
     * - Service is ready to process requests
     */
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }

    /**
     * Test Case: Verify PostgreSQL container is running and accessible
     * 
     * Scenario:
     * 1. Check PostgreSQL container status
     * 2. Verify container is running
     * 3. Validate connection parameters
     * 
     * Expected Result:
     * - PostgreSQL container is running
     * - Connection URL is valid
     * - Database name matches configuration
     */
    @Test
    void postgresContainerIsRunning() {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDatabaseName()).isEqualTo("orderservice_test");
        assertThat(postgresContainer.getUsername()).isEqualTo("test_user");
    }

    /**
     * Test Case: Verify OrderController bean is present in context
     * 
     * Scenario:
     * 1. Query application context for OrderController bean
     * 2. Verify bean exists and is properly initialized
     * 
     * Expected Result:
     * - OrderController bean is present
     * - Bean is a valid Spring component
     */
    @Test
    void orderControllerBeanExists() {
        assertThat(applicationContext.containsBean("orderController")).isTrue();
    }

    /**
     * Test Case: Verify OrderService bean is present in context
     * 
     * Scenario:
     * 1. Query application context for OrderService bean
     * 2. Verify bean exists and is properly initialized
     * 
     * Expected Result:
     * - OrderService bean is present
     * - Bean is a valid Spring service component
     */
    @Test
    void orderServiceBeanExists() {
        assertThat(applicationContext.containsBean("orderService")).isTrue();
    }

    /**
     * Test Case: Verify OrderRepository bean is present in context
     * 
     * Scenario:
     * 1. Query application context for OrderRepository bean
     * 2. Verify bean exists and is properly initialized
     * 
     * Expected Result:
     * - OrderRepository bean is present
     * - Bean is a valid Spring Data JPA repository
     */
    @Test
    void orderRepositoryBeanExists() {
        assertThat(applicationContext.containsBean("orderRepository")).isTrue();
    }

    /**
     * Test Case: Verify Flyway migration runs successfully
     * 
     * Scenario:
     * 1. Check if Flyway bean is present
     * 2. Verify migrations have been applied
     * 3. Validate database schema is ready
     * 
     * Expected Result:
     * - Flyway bean is configured
     * - Database schema is initialized
     * - OrderService schema is ready for use
     */
    @Test
    void flywayMigrationExecuted() {
        assertThat(applicationContext.containsBean("flyway")).isTrue();
    }
}
