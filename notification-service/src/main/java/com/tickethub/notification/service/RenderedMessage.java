package com.tickethub.notification.service;

/** Subject + the two mail bodies, ready to send. */
public record RenderedMessage(String subject, String htmlBody, String textBody) {
}
