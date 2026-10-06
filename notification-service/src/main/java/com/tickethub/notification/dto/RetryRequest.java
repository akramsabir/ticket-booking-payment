package com.tickethub.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Optional body of POST /api/notifications/{id}/retry - supply a corrected address if the old one was wrong/missing. */
public class RetryRequest {

    @Email(message = "recipient must be a valid email address")
    @Size(max = 254)
    private String recipient;

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
}
