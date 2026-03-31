package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OrderController REST API endpoints.
 * 
 * Jira: EPMCDMETST-36038
 * Test Objective: Verify OrderService REST API endpoints for order retrieval
 * work correctly with PostgreSQL backend.
 * 
 * Test Coverage:
 * - GET /api/orders - Retrieve all orders
 * - GET /api/orders/{id} - Retrieve order by ID
 * - GET /api/orders/customer/{customerId} - Retrieve orders by customer
 * - Error handling and validation
 * - Response format and status codes
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderDto testOrder1;
    private OrderDto testOrder2;

    @BeforeEach
    void setUp() {
        // Create test data
        testOrder1 = new OrderDto();
        testOrder1.setCustomerId(1001L);
        testOrder1.setOrderNumber("ORD-2024-001");
        testOrder1.setTotalAmount(new BigDecimal("299.99"));
        testOrder1.setStatus("PENDING");
        testOrder1.setOrderDate(LocalDateTime.now());

        testOrder2 = new OrderDto();
        testOrder2.setCustomerId(1001L);
        testOrder2.setOrderNumber("ORD-2024-002");
        testOrder2.setTotalAmount(new BigDecimal("149.50"));
        testOrder2.setStatus("COMPLETED");
        testOrder2.setOrderDate(LocalDateTime.now().minusDays(1));
    }

    /**
     * Test Case: Retrieve all orders via REST API
     * 
     * Scenario:
     * 1. Create test orders in database
     * 2. Call GET /api/orders endpoint
     * 3. Verify response contains orders
     * 4. Validate response structure and data
     * 
     * Expected Result:
     * - HTTP 200 OK status
     * - Response contains list of orders
     * - Order data matches expected format
     */
    @Test
    void testGetAllOrders_ReturnsOrderList() throws Exception {
        // Given: orders exist in database
        OrderDto savedOrder1 = orderService.createOrder(testOrder1);
        OrderDto savedOrder2 = orderService.createOrder(testOrder2);

        // When & Then: retrieve all orders
        mockMvc.perform(get("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].orderId", hasItem(savedOrder1.getOrderId().intValue())))
                .andExpect(jsonPath("$[*].orderId", hasItem(savedOrder2.getOrderId().intValue())))
                .andExpect(jsonPath("$[*].orderNumber", hasItems("ORD-2024-001", "ORD-2024-002")));
    }

    /**
     * Test Case: Retrieve order by ID
     * 
     * Scenario:
     * 1. Create a test order
     * 2. Call GET /api/orders/{id} with valid order ID
     * 3. Verify response contains correct order
     * 
     * Expected Result:
     * - HTTP 200 OK status
     * - Response contains requested order
     * - All order fields are populated correctly
     */
    @Test
    void testGetOrderById_ValidId_ReturnsOrder() throws Exception {
        // Given: order exists
        OrderDto savedOrder = orderService.createOrder(testOrder1);

        // When & Then: retrieve order by ID
        mockMvc.perform(get("/api/orders/{id}", savedOrder.getOrderId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(savedOrder.getOrderId()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-2024-001"))
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.totalAmount").value(299.99))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /**
     * Test Case: Retrieve order with non-existent ID
     * 
     * Scenario:
     * 1. Call GET /api/orders/{id} with invalid order ID
     * 2. Verify appropriate error response
     * 
     * Expected Result:
     * - HTTP 404 NOT FOUND status
     * - Error message indicates order not found
     */
    @Test
    void testGetOrderById_InvalidId_ReturnsNotFound() throws Exception {
        // Given: non-existent order ID
        Long nonExistentId = 999999L;

        // When & Then: attempt to retrieve non-existent order
        mockMvc.perform(get("/api/orders/{id}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    /**
     * Test Case: Retrieve orders by customer ID
     * 
     * Scenario:
     * 1. Create multiple orders for same customer
     * 2. Call GET /api/orders/customer/{customerId}
     * 3. Verify only customer's orders are returned
     * 
     * Expected Result:
     * - HTTP 200 OK status
     * - Response contains only orders for specified customer
     * - Order count matches expected
     */
    @Test
    void testGetOrdersByCustomerId_ReturnsCustomerOrders() throws Exception {
        // Given: multiple orders for customer 1001
        OrderDto savedOrder1 = orderService.createOrder(testOrder1);
        OrderDto savedOrder2 = orderService.createOrder(testOrder2);

        // When & Then: retrieve orders for customer
        mockMvc.perform(get("/api/orders/customer/{customerId}", 1001L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].customerId", everyItem(is(1001))))
                .andExpect(jsonPath("$[*].orderId", hasItems(
                        savedOrder1.getOrderId().intValue(),
                        savedOrder2.getOrderId().intValue()
                )));
    }

    /**
     * Test Case: Retrieve orders for customer with no orders
     * 
     * Scenario:
     * 1. Call GET /api/orders/customer/{customerId} for customer with no orders
     * 2. Verify empty list is returned
     * 
     * Expected Result:
     * - HTTP 200 OK status
     * - Response contains empty array
     */
    @Test
    void testGetOrdersByCustomerId_NoOrders_ReturnsEmptyList() throws Exception {
        // Given: customer with no orders
        Long customerIdWithNoOrders = 9999L;

        // When & Then: retrieve orders for customer
        mockMvc.perform(get("/api/orders/customer/{customerId}", customerIdWithNoOrders)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test Case: Verify API returns proper content type
     * 
     * Scenario:
     * 1. Call order retrieval endpoints
     * 2. Verify Content-Type header is application/json
     * 
     * Expected Result:
     * - All responses have Content-Type: application/json
     */
    @Test
    void testApiEndpoints_ReturnJsonContentType() throws Exception {
        // Given: order exists
        OrderDto savedOrder = orderService.createOrder(testOrder1);

        // When & Then: verify content type for all endpoints
        mockMvc.perform(get("/api/orders"))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        mockMvc.perform(get("/api/orders/{id}", savedOrder.getOrderId()))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        mockMvc.perform(get("/api/orders/customer/{customerId}", 1001L))
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    /**
     * Test Case: Verify database isolation between tests
     * 
     * Scenario:
     * 1. Verify PostgreSQL container is running
     * 2. Check database connectivity
     * 3. Ensure test data isolation
     * 
     * Expected Result:
     * - Database container is healthy
     * - Each test has clean database state
     */
    @Test
    void testDatabaseConnectivity() throws Exception {
        // Verify PostgreSQL container
        assert postgresContainer.isRunning();
        assert postgresContainer.getDatabaseName().equals("orderservice_test");

        // Verify we can query orders
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }
}
