package com.tickethub.payment.repository;

import com.tickethub.payment.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    // "have we already processed the webhook with this X-Razorpay-Event-Id?"
    boolean existsByEventId(String eventId);
}
