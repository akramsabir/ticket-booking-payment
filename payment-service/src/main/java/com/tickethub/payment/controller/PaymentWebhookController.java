package com.tickethub.payment.controller;

import com.tickethub.payment.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The ONE public door Razorpay knocks on:   POST /api/v1/payments/webhook
 * Register this URL (https, publicly reachable) in Razorpay Dashboard > Settings > Webhooks, together with the
 * webhook secret. There is no login on this endpoint by design: the SIGNATURE is the authentication.
 *
 * Why the body is a byte[] and not a DTO: the signature is calculated over the EXACT bytes Razorpay sent.
 * If we let Spring turn the JSON into an object and back, spacing/ordering could change and every signature check
 * would fail. So we keep the raw bytes and parse them ourselves, after verifying.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        String result = webhookService.handle(rawBody, signature, eventId);
        return ResponseEntity.ok(Map.of("status", result));
    }
}
