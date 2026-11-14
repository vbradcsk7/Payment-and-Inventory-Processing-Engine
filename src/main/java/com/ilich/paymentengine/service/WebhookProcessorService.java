package com.ilich.paymentengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.dto.request.PaymentWebhookEvent;
import com.ilich.paymentengine.dto.response.WebhookProcessResponse;
import com.ilich.paymentengine.exception.InvalidOrderStatusException;
import com.ilich.paymentengine.exception.OrderNotFoundException;
import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.OrderItem;
import com.ilich.paymentengine.model.WebhookEvent;
import com.ilich.paymentengine.model.enums.OrderStatus;
import com.ilich.paymentengine.model.enums.WebhookEventStatus;
import com.ilich.paymentengine.repository.OrderRepository;
import com.ilich.paymentengine.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WebhookProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessorService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public WebhookProcessorService(WebhookEventRepository webhookEventRepository,
                                   OrderRepository orderRepository,
                                   InventoryService inventoryService,
                                   ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes inbound payment gateway webhooks idempotently.
     * Prevents double processing by tracking event_id and locking the target order.
     */
    @Transactional(rollbackFor = Exception.class)
    public WebhookProcessResponse processWebhook(PaymentWebhookEvent event, String rawPayload) {
        log.info("Processing webhook event '{}' of type '{}' for order '{}'",
                event.eventId(), event.eventType(), event.orderId());

        // 1. Idempotency Check: if event was already recorded, return 200 OK immediately
        if (webhookEventRepository.existsByEventId(event.eventId())) {
            log.info("Webhook event '{}' was already processed. Idempotent skip.", event.eventId());
            return new WebhookProcessResponse(
                    event.eventId(),
                    WebhookEventStatus.PROCESSED,
                    "Event already processed (idempotent skip)",
                    Instant.now()
            );
        }

        // 2. Persist audit log of the webhook event
        WebhookEvent initialEvent = new WebhookEvent(
                event.eventId(),
                event.provider(),
                event.eventType(),
                rawPayload
        );

        WebhookEvent savedEvent;
        try {
            savedEvent = webhookEventRepository.saveAndFlush(initialEvent);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate webhook event '{}' received", event.eventId());
            return new WebhookProcessResponse(
                    event.eventId(),
                    WebhookEventStatus.PROCESSED,
                    "Event already processed concurrently (idempotent skip)",
                    Instant.now()
            );
        }

        final WebhookEvent webhookEvent = savedEvent;

        // 3. Lock target order for transactional transition
        Order order = orderRepository.findByIdWithPessimisticLock(event.orderId())
                .orElseThrow(() -> {
                    webhookEvent.markFailed();
                    webhookEventRepository.save(webhookEvent);
                    return new OrderNotFoundException(event.orderId());
                });

        // 4. Handle event types
        switch (event.eventType()) {
            case "payment.succeeded" -> handlePaymentSucceeded(order, webhookEvent);
            case "payment.failed" -> handlePaymentFailed(order, webhookEvent);
            default -> {
                log.warn("Unrecognized or unhandled webhook event type: {}", event.eventType());
                webhookEvent.markProcessed();
            }
        }

        webhookEventRepository.save(webhookEvent);
        return new WebhookProcessResponse(
                event.eventId(),
                webhookEvent.getStatus(),
                "Webhook processed successfully for order: " + order.getId(),
                webhookEvent.getProcessedAt()
        );
    }

    private void handlePaymentSucceeded(Order order, WebhookEvent webhookEvent) {
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order {} is already marked as PAID. Nothing to do.", order.getId());
            webhookEvent.markProcessed();
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            webhookEvent.markFailed();
            throw new InvalidOrderStatusException(
                    "Cannot mark order " + order.getId() + " as PAID because it is in state: " + order.getStatus()
            );
        }

        // Transition order to PAID
        order.setStatus(OrderStatus.PAID);

        // Commit reserved stock permanently
        for (OrderItem item : order.getItems()) {
            inventoryService.commitReservedStock(item.getProduct().getId(), item.getQuantity());
        }

        orderRepository.save(order);
        webhookEvent.markProcessed();
        log.info("Order {} transitioned to PAID. Stock successfully committed.", order.getId());
    }

    private void handlePaymentFailed(Order order, WebhookEvent webhookEvent) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            webhookEvent.markProcessed();
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            // Cancel order and return reserved stock to available stock
            order.setStatus(OrderStatus.CANCELLED);
            for (OrderItem item : order.getItems()) {
                inventoryService.releaseReservedStock(item.getProduct().getId(), item.getQuantity());
            }
            orderRepository.save(order);
            log.info("Order {} payment failed. Order CANCELLED and stock released.", order.getId());
        }

        webhookEvent.markProcessed();
    }
}
