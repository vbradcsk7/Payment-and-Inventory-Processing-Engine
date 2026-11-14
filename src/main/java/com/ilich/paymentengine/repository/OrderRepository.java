package com.ilich.paymentengine.repository;

import com.ilich.paymentengine.model.Order;
import com.ilich.paymentengine.model.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithPessimisticLock(@Param("id") UUID id);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :cutoffTime")
    List<Order> findByStatusAndCreatedAtBefore(
            @Param("status") OrderStatus status,
            @Param("cutoffTime") Instant cutoffTime
    );
}
