package com.tickethub.payment.service;

import com.tickethub.payment.config.PaymentProperties;
import com.tickethub.payment.dto.PaymentEvent;
import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.dto.VerifyPaymentRequest;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentMethod;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.exception.DuplicatePaymentException;
import com.tickethub.payment.exception.IdempotencyKeyReuseException;
import com.tickethub.payment.exception.InvalidPaymentRequestException;
import com.tickethub.payment.exception.InvalidPaymentStateException;
import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.exception.PaymentNotFoundException;
import com.tickethub.payment.exception.PaymentProviderException;
import com.tickethub.payment.exception.PaymentVerificationException;
import com.tickethub.payment.gateway.AmountUtil;
import com.tickethub.payment.gateway.GatewayOrder;
import com.tickethub.payment.gateway.GatewayPayment;
import com.tickethub.payment.gateway.GatewayRefund;
import com.tickethub.payment.gateway.PaymentGateway;
import com.tickethub.payment.kafka.PaymentEventPublisher;
import com.tickethub.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SERVICE = the BRAIN. All business rules live here (not in the controller).
 *
 * The Razorpay life of a payment, and the method that handles each step:
 *
 *   Booking asks for a payment       -> createPayment   : we create a Razorpay ORDER, status PENDING
 *   Customer pays in Razorpay Checkout (browser, not our code)
 *   Browser/Booking brings the proof -> verifyPayment   : check signature + ask Razorpay -> SUCCESS
 *   Razorpay calls our webhook       -> PaymentWebhookService -> applyProviderPayment (same rules)
 *   We never heard back              -> syncPayment / PaymentSyncJob : ask Razorpay ourselves
 *   Booking cancelled / expired      -> cancelPaymentsForBooking
 *   Money must go back               -> refundPayment / refundForBooking
 *
 * TRANSACTION STYLE: we never keep a database transaction open while waiting for Razorpay (it can take seconds).
 * Slow calls happen first, WITHOUT a transaction. Then a SHORT transaction (TransactionTemplate) locks the payment row,
 * applies the change and commits. Kafka events are published AFTER the commit, so a rolled-back change never
 * produces a message.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Payments the customer can still pay (an order exists and nothing has succeeded yet). */
    private static final List<PaymentStatus> OPEN_STATUSES = List.of(PaymentStatus.PENDING, PaymentStatus.FAILED);

    /** Everything that is not settled yet - these are the ones a cancellation may close. */
    private static final List<PaymentStatus> CANCELLABLE_STATUSES =
            List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.FAILED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final PaymentProperties properties;

    // Constructor injection (recommended way): Spring finds the objects and passes them in.
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          PaymentEventPublisher eventPublisher,
                          TransactionTemplate transactionTemplate,
                          PaymentProperties properties) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.tx = transactionTemplate;
        this.properties = properties;
    }

    /**
     * What a state change decided: the (saved) payment, an event to publish after commit (or null),
     * and whether we are now holding money for a dead booking that must be refunded.
     */
    public record Outcome(Payment payment, PaymentEvent event, boolean refundNeeded) {
        static Outcome unchanged(Payment p) {
            return new Outcome(p, null, false);
        }
    }

    // =====================================================================================================
    // 1. CREATE THE ORDER
    // =====================================================================================================

    /*
     * No @Transactional on purpose: the Razorpay call must not run inside a database transaction.
     * Each repository call below is its own small transaction.
     */
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Creating payment order for booking {}", request.bookingId());

        // Rule 0: only currencies we are set up for (Razorpay amounts are in "minor units"; we only handle 2-decimal ones)
        if (!properties.getAllowedCurrencies().contains(request.currency())) {
            throw new InvalidPaymentRequestException("Currency " + request.currency()
                    + " is not supported. Supported: " + properties.getAllowedCurrencies());
        }

        String key = (request.idempotencyKey() == null || request.idempotencyKey().isBlank())
                ? "booking-" + request.bookingId() + "-" + UUID.randomUUID()
                : request.idempotencyKey();

        // Rule 1: REPLAY. The same idempotency key must give back the same payment - never a second order.
        Optional<Payment> byKey = paymentRepository.findByIdempotencyKey(key);
        Payment payment;
        if (byKey.isPresent()) {
            payment = byKey.get();
            if (!matches(payment, request)) {
                throw new IdempotencyKeyReuseException(key);
            }
            if (payment.getProviderOrderId() != null) {
                log.info("Idempotent replay for key {}: returning payment {}", key, payment.getId());
                return toResponse(payment);
            }
            // An earlier attempt saved the row but Razorpay never created the order: just try the order again on that row.
            log.info("Retrying order creation on payment {} (earlier attempt had no order)", payment.getId());
        } else {
            // Rule 2: one successful payment per booking (no double charge)
            if (paymentRepository.existsByBookingIdAndStatus(request.bookingId(), PaymentStatus.SUCCESS)) {
                throw new DuplicatePaymentException(request.bookingId());
            }

            // Rule 3: one OPEN order per booking. If one exists, hand it out again instead of creating a second one
            // (two open orders could both be paid = the customer charged twice).
            Optional<Payment> open = paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(
                    request.bookingId(), OPEN_STATUSES);
            if (open.isPresent() && open.get().getProviderOrderId() != null) {
                if (matchesAmount(open.get(), request)) {
                    log.info("Booking {} already has open payment {}: reusing it", request.bookingId(), open.get().getId());
                    return toResponse(open.get());
                }
                // The amount changed (should not happen): close the old order, then open a fresh one
                cancelOne(open.get().getId(), "Superseded by a new order with a different amount");
            }

            // Save OUR record first, so we always have a trace even if Razorpay or our app crashes right after
            Payment fresh = new Payment();
            fresh.setBookingId(request.bookingId());
            fresh.setUserId(request.userId());
            fresh.setCustomerEmail(request.customerEmail());
            fresh.setAmount(request.amount());
            fresh.setCurrency(request.currency());
            fresh.setPaymentMethod(PaymentMethod.UNSPECIFIED);
            fresh.setStatus(PaymentStatus.INITIATED);
            fresh.setProvider(paymentGateway.providerName());
            fresh.setIdempotencyKey(key);
            try {
                payment = paymentRepository.saveAndFlush(fresh);
            } catch (DataIntegrityViolationException race) {
                // Another request with the same key won the race between our check and our insert: return ITS payment.
                return paymentRepository.findByIdempotencyKey(key)
                        .map(this::toResponse)
                        .orElseThrow(() -> race);
            }
        }

        // Ask Razorpay to create the order. The AMOUNT COMES FROM US (the Booking service), never from the browser.
        GatewayOrder order;
        try {
            order = paymentGateway.createOrder(
                    payment.getAmount(),
                    payment.getCurrency(),
                    "tkh_" + payment.getId(),       // our reference; Razorpay allows max 40 characters
                    Map.of("bookingId", String.valueOf(payment.getBookingId()),
                            "paymentId", String.valueOf(payment.getId())));   // visible in the Razorpay Dashboard
        } catch (RuntimeException e) {
            log.error("Could not create Razorpay order for payment {} (booking {}): {}",
                    payment.getId(), payment.getBookingId(), e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(truncate("Could not create the payment order: " + e.getMessage()));
            paymentRepository.save(payment);
            throw e;   // the caller (Booking) must know; a retry with the same key will try again on this row
        }

        payment.setProviderOrderId(order.orderId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        payment = paymentRepository.save(payment);
        log.info("Payment {} for booking {} is PENDING with order {}", payment.getId(), payment.getBookingId(), order.orderId());
        return toResponse(payment);
    }

    // =====================================================================================================
    // 2. VERIFY WHAT THE BROWSER BRINGS BACK
    // =====================================================================================================

    /**
     * Called after the customer paid in Razorpay Checkout. The browser (or Booking on its behalf) hands us three
     * values. We do NOT simply believe them:
     *   1. the signature must match   -> proves Razorpay produced these values for this order
     *   2. we ask Razorpay for the payment itself, and check order id + amount + currency match OUR order
     *   3. only a CAPTURED payment becomes SUCCESS
     */
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        String orderId = request.razorpayOrderId();
        String paymentId = request.razorpayPaymentId();

        // Step 1: the signature. First thing, before any database or network work.
        if (!paymentGateway.verifyPaymentSignature(orderId, paymentId, request.razorpaySignature())) {
            throw new InvalidSignatureException("checkout signature mismatch for order " + orderId);
        }

        Payment payment = paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> PaymentNotFoundException.forOrder(orderId));

        // Replay protection: the same proof sent twice is harmless and returns the current state
        if (payment.getStatus() == PaymentStatus.SUCCESS && paymentId.equals(payment.getProviderPaymentId())) {
            log.info("Payment {} was already verified; returning current state", payment.getId());
            return toResponse(payment);
        }

        // Step 2: ask Razorpay for the truth (slow network call: no transaction open)
        GatewayPayment fetched = paymentGateway.fetchPayment(paymentId);
        if (fetched.isAuthorized() && properties.getRazorpay().isManualCapture()) {
            fetched = paymentGateway.capture(paymentId, payment.getAmount(), payment.getCurrency());
        }
        final GatewayPayment providerPayment = fetched;

        // Step 3: record it - short transaction, row locked
        Outcome outcome = tx.execute(status -> {
            Payment locked = lock(payment.getId());
            return applyProviderPayment(locked, providerPayment);
        });
        return toResponse(finish(outcome));
    }

    // =====================================================================================================
    // 3. THE STATE MACHINE (shared by verify, webhook and sync)
    // =====================================================================================================

    /**
     * Applies what Razorpay says about ONE payment attempt to our payment row.
     * MUST be called inside a transaction with the row locked (see lock()). It only changes the database and
     * returns an Outcome; publishing events and refunds happen in finish(), after the commit.
     *
     * The rules, in plain words:
     *  - captured : money is ours. PENDING/FAILED/INITIATED -> SUCCESS. If the booking was already
     *               CANCELLED the money has no booking: record it and flag a refund.
     *  - failed   : only matters while we are still waiting (PENDING/FAILED). A failed message that arrives
     *               AFTER success (out of order, scenario SC-11) is ignored.
     *  - anything else (created, authorized...) : nothing to record yet.
     */
    public Outcome applyProviderPayment(Payment payment, GatewayPayment provider) {
        PaymentStatus before = payment.getStatus();

        if (provider.isCaptured()) {
            checkMatchesOurOrder(payment, provider);
            switch (before) {
                case SUCCESS -> {
                    if (!Objects.equals(payment.getProviderPaymentId(), provider.id())) {
                        // Two different captured payments on one order: the customer paid twice. Needs a human.
                        log.error("DOUBLE PAYMENT: order {} has a second captured payment {} (first: {}). Refund the extra one manually.",
                                payment.getProviderOrderId(), provider.id(), payment.getProviderPaymentId());
                    }
                    return Outcome.unchanged(payment);
                }
                case REFUNDED -> {
                    return Outcome.unchanged(payment);
                }
                case CANCELLED -> {
                    log.warn("Payment {} was CANCELLED but Razorpay captured {}: recording it and refunding",
                            payment.getId(), provider.id());
                    markSuccess(payment, provider);
                    payment = paymentRepository.save(payment);
                    // No PAYMENT_SUCCESS event: the customer must not get a "paid" email for a booking that is gone.
                    return new Outcome(payment, null, true);
                }
                default -> {
                    markSuccess(payment, provider);
                    payment = paymentRepository.save(payment);
                    log.info("Payment {} (booking {}) is SUCCESS via {}", payment.getId(), payment.getBookingId(), provider.id());
                    return new Outcome(payment, PaymentEvent.from(payment), false);
                }
            }
        }

        if (provider.isFailed()) {
            boolean waiting = before == PaymentStatus.PENDING || before == PaymentStatus.FAILED
                    || before == PaymentStatus.INITIATED;
            if (!waiting) {
                log.info("Ignoring failed attempt {} for payment {} which is already {}", provider.id(), payment.getId(), before);
                return Outcome.unchanged(payment);
            }
            if (before == PaymentStatus.FAILED && Objects.equals(payment.getProviderPaymentId(), provider.id())) {
                return Outcome.unchanged(payment);   // the same failed attempt reported again
            }
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderPaymentId(provider.id());   // remembers the latest attempt; overwritten if a retry succeeds
            payment.setFailureReason(truncate(provider.errorDescription() != null
                    ? provider.errorDescription() : "Payment attempt failed"));
            payment = paymentRepository.save(payment);
            log.info("Payment {} (booking {}): attempt {} failed: {}", payment.getId(), payment.getBookingId(),
                    provider.id(), payment.getFailureReason());
            return new Outcome(payment, PaymentEvent.from(payment), false);
        }

        return Outcome.unchanged(payment);
    }

    /**
     * Applies a refund notification from Razorpay (a refund started from the Razorpay Dashboard, or the
     * confirmation of one we started). MUST be called inside a transaction with the row locked.
     */
    public Outcome applyProviderRefund(Payment payment, String refundId, String refundStatus) {
        if ("failed".equalsIgnoreCase(refundStatus)) {
            // Rare: the bank rejected the refund. Money is back in OUR Razorpay balance; a human must pay the customer.
            log.error("REFUND FAILED at provider for payment {} (refund {}). Manual follow-up required.",
                    payment.getId(), refundId);
            payment.setFailureReason(truncate("Refund failed at provider (" + refundId + ")"));
            return Outcome.unchanged(paymentRepository.save(payment));
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setProviderRefundId(refundId);
            payment.setRefundedAt(Instant.now());
            payment = paymentRepository.save(payment);
            log.info("Payment {} is REFUNDED (refund {})", payment.getId(), refundId);
            return new Outcome(payment, PaymentEvent.from(payment), false);
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED && payment.getProviderRefundId() == null && refundId != null) {
            payment.setProviderRefundId(refundId);
            return Outcome.unchanged(paymentRepository.save(payment));
        }
        return Outcome.unchanged(payment);
    }

    /**
     * Runs AFTER the transaction committed: publish the Kafka event, and if we are holding money for a dead booking,
     * give it back. Returns the latest state of the payment.
     */
    public Payment finish(Outcome outcome) {
        if (outcome.event() != null) {
            eventPublisher.publish(outcome.event());
        }
        if (outcome.refundNeeded()) {
            try {
                return refundInternal(outcome.payment());
            } catch (RuntimeException e) {
                // Do not lose the signal: the payment stays SUCCESS and the log tells operations exactly what to do.
                log.error("AUTO-REFUND FAILED for payment {} (booking {}). It is SUCCESS but the booking is gone: refund it manually. Cause: {}",
                        outcome.payment().getId(), outcome.payment().getBookingId(), e.getMessage());
            }
        }
        return outcome.payment();
    }

    /** Loads a payment row and locks it until the surrounding transaction ends. Call inside tx. */
    public Payment lock(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    /** Finds by Razorpay order id and locks the row. Call inside tx. Empty if the order is not ours. */
    public Optional<Payment> lockByProviderOrderId(String providerOrderId) {
        return paymentRepository.findByProviderOrderId(providerOrderId)
                .map(p -> lock(p.getId()));
    }

    /** Finds by Razorpay payment id and locks the row. Call inside tx. Empty if unknown. */
    public Optional<Payment> lockByProviderPaymentId(String providerPaymentId) {
        return paymentRepository.findByProviderPaymentId(providerPaymentId)
                .map(p -> lock(p.getId()));
    }

    // =====================================================================================================
    // 4. READS
    // =====================================================================================================

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForBooking(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =====================================================================================================
    // 5. SYNC (we missed a message - ask Razorpay ourselves)
    // =====================================================================================================

    /** Asks Razorpay what happened on this payment's order and applies it. Safe to call any number of times. */
    public PaymentResponse syncPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return toResponse(syncFromProvider(payment));
    }

    /** Same as syncPayment but on the entity; used by the background job and by cancellation. May throw PaymentProviderException. */
    public Payment syncFromProvider(Payment payment) {
        if (payment.getProviderOrderId() == null
                || !(payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED)) {
            return payment;
        }
        List<GatewayPayment> attempts = paymentGateway.fetchOrderPayments(payment.getProviderOrderId());

        // Prefer a captured attempt; then (manual capture only) capture an authorized one; else the latest failure.
        Optional<GatewayPayment> best = attempts.stream().filter(GatewayPayment::isCaptured).findFirst();
        if (best.isEmpty() && properties.getRazorpay().isManualCapture()) {
            best = attempts.stream().filter(GatewayPayment::isAuthorized).findFirst()
                    .map(a -> paymentGateway.capture(a.id(), payment.getAmount(), payment.getCurrency()));
        }
        if (best.isEmpty()) {
            best = attempts.stream().filter(GatewayPayment::isFailed)
                    .max(Comparator.comparingLong(GatewayPayment::createdAtEpochSeconds));
        }
        if (best.isEmpty()) {
            return payment;
        }
        final GatewayPayment chosen = best.get();
        Outcome outcome = tx.execute(status -> applyProviderPayment(lock(payment.getId()), chosen));
        return finish(outcome);
    }

    // =====================================================================================================
    // 6. CANCEL (booking cancelled or expired before it was paid)
    // =====================================================================================================

    /**
     * Closes every unsettled payment of a booking. Before closing we ask Razorpay once more: the customer may have
     * paid a second ago and we simply have not heard yet. If so, that payment becomes SUCCESS and is NOT cancelled -
     * the caller (Booking) sees the SUCCESS in the returned list and decides what to do.
     *
     * Even if this check cannot run (Razorpay unreachable) we still cancel: a payment captured later is then
     * recognised and refunded automatically (see applyProviderPayment, case CANCELLED).
     */
    public List<PaymentResponse> cancelPaymentsForBooking(Long bookingId) {
        List<PaymentResponse> result = new ArrayList<>();
        for (Payment payment : paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)) {
            Payment current = payment;
            if (CANCELLABLE_STATUSES.contains(current.getStatus())) {
                try {
                    current = syncFromProvider(current);
                } catch (PaymentProviderException e) {
                    log.warn("Could not double-check payment {} with Razorpay before cancelling: {}", current.getId(), e.getMessage());
                }
            }
            if (CANCELLABLE_STATUSES.contains(current.getStatus())) {
                current = cancelOne(current.getId(), "Booking cancelled or expired before payment");
            }
            result.add(toResponse(current));
        }
        return result;
    }

    private Payment cancelOne(Long paymentId, String reason) {
        return tx.execute(status -> {
            Payment locked = lock(paymentId);
            if (!CANCELLABLE_STATUSES.contains(locked.getStatus())) {
                return locked;   // settled in the meantime: leave it alone
            }
            locked.setStatus(PaymentStatus.CANCELLED);
            if (locked.getFailureReason() == null) {
                locked.setFailureReason(reason);
            }
            log.info("Payment {} (booking {}) CANCELLED: {}", locked.getId(), locked.getBookingId(), reason);
            return paymentRepository.save(locked);
        });
    }

    // =====================================================================================================
    // 7. REFUND
    // =====================================================================================================

    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return toResponse(refundInternal(payment));
    }

    /** Refunds the successful payment of a booking (used when a CONFIRMED booking is cancelled). Safe to repeat. */
    public PaymentResponse refundForBooking(Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        Optional<Payment> paid = payments.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS).findFirst();
        if (paid.isPresent()) {
            return toResponse(refundInternal(paid.get()));
        }
        Optional<Payment> refunded = payments.stream().filter(p -> p.getStatus() == PaymentStatus.REFUNDED).findFirst();
        if (refunded.isPresent()) {
            return toResponse(refunded.get());   // already done: repeating is harmless
        }
        throw new InvalidPaymentStateException("Booking " + bookingId + " has no successful payment to refund");
    }

    private Payment refundInternal(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }
        // Business rule: only money that was actually taken can be returned
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidPaymentStateException(
                    "Only SUCCESS payments can be refunded. Current status: " + payment.getStatus());
        }

        // One fixed receipt per payment: Razorpay refuses a second refund with the same receipt = a double click can't refund twice.
        String receipt = "rfnd_" + payment.getId();
        GatewayRefund refund;
        try {
            refund = paymentGateway.refund(payment.getProviderPaymentId(), payment.getAmount(), receipt);
        } catch (PaymentProviderException e) {
            // Maybe the refund already went through (a retry after a timeout)? Ask Razorpay before giving up.
            GatewayPayment current = paymentGateway.fetchPayment(payment.getProviderPaymentId());
            if (!current.isFullyRefunded()) {
                throw e;
            }
            log.warn("Payment {} was already fully refunded at Razorpay; syncing our record", payment.getId());
            refund = new GatewayRefund(null, payment.getProviderPaymentId(), current.amountRefundedInMinorUnits(), "processed");
        }

        final GatewayRefund confirmed = refund;
        Outcome outcome = tx.execute(status -> {
            Payment locked = lock(payment.getId());
            if (locked.getStatus() == PaymentStatus.REFUNDED) {
                return Outcome.unchanged(locked);   // a webhook got there first
            }
            locked.setStatus(PaymentStatus.REFUNDED);
            locked.setProviderRefundId(confirmed.refundId());
            locked.setRefundedAt(Instant.now());
            Payment saved = paymentRepository.save(locked);
            log.info("Payment {} (booking {}) REFUNDED", saved.getId(), saved.getBookingId());
            return new Outcome(saved, PaymentEvent.from(saved), false);
        });
        if (outcome.event() != null) {
            eventPublisher.publish(outcome.event());
        }
        return outcome.payment();
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    private void markSuccess(Payment payment, GatewayPayment provider) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(provider.id());
        payment.setPaymentMethod(PaymentMethod.fromProvider(provider.method()));
        payment.setPaidAt(Instant.now());
        payment.setFailureReason(null);
    }

    /** Defence in depth: even a correctly signed payment must be for OUR order, amount and currency. */
    private void checkMatchesOurOrder(Payment payment, GatewayPayment provider) {
        if (!Objects.equals(payment.getProviderOrderId(), provider.orderId())) {
            throw new PaymentVerificationException("Razorpay payment " + provider.id()
                    + " belongs to order " + provider.orderId() + ", not " + payment.getProviderOrderId());
        }
        if (provider.amountInMinorUnits() != AmountUtil.toMinorUnits(payment.getAmount())
                || !payment.getCurrency().equalsIgnoreCase(provider.currency())) {
            log.error("AMOUNT MISMATCH for payment {}: we expected {} {} but Razorpay captured {} {}",
                    payment.getId(), AmountUtil.toMinorUnits(payment.getAmount()), payment.getCurrency(),
                    provider.amountInMinorUnits(), provider.currency());
            throw new PaymentVerificationException("Amount or currency reported by Razorpay does not match order "
                    + payment.getProviderOrderId());
        }
    }

    private boolean matches(Payment p, PaymentRequest r) {
        return Objects.equals(p.getBookingId(), r.bookingId()) && matchesAmount(p, r);
    }

    private boolean matchesAmount(Payment p, PaymentRequest r) {
        return p.getAmount().compareTo(r.amount()) == 0 && p.getCurrency().equals(r.currency());
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment, paymentGateway.publicKey());
    }

    private static String truncate(String text) {
        return text.length() <= 255 ? text : text.substring(0, 255);
    }
}
