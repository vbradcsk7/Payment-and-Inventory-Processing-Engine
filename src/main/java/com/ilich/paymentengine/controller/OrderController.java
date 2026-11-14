package com.ilich.paymentengine.controller;

import com.ilich.paymentengine.dto.request.CreateOrderRequest;
import com.ilich.paymentengine.dto.response.OrderResponse;
import com.ilich.paymentengine.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Endpoints for creating and retrieving orders with idempotency control")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(
            summary = "Create an order idempotently",
            description = "Creates a new order and reserves inventory using pessimistic locking. " +
                    "If a request with the same Idempotency-Key has already been processed, the original order is returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "200", description = "Idempotent request: Existing order returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing Idempotency-Key"),
            @ApiResponse(responseCode = "409", description = "Insufficient inventory stock")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Unique client-generated idempotency key (UUID)", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    @Operation(summary = "List all orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
