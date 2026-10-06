package com.tickethub.payment.repository;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITORY = the class that talks to the database.
 * We write NO implementation: Spring Data creates it at startup.
 * Custom queries: Spring reads the METHOD NAME and builds the SQL.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // SELECT * FROM payments WHERE booking_id = ? ORDER BY created_at DESC
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    // "has this booking already been paid?"
    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    // Retry-safety: the same idempotency key must always lead to the same payment row
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // Razorpay tells us the ORDER id in verify calls and webhooks - this is how we find our row
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    // Refund webhooks identify the PAYMENT id, not the order id
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    // "is there already an open order for this booking?" -> reuse it instead of creating a second one (no double charge)
    Optional<Payment> findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(Long bookingId, Collection<PaymentStatus> statuses);

    /**
     * Loads the row AND locks it (SELECT ... FOR UPDATE) until the surrounding transaction ends.
     * Why: a customer's verify call, Razorpay's webhook and our background sync can all arrive at the
     * same moment for the same payment. With the lock they take turns, so a payment can never be
     * "completed" twice and the Kafka event is never published twice.
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    // For the background reconciliation job: quiet PENDING payments that are not too old
    List<Payment> findTop50ByStatusAndUpdatedAtBeforeAndCreatedAtAfterOrderByCreatedAtAsc(
            PaymentStatus status, Instant updatedBefore, Instant createdAfter);
}
