package com.tickethub.notification.channel;

/** Channel-neutral message. For email: recipient = address, htmlBody/textBody = the two mail parts. */
public record ChannelMessage(String recipient,
                             String recipientName,
                             String subject,
                             String htmlBody,
                             String textBody) {
}
