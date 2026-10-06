package com.tickethub.notification.controller;

import com.tickethub.notification.dto.*;
import com.tickethub.notification.entity.NotificationRecord;
import com.tickethub.notification.entity.NotificationStatus;
import com.tickethub.notification.entity.NotificationType;
import com.tickethub.notification.service.NotificationRequest;
import com.tickethub.notification.service.NotificationService;
import com.tickethub.notification.service.SubmitResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API of the notification service.
 *
 *  POST /api/notifications/email              send a free-text email to any address
 *  POST /api/notifications/trigger            send one of the ready-made booking/payment emails
 *  GET  /api/notifications                    list (filters: status, type, recipient, bookingId, page, size)
 *  GET  /api/notifications/{id}               one notification
 *  GET  /api/notifications/{id}/preview       the stored HTML email
 *  POST /api/notifications/{id}/retry         send a FAILED/PENDING one again (optionally with a corrected recipient)
 *
 * Response codes for the two POST-send endpoints: 201 = email sent, 202 = saved but not delivered yet
 * (it will be retried automatically), 200 = duplicate of an earlier request (nothing sent again).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/email")
    public ResponseEntity<NotificationResponse> sendEmail(
            @Valid @RequestBody SendEmailRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {

        String key = firstNonBlank(body.getIdempotencyKey(), idempotencyHeader);
        String dedupeKey = key == null ? "CUSTOM:" + UUID.randomUUID() : "CUSTOM:" + key.trim();

        NotificationRequest request = new NotificationRequest(NotificationType.CUSTOM, dedupeKey)
                .recipient(body.getTo())
                .recipientName(body.getRecipientName())
                .subject(body.getSubject())
                .message(body.getMessage());
        return respond(notificationService.submit(request));
    }

    @PostMapping("/trigger")
    public ResponseEntity<NotificationResponse> trigger(@Valid @RequestBody TriggerNotificationRequest body) {
        if (body.getType() == NotificationType.CUSTOM) {
            throw new com.tickethub.notification.exception.InvalidRequestException(
                    "Use POST /api/notifications/email for CUSTOM messages");
        }
        String dedupeKey = "TRIGGER:" + body.getType() + ":" + body.getBookingId()
                + (body.getPaymentId() == null ? "" : ":" + body.getPaymentId());

        NotificationRequest request = new NotificationRequest(body.getType(), dedupeKey)
                .recipient(body.getRecipientEmail())
                .recipientName(body.getRecipientName())
                .bookingId(body.getBookingId())
                .paymentId(body.getPaymentId())
                .userId(body.getUserId())
                .amount(body.getAmount())
                .currency(body.getCurrency())
                .reason(body.getReason())
                .showTitle(body.getShowTitle())
                .venue(body.getVenue())
                .showTime(body.getShowTime())
                .seats(body.getSeats());
        return respond(notificationService.submit(request));
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(notificationService.search(status, type, recipient, bookingId, page, size),
                NotificationResponse::from);
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse get(@PathVariable String notificationId) {
        return NotificationResponse.from(notificationService.get(notificationId));
    }

    @GetMapping(value = "/{notificationId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@PathVariable String notificationId) {
        String html = notificationService.get(notificationId).getHtmlBody();
        return html == null ? "" : html;
    }

    @PostMapping("/{notificationId}/retry")
    public NotificationResponse retry(@PathVariable String notificationId,
                                      @Valid @RequestBody(required = false) RetryRequest body) {
        NotificationRecord record = notificationService.retryNow(notificationId, body == null ? null : body.getRecipient());
        return NotificationResponse.from(record);
    }

    private ResponseEntity<NotificationResponse> respond(SubmitResult result) {
        NotificationRecord record = result.record();
        HttpStatus status;
        if (!result.created()) {
            status = HttpStatus.OK;
        } else if (record.getStatus() == NotificationStatus.SENT) {
            status = HttpStatus.CREATED;
        } else {
            status = HttpStatus.ACCEPTED;
        }
        return ResponseEntity.status(status).body(NotificationResponse.from(record, !result.created()));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
