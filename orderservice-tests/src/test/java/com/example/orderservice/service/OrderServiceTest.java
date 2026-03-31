package com.example.orderservice.service;

import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService business logic.
 * 
 * Jira: EPMCDMETST-36038
 * Test Objective: Verify OrderService business logic handles order retrieval
 * operations correctly with proper error handling.
 * 
 * Test Coverage:
 * - Order retrieval by ID
 * - Order retrieval by customer ID
 * - Retrieve all orders
 * - Error handling for non-existent orders
 * - Data mapping between entities and DTOs
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder1;
    private Order testOrder2;

    @BeforeEach
    void setUp() {
        testOrder1 = new Order();
        testOrder1.setOrderId(1L);
        testOrder1.setCustomerId(1001L);
        testOrder1.setOrderNumber("ORD-2024-001");
        testOrder1.setTotalAmount(new BigDecimal("299.99"));
        testOrder1.setStatus("PENDING");
        testOrder1.setOrderDate(LocalDateTime.now());
        testOrder1.setCreatedAt(LocalDateTime.now());
        testOrder1.setUpdatedAt(LocalDateTime.now());

        testOrder2 = new Order();
        testOrder2.setOrderId(2L);
        testOrder2.setCustomerId(1001L);
        testOrder2.setOrderNumber("ORD-2024-002");
        testOrder2.setTotalAmount(new BigDecimal("149.50"));
        testOrder2.setStatus("COMPLETED");
        testOrder2.setOrderDate(LocalDateTime.now().minusDays(1));
        testOrder2.setCreatedAt(LocalDateTime.now().minusDays(1));
        testOrder2.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Test Case: Retrieve order by ID - Success scenario
     * 
     * Scenario:
     * 1. Mock repository to return order
     * 2. Call service.getOrderById()
     * 3. Verify order is returned with correct data
     * 
     * Expected Result:
     * - Order is retrieved successfully
     * - All fields are mapped correctly from entity to DTO
     * - Repository is called exactly once
     */
    @Test
    void testGetOrderById_ValidId_ReturnsOrder() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder1));

        // When
        OrderDto result = orderService.getOrderById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(result.getCustomerId()).isEqualTo(1001L);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("299.99"));
        assertThat(result.getStatus()).isEqualTo("PENDING");
        
        verify(orderRepository, times(1)).findById(1L);
    }

    /**
     * Test Case: Retrieve order by ID - Order not found
     * 
     * Scenario:
     * 1. Mock repository to return empty Optional
     * 2. Call service.getOrderById() with non-existent ID
     * 3. Verify appropriate exception is thrown
     * 
     * Expected Result:
     * - OrderNotFoundException is thrown
     * - Exception message contains order ID
     * - Repository is called exactly once
     */
    @Test
    void testGetOrderById_InvalidId_ThrowsException() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with id: 999");
        
        verify(orderRepository, times(1)).findById(999L);
    }

    /**
     * Test Case: Retrieve all orders
     * 
     * Scenario:
     * 1. Mock repository to return list of orders
     * 2. Call service.getAllOrders()
     * 3. Verify all orders are returned
     * 
     * Expected Result:
     * - All orders are retrieved
     * - Order count matches expected
     * - Data is correctly mapped to DTOs
     */
    @Test
    void testGetAllOrders_ReturnsAllOrders() {
        // Given
        List<Order> orders = Arrays.asList(testOrder1, testOrder2);
        when(orderRepository.findAll()).thenReturn(orders);

        // When
        List<OrderDto> result = orderService.getAllOrders();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrderId()).isEqualTo(1L);
        assertThat(result.get(1).getOrderId()).isEqualTo(2L);
        assertThat(result.get(0).getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(result.get(1).getOrderNumber()).isEqualTo("ORD-2024-002");
        
        verify(orderRepository, times(1)).findAll();
    }

    /**
     * Test Case: Retrieve all orders - Empty database
     * 
     * Scenario:
     * 1. Mock repository to return empty list
     * 2. Call service.getAllOrders()
     * 3. Verify empty list is returned
     * 
     * Expected Result:
     * - Empty list is returned (not null)
     * - No exception is thrown
     */
    @Test
    void testGetAllOrders_EmptyDatabase_ReturnsEmptyList() {
        // Given
        when(orderRepository.findAll()).thenReturn(Arrays.asList());

        // When
        List<OrderDto> result = orderService.getAllOrders();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(orderRepository, times(1)).findAll();
    }

    /**
     * Test Case: Retrieve orders by customer ID
     * 
     * Scenario:
     * 1. Mock repository to return customer's orders
     * 2. Call service.getOrdersByCustomerId()
     * 3. Verify only customer's orders are returned
     * 
     * Expected Result:
     * - All orders for customer are retrieved
     * - All returned orders belong to specified customer
     * - Order data is correctly mapped
     */
    @Test
    void testGetOrdersByCustomerId_ReturnsCustomerOrders() {
        // Given
        List<Order> customerOrders = Arrays.asList(testOrder1, testOrder2);
        when(orderRepository.findByCustomerId(1001L)).thenReturn(customerOrders);

        // When
        List<OrderDto> result = orderService.getOrdersByCustomerId(1001L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(order -> order.getCustomerId().equals(1001L));
        assertThat(result.get(0).getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(result.get(1).getOrderNumber()).isEqualTo("ORD-2024-002");
        
        verify(orderRepository, times(1)).findByCustomerId(1001L);
    }

    /**
     * Test Case: Retrieve orders by customer ID - No orders found
     * 
     * Scenario:
     * 1. Mock repository to return empty list
     * 2. Call service.getOrdersByCustomerId()
     * 3. Verify empty list is returned
     * 
     * Expected Result:
     * - Empty list is returned
     * - No exception is thrown
     */
    @Test
    void testGetOrdersByCustomerId_NoOrders_ReturnsEmptyList() {
        // Given
        when(orderRepository.findByCustomerId(9999L)).thenReturn(Arrays.asList());

        // When
        List<OrderDto> result = orderService.getOrdersByCustomerId(9999L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(orderRepository, times(1)).findByCustomerId(9999L);
    }

    /**
     * Test Case: Create new order
     * 
     * Scenario:
     * 1. Prepare order DTO
     * 2. Mock repository save operation
     * 3. Call service.createOrder()
     * 4. Verify order is saved with generated ID
     * 
     * Expected Result:
     * - Order is saved successfully
     * - Generated ID is returned
     * - All fields are persisted correctly
     */
    @Test
    void testCreateOrder_ValidOrder_ReturnsSavedOrder() {
        // Given
        OrderDto orderDto = new OrderDto();
        orderDto.setCustomerId(1001L);
        orderDto.setOrderNumber("ORD-2024-003");
        orderDto.setTotalAmount(new BigDecimal("599.99"));
        orderDto.setStatus("PENDING");
        orderDto.setOrderDate(LocalDateTime.now());

        Order savedOrder = new Order();
        savedOrder.setOrderId(3L);
        savedOrder.setCustomerId(orderDto.getCustomerId());
        savedOrder.setOrderNumber(orderDto.getOrderNumber());
        savedOrder.setTotalAmount(orderDto.getTotalAmount());
        savedOrder.setStatus(orderDto.getStatus());
        savedOrder.setOrderDate(orderDto.getOrderDate());
        savedOrder.setCreatedAt(LocalDateTime.now());
        savedOrder.setUpdatedAt(LocalDateTime.now());

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // When
        OrderDto result = orderService.createOrder(orderDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(3L);
        assertThat(result.getOrderNumber()).isEqualTo("ORD-2024-003");
        assertThat(result.getCustomerId()).isEqualTo(1001L);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("599.99"));
        
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    /**
     * Test Case: Verify service handles null values gracefully
     * 
     * Scenario:
     * 1. Call service methods with null parameters
     * 2. Verify appropriate exceptions are thrown
     * 
     * Expected Result:
     * - IllegalArgumentException is thrown for null inputs
     * - Error messages are descriptive
     */
    @Test
    void testServiceMethods_NullParameters_ThrowsException() {
        // Test null order ID
        assertThatThrownBy(() -> orderService.getOrderById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order ID cannot be null");

        // Test null customer ID
        assertThatThrownBy(() -> orderService.getOrdersByCustomerId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer ID cannot be null");

        // Test null order DTO
        assertThatThrownBy(() -> orderService.createOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order cannot be null");
    }
}
