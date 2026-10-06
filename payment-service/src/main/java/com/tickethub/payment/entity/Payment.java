package com.tickethub.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * ENTITY = a Java class that is mapped to a database table.
 * One object of this class = one row in the table "payments" = one Razorpay ORDER for one booking.
 *
 * NOTE: We never send this class directly to the client (API). We use DTOs for that
 * (see the dto package) - this is one of the TicketHub Spring Boot standards.
 *
 * What changed for Razorpay: the old single "providerTransactionId" is split into the three ids
 * Razorpay really has - order id, payment id, refund id - plus an idempotency key, so that
 * retries can never create two orders for the same request.
 */
@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payments_booking_id", columnList = "booking_id"),
                @Index(name = "idx_payments_status_updated", columnList = "status, updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_provider_order", columnNames = "provider_order_id"),
                @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
        })
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which booking this payment is for. It belongs to the Booking service; we only store its id (no foreign key across services!). */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Where the notification email should go. Travels inside the Kafka event so the Notification service needs no extra call. */
    @Column(name = "customer_email", nullable = false, length = 150)
    private String customerEmail;

    /** Money. ALWAYS BigDecimal for money. We convert to paise only at the Razorpay boundary. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.UNSPECIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Which provider handled it: "RAZORPAY" or "MOCK". */
    @Column(length = 20)
    private String provider;

    /** Razorpay ORDER id (order_xxx). Created by us BEFORE the customer pays. Unique. */
    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    /** Razorpay PAYMENT id (pay_xxx). Known only after the customer pays. Needed for refunds. */
    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    /** Razorpay REFUND id (rfnd_xxx). */
    @Column(name = "provider_refund_id", length = 64)
    private String providerRefundId;

    /** Caller-supplied key: the same key always returns the same payment instead of creating a second order. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** Why it failed (latest failed attempt), or a note such as "Booking cancelled". */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Payment() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
