package com.ilich.paymentengine.service;

import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.OrderItem;
import com.ilich.paymentengine.model.enums.OrderStatus;
import com.ilich.paymentengine.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final long expirationMinutes;

    public OrderExpirationJob(OrderRepository orderRepository,
                              InventoryService inventoryService,
                              @Value("${payment.orders.expiration-minutes:15}") long expirationMinutes) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Periodically runs to expire unpaid orders exceeding the expiration TTL.
     * Restores reserved inventory back to available inventory.
     */
    @Scheduled(cron = "${payment.orders.expiration-cron:0 * * * * *}")
    @Transactional
    public void expirePendingOrders() {
        Instant cutoffTime = Instant.now().minus(Duration.ofMinutes(expirationMinutes));
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoffTime);

        if (!expiredOrders.isEmpty()) {
            log.info("Found {} pending orders older than {} minutes to expire", expiredOrders.size(), expirationMinutes);

            for (Order order : expiredOrders) {
                try {
                    order.setStatus(OrderStatus.EXPIRED);
                    for (OrderItem item : order.getItems()) {
                        inventoryService.releaseReservedStock(item.getProduct().getId(), item.getQuantity());
                    }
                    orderRepository.save(order);
                    log.info("Order {} expired. Reserved inventory restored to available.", order.getId());
                } catch (Exception e) {
                    log.error("Failed to expire order ID {}: {}", order.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
