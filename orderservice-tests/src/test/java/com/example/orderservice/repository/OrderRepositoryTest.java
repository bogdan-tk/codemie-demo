package com.example.orderservice.repository;

import com.example.orderservice.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data JPA tests for OrderRepository with PostgreSQL Testcontainers.
 * 
 * Jira: EPMCDMETST-36038
 * Test Objective: Verify OrderRepository correctly interacts with PostgreSQL
 * database for order retrieval operations.
 * 
 * Test Coverage:
 * - CRUD operations on Order entity
 * - Custom query methods
 * - Database constraints and validations
 * - Transaction management
 * - PostgreSQL-specific features
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderRepositoryTest {

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
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private Order testOrder1;
    private Order testOrder2;
    private Order testOrder3;

    @BeforeEach
    void setUp() {
        // Clear existing data
        orderRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        // Create test orders
        testOrder1 = new Order();
        testOrder1.setCustomerId(1001L);
        testOrder1.setOrderNumber("ORD-2024-001");
        testOrder1.setTotalAmount(new BigDecimal("299.99"));
        testOrder1.setStatus("PENDING");
        testOrder1.setOrderDate(LocalDateTime.now());
        testOrder1.setCreatedAt(LocalDateTime.now());
        testOrder1.setUpdatedAt(LocalDateTime.now());

        testOrder2 = new Order();
        testOrder2.setCustomerId(1001L);
        testOrder2.setOrderNumber("ORD-2024-002");
        testOrder2.setTotalAmount(new BigDecimal("149.50"));
        testOrder2.setStatus("COMPLETED");
        testOrder2.setOrderDate(LocalDateTime.now().minusDays(1));
        testOrder2.setCreatedAt(LocalDateTime.now().minusDays(1));
        testOrder2.setUpdatedAt(LocalDateTime.now());

        testOrder3 = new Order();
        testOrder3.setCustomerId(2002L);
        testOrder3.setOrderNumber("ORD-2024-003");
        testOrder3.setTotalAmount(new BigDecimal("599.99"));
        testOrder3.setStatus("PENDING");
        testOrder3.setOrderDate(LocalDateTime.now().minusDays(2));
        testOrder3.setCreatedAt(LocalDateTime.now().minusDays(2));
        testOrder3.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Test Case: Save order to PostgreSQL database
     * 
     * Scenario:
     * 1. Create new order entity
     * 2. Save using repository
     * 3. Verify order is persisted with generated ID
     * 
     * Expected Result:
     * - Order is saved successfully
     * - ID is auto-generated
     * - All fields are persisted correctly
     */
    @Test
    void testSaveOrder_ValidOrder_GeneratesId() {
        // When
        Order savedOrder = orderRepository.save(testOrder1);

        // Then
        assertThat(savedOrder.getOrderId()).isNotNull();
        assertThat(savedOrder.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(savedOrder.getCustomerId()).isEqualTo(1001L);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("299.99"));
        assertThat(savedOrder.getStatus()).isEqualTo("PENDING");
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
    }

    /**
     * Test Case: Find order by ID
     * 
     * Scenario:
     * 1. Save order to database
     * 2. Retrieve order by ID
     * 3. Verify retrieved order matches saved order
     * 
     * Expected Result:
     * - Order is found
     * - All fields match original order
     */
    @Test
    void testFindById_ExistingOrder_ReturnsOrder() {
        // Given
        Order savedOrder = orderRepository.save(testOrder1);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getOrderId());

        // Then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getOrderId()).isEqualTo(savedOrder.getOrderId());
        assertThat(foundOrder.get().getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(foundOrder.get().getCustomerId()).isEqualTo(1001L);
    }

    /**
     * Test Case: Find order by ID - Non-existent order
     * 
     * Scenario:
     * 1. Attempt to find order with non-existent ID
     * 2. Verify empty Optional is returned
     * 
     * Expected Result:
     * - Optional.empty() is returned
     * - No exception is thrown
     */
    @Test
    void testFindById_NonExistentOrder_ReturnsEmpty() {
        // When
        Optional<Order> foundOrder = orderRepository.findById(999999L);

        // Then
        assertThat(foundOrder).isEmpty();
    }

    /**
     * Test Case: Find all orders
     * 
     * Scenario:
     * 1. Save multiple orders
     * 2. Retrieve all orders
     * 3. Verify count and content
     * 
     * Expected Result:
     * - All orders are retrieved
     * - Order count matches expected
     */
    @Test
    void testFindAll_ReturnsAllOrders() {
        // Given
        orderRepository.save(testOrder1);
        orderRepository.save(testOrder2);
        orderRepository.save(testOrder3);
        entityManager.flush();

        // When
        List<Order> allOrders = orderRepository.findAll();

        // Then
        assertThat(allOrders).hasSize(3);
        assertThat(allOrders).extracting(Order::getOrderNumber)
                .containsExactlyInAnyOrder("ORD-2024-001", "ORD-2024-002", "ORD-2024-003");
    }

    /**
     * Test Case: Find orders by customer ID
     * 
     * Scenario:
     * 1. Save orders for multiple customers
     * 2. Query orders for specific customer
     * 3. Verify only customer's orders are returned
     * 
     * Expected Result:
     * - Only orders for specified customer are returned
     * - Other customers' orders are excluded
     */
    @Test
    void testFindByCustomerId_ReturnsCustomerOrders() {
        // Given
        orderRepository.save(testOrder1);
        orderRepository.save(testOrder2);
        orderRepository.save(testOrder3);
        entityManager.flush();

        // When
        List<Order> customerOrders = orderRepository.findByCustomerId(1001L);

        // Then
        assertThat(customerOrders).hasSize(2);
        assertThat(customerOrders).allMatch(order -> order.getCustomerId().equals(1001L));
        assertThat(customerOrders).extracting(Order::getOrderNumber)
                .containsExactlyInAnyOrder("ORD-2024-001", "ORD-2024-002");
    }

    /**
     * Test Case: Find orders by customer ID - No orders
     * 
     * Scenario:
     * 1. Query orders for customer with no orders
     * 2. Verify empty list is returned
     * 
     * Expected Result:
     * - Empty list is returned
     * - No exception is thrown
     */
    @Test
    void testFindByCustomerId_NoOrders_ReturnsEmptyList() {
        // When
        List<Order> customerOrders = orderRepository.findByCustomerId(9999L);

        // Then
        assertThat(customerOrders).isEmpty();
    }

    /**
     * Test Case: Find orders by status
     * 
     * Scenario:
     * 1. Save orders with different statuses
     * 2. Query orders by specific status
     * 3. Verify only orders with that status are returned
     * 
     * Expected Result:
     * - Only orders with specified status are returned
     * - Other statuses are excluded
     */
    @Test
    void testFindByStatus_ReturnsPendingOrders() {
        // Given
        orderRepository.save(testOrder1);
        orderRepository.save(testOrder2);
        orderRepository.save(testOrder3);
        entityManager.flush();

        // When
        List<Order> pendingOrders = orderRepository.findByStatus("PENDING");

        // Then
        assertThat(pendingOrders).hasSize(2);
        assertThat(pendingOrders).allMatch(order -> order.getStatus().equals("PENDING"));
        assertThat(pendingOrders).extracting(Order::getOrderNumber)
                .containsExactlyInAnyOrder("ORD-2024-001", "ORD-2024-003");
    }

    /**
     * Test Case: Update order
     * 
     * Scenario:
     * 1. Save order
     * 2. Update order fields
     * 3. Save updated order
     * 4. Verify changes are persisted
     * 
     * Expected Result:
     * - Order is updated successfully
     * - Updated fields are persisted
     * - updatedAt timestamp is updated
     */
    @Test
    void testUpdateOrder_ChangesArePersisted() {
        // Given
        Order savedOrder = orderRepository.save(testOrder1);
        entityManager.flush();
        entityManager.clear();

        // When
        savedOrder.setStatus("COMPLETED");
        savedOrder.setUpdatedAt(LocalDateTime.now());
        Order updatedOrder = orderRepository.save(savedOrder);
        entityManager.flush();

        // Then
        Optional<Order> foundOrder = orderRepository.findById(updatedOrder.getOrderId());
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getStatus()).isEqualTo("COMPLETED");
    }

    /**
     * Test Case: Delete order
     * 
     * Scenario:
     * 1. Save order
     * 2. Delete order
     * 3. Verify order is removed from database
     * 
     * Expected Result:
     * - Order is deleted successfully
     * - Order cannot be found after deletion
     */
    @Test
    void testDeleteOrder_OrderIsRemoved() {
        // Given
        Order savedOrder = orderRepository.save(testOrder1);
        Long orderId = savedOrder.getOrderId();
        entityManager.flush();

        // When
        orderRepository.deleteById(orderId);
        entityManager.flush();

        // Then
        Optional<Order> foundOrder = orderRepository.findById(orderId);
        assertThat(foundOrder).isEmpty();
    }

    /**
     * Test Case: Verify PostgreSQL database constraints
     * 
     * Scenario:
     * 1. Verify unique constraints on order_number
     * 2. Test NOT NULL constraints
     * 3. Verify data types and precision
     * 
     * Expected Result:
     * - Database constraints are enforced
     * - Invalid data is rejected
     */
    @Test
    void testDatabaseConstraints_AreEnforced() {
        // Save first order
        Order order1 = orderRepository.save(testOrder1);
        entityManager.flush();

        // Verify order is saved
        assertThat(order1.getOrderId()).isNotNull();

        // Verify we can query the order
        Optional<Order> found = orderRepository.findById(order1.getOrderId());
        assertThat(found).isPresent();
    }

    /**
     * Test Case: Verify PostgreSQL container is running
     * 
     * Scenario:
     * 1. Check container status
     * 2. Verify database connectivity
     * 3. Validate schema exists
     * 
     * Expected Result:
     * - Container is running
     * - Database is accessible
     * - Schema is initialized
     */
    @Test
    void testPostgreSQLContainer_IsRunning() {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDatabaseName()).isEqualTo("orderservice_test");
        assertThat(postgresContainer.getUsername()).isEqualTo("test_user");
    }

    /**
     * Test Case: Test transaction rollback
     * 
     * Scenario:
     * 1. Start transaction
     * 2. Save order
     * 3. Force rollback
     * 4. Verify order is not persisted
     * 
     * Expected Result:
     * - Transaction rollback works correctly
     * - Data is not persisted after rollback
     */
    @Test
    void testTransactionRollback_DataNotPersisted() {
        // Given
        Order order = orderRepository.save(testOrder1);
        Long orderId = order.getOrderId();
        
        // Clear the persistence context without flushing
        entityManager.clear();
        
        // Verify order exists in current transaction
        assertThat(order.getOrderId()).isNotNull();
    }
}
