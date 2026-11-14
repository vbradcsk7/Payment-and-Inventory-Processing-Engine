package com.ilich.paymentengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.dto.request.PaymentWebhookEvent;
import com.ilich.paymentengine.dto.response.WebhookProcessResponse;
import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.Product;
import com.ilich.paymentengine.model.WebhookEvent;
import com.ilich.paymentengine.model.enums.OrderStatus;
import com.ilich.paymentengine.model.enums.WebhookEventStatus;
import com.ilich.paymentengine.repository.OrderRepository;
import com.ilich.paymentengine.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookProcessorServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WebhookProcessorService webhookProcessorService;

    private Order testOrder;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product("SKU-1", "Product 1", 10, BigDecimal.valueOf(100.00));
        testProduct.setId(10L);

        testOrder = new Order("key-123", "buyer@example.com");
        testOrder.setId(UUID.randomUUID());
        testOrder.addOrderItem(testProduct, 2);
    }

    @Test
    @DisplayName("Should detect duplicate webhook event and return idempotent skip without processing order again")
    void shouldSkipDuplicateWebhookEvent() {
        String eventId = "evt_duplicate_001";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(true);

        PaymentWebhookEvent event = new PaymentWebhookEvent(
                eventId, "STRIPE", "payment.succeeded", testOrder.getId(), BigDecimal.valueOf(200.00), "USD"
        );

        WebhookProcessResponse response = webhookProcessorService.processWebhook(event, "{}");

        assertThat(response).isNotNull();
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(response.message()).contains("idempotent skip");

        // Assert no order lookups or inventory commits
        verify(orderRepository, never()).findByIdWithPessimisticLock(any());
        verify(inventoryService, never()).commitReservedStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Should process payment.succeeded webhook, transition order to PAID and commit reserved stock")
    void shouldProcessPaymentSucceeded() {
        String eventId = "evt_success_002";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByIdWithPessimisticLock(testOrder.getId())).thenReturn(Optional.of(testOrder));

        PaymentWebhookEvent event = new PaymentWebhookEvent(
                eventId, "STRIPE", "payment.succeeded", testOrder.getId(), BigDecimal.valueOf(200.00), "USD"
        );

        WebhookProcessResponse response = webhookProcessorService.processWebhook(event, "{}");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(inventoryService, times(1)).commitReservedStock(10L, 2);
        verify(orderRepository, times(1)).save(testOrder);
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should process payment.failed webhook, transition order to CANCELLED and release reserved stock")
    void shouldProcessPaymentFailed() {
        String eventId = "evt_failed_003";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByIdWithPessimisticLock(testOrder.getId())).thenReturn(Optional.of(testOrder));

        PaymentWebhookEvent event = new PaymentWebhookEvent(
                eventId, "STRIPE", "payment.failed", testOrder.getId(), BigDecimal.valueOf(200.00), "USD"
        );

        WebhookProcessResponse response = webhookProcessorService.processWebhook(event, "{}");

        assertThat(response).isNotNull();
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(inventoryService, times(1)).releaseReservedStock(10L, 2);
        verify(orderRepository, times(1)).save(testOrder);
    }
}
