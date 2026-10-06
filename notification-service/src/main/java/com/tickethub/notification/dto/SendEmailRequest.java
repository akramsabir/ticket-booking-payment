package com.tickethub.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/notifications/email - send a free-text email to any address. */
public class SendEmailRequest {

    @NotBlank(message = "to is required")
    @Email(message = "to must be a valid email address")
    @Size(max = 254, message = "to is too long")
    private String to;

    @Size(max = 150, message = "recipientName must be at most 150 characters")
    private String recipientName;

    @NotBlank(message = "subject is required")
    @Size(max = 200, message = "subject must be at most 200 characters")
    private String subject;

    @NotBlank(message = "message is required")
    @Size(max = 5000, message = "message must be at most 5000 characters")
    private String message;

    /** Optional: sending the same key twice sends only one email. */
    @Size(max = 100, message = "idempotencyKey must be at most 100 characters")
    private String idempotencyKey;

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
