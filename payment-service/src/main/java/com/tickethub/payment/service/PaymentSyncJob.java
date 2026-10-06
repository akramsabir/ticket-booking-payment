package com.tickethub.payment.service;

import com.tickethub.payment.config.PaymentProperties;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SAFETY NET: periodically asks Razorpay about payments that are still waiting.
 *
 * Why it exists: webhooks and the customer's verify call can both fail to reach us (our service was restarting, the
 * customer lost connection right after paying, a webhook URL was misconfigured, or - on your laptop - Razorpay simply
 * cannot reach localhost). Without this job such a payment would stay PENDING even though Razorpay has the money.
 * This is the "reconciliation" of the blueprint (SC-11): eventually our records agree with the provider's.
 *
 * It uses exactly the same state machine as the webhook (PaymentService.syncFromProvider), so running it
 * any number of times is harmless.
 */
@Component
public class PaymentSyncJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentSyncJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentProperties properties;

    public PaymentSyncJob(PaymentRepository paymentRepository, PaymentService paymentService, PaymentProperties properties) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${tickethub.payment.sync.delay-ms:60000}",
            initialDelayString = "${tickethub.payment.sync.delay-ms:60000}")
    public void syncQuietPayments() {
        PaymentProperties.Sync cfg = properties.getSync();
        if (!cfg.isEnabled()) {
            return;
        }
        Instant quietBefore = Instant.now().minusSeconds(cfg.getMinAgeSeconds());
        Instant notOlderThan = Instant.now().minus(Duration.ofMinutes(cfg.getMaxAgeMinutes()));

        // PENDING = waiting for a result; FAILED = last attempt failed but the customer may have retried successfully
        List<Payment> candidates = new ArrayList<>();
        for (PaymentStatus status : List.of(PaymentStatus.PENDING, PaymentStatus.FAILED)) {
            candidates.addAll(paymentRepository
                    .findTop50ByStatusAndUpdatedAtBeforeAndCreatedAtAfterOrderByCreatedAtAsc(status, quietBefore, notOlderThan));
        }

        for (Payment payment : candidates) {
            try {
                paymentService.syncFromProvider(payment);
            } catch (RuntimeException e) {
                // One bad payment (or Razorpay hiccup) must not stop the others
                log.warn("Sync of payment {} failed: {}", payment.getId(), e.getMessage());
            }
        }
        if (!candidates.isEmpty()) {
            log.debug("Reconciliation looked at {} quiet payments", candidates.size());
        }
    }
}
