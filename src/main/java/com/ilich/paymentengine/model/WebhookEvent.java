package com.ilich.paymentengine.model;

import com.ilich.paymentengine.model.enums.WebhookEventStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookEventStatus status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public WebhookEvent() {
    }

    public WebhookEvent(String eventId, String provider, String eventType, String payload) {
        this.eventId = eventId;
        this.provider = provider;
        this.eventType = eventType;
        this.payload = payload;
        this.status = WebhookEventStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public void markProcessed() {
        this.status = WebhookEventStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.status = WebhookEventStatus.FAILED;
        this.processedAt = Instant.now();
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookEventStatus status) {
        this.status = status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
