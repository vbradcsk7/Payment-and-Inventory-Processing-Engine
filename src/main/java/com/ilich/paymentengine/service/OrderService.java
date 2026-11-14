package com.ilich.paymentengine.service;

import com.ilich.paymentengine.dto.request.CreateOrderRequest;
import com.ilich.paymentengine.dto.request.OrderItemRequest;
import com.ilich.paymentengine.dto.response.OrderResponse;
import com.ilich.paymentengine.exception.MissingIdempotencyKeyException;
import com.ilich.paymentengine.exception.OrderNotFoundException;
import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.Product;
import com.ilich.paymentengine.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderService(OrderRepository orderRepository, InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * Creates an order with strict Idempotency and Pessimistic Concurrency Control.
     * If an order with the given idempotencyKey already exists, it is returned safely.
     */
    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Header 'Idempotency-Key' is mandatory for creating orders");
        }

        // Fast-path lookup for existing idempotency key
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            log.info("Idempotent hit for key '{}'. Returning existing order ID {}", idempotencyKey, existingOrder.get().getId());
            return OrderResponse.fromEntity(existingOrder.get());
        }

        try {
            return executeCreateOrder(idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            // Concurrent race condition safeguard: Another thread committed the same idempotency key
            log.warn("Concurrent duplicate idempotency key '{}' caught by DB constraint. Fetching committed order.", idempotencyKey);
            return orderRepository.findByIdempotencyKey(idempotencyKey)
                    .map(OrderResponse::fromEntity)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected OrderResponse executeCreateOrder(String idempotencyKey, CreateOrderRequest request) {
        log.info("Creating new order for customer '{}' with Idempotency-Key '{}'", request.customerEmail(), idempotencyKey);

        Order order = new Order(idempotencyKey, request.customerEmail());

        for (OrderItemRequest itemRequest : request.items()) {
            // Acquires pessimistic lock and decrements available stock, increments reserved stock
            Product lockedProduct = inventoryService.reserveStock(itemRequest.productId(), itemRequest.quantity());
            order.addOrderItem(lockedProduct, itemRequest.quantity());
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order successfully created with ID: {} and total: {}", savedOrder.getId(), savedOrder.getTotalAmount());
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::fromEntity)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }
}
