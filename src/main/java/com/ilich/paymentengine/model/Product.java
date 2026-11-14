package com.ilich.paymentengine.model;

import com.ilich.paymentengine.exception.InsufficientStockException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "stock_available", nullable = false)
    private int stockAvailable;

    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product() {
    }

    public Product(String sku, String name, int stockAvailable, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.stockAvailable = stockAvailable;
        this.stockReserved = 0;
        this.price = price;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Reserves available stock when an order is created.
     * Decrements stockAvailable and increments stockReserved.
     */
    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be greater than zero");
        }
        if (this.stockAvailable < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product SKU " + sku + ". Available: " + stockAvailable + ", Requested: " + quantity
            );
        }
        this.stockAvailable -= quantity;
        this.stockReserved += quantity;
    }

    /**
     * Commits reserved stock upon successful payment.
     * Decrements stockReserved permanently.
     */
    public void commitReservedStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to commit must be greater than zero");
        }
        if (this.stockReserved < quantity) {
            throw new IllegalStateException(
                    "Cannot commit " + quantity + " units for SKU " + sku + ". Reserved is only: " + stockReserved
            );
        }
        this.stockReserved -= quantity;
    }

    /**
     * Releases reserved stock back to available stock if order expires or payment fails.
     */
    public void releaseReservedStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to release must be greater than zero");
        }
        if (this.stockReserved < quantity) {
            throw new IllegalStateException(
                    "Cannot release " + quantity + " units for SKU " + sku + ". Reserved is only: " + stockReserved
            );
        }
        this.stockReserved -= quantity;
        this.stockAvailable += quantity;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStockAvailable() {
        return stockAvailable;
    }

    public void setStockAvailable(int stockAvailable) {
        this.stockAvailable = stockAvailable;
    }

    public int getStockReserved() {
        return stockReserved;
    }

    public void setStockReserved(int stockReserved) {
        this.stockReserved = stockReserved;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
