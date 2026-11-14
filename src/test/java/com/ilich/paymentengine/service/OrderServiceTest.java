package com.ilich.paymentengine.service;

import com.ilich.paymentengine.dto.request.CreateOrderRequest;
import com.ilich.paymentengine.dto.request.OrderItemRequest;
import com.ilich.paymentengine.dto.response.OrderResponse;
import com.ilich.paymentengine.exception.InsufficientStockException;
import com.ilich.paymentengine.exception.MissingIdempotencyKeyException;
import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.Product;
import com.ilich.paymentengine.model.enums.OrderStatus;
import com.ilich.paymentengine.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderService orderService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product("SKU-TEST-1", "Test Product", 10, BigDecimal.valueOf(100.00));
        testProduct.setId(1L);
    }

    @Test
    @DisplayName("Should return existing order when Idempotency-Key already exists (Idempotent Safe Replay)")
    void shouldReturnExistingOrderOnIdempotentRequest() {
        String idempotencyKey = "idem-key-12345";
        Order existingOrder = new Order(idempotencyKey, "customer@example.com");
        existingOrder.setId(UUID.randomUUID());
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setTotalAmount(BigDecimal.valueOf(100.00));

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(1L, 1))
        );

        OrderResponse response = orderService.createOrder(idempotencyKey, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingOrder.getId());
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);

        // Crucial: Inventory reservation must NOT be called again!
        verify(inventoryService, never()).reserveStock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should create new order and reserve inventory when Idempotency-Key is new")
    void shouldCreateNewOrderSuccessfully() {
        String idempotencyKey = "new-idem-key-67890";
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(inventoryService.reserveStock(1L, 2)).thenReturn(testProduct);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(1L, 2))
        );

        OrderResponse response = orderService.createOrder(idempotencyKey, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.customerEmail()).isEqualTo("customer@example.com");
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200.00));

        verify(inventoryService, times(1)).reserveStock(1L, 2);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw MissingIdempotencyKeyException if header is null or blank")
    void shouldThrowExceptionWhenIdempotencyKeyIsMissing() {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(1L, 1))
        );

        assertThatThrownBy(() -> orderService.createOrder(null, request))
                .isInstanceOf(MissingIdempotencyKeyException.class);

        assertThatThrownBy(() -> orderService.createOrder("   ", request))
                .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("Should propagate InsufficientStockException when stock is unavailable")
    void shouldFailWhenInsufficientStock() {
        String idempotencyKey = "insufficient-stock-key";
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(inventoryService.reserveStock(1L, 50))
                .thenThrow(new InsufficientStockException("Insufficient stock"));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(1L, 50))
        );

        assertThatThrownBy(() -> orderService.createOrder(idempotencyKey, request))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }
}
