package com.tickethub.notification.channel;

import com.tickethub.notification.entity.NotificationChannelType;

/**
 * A way of reaching the customer. EmailChannel is the only one today.
 * To add SMS/WhatsApp/push later: add a value to NotificationChannelType and write another class
 * that implements this interface - the rest of the service does not change.
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /**
     * Deliver the message.
     *
     * @return a provider reference (for email: the Message-Id), may be null
     * @throws ChannelDeliveryException when delivery failed (see isRetryable())
     */
    String send(ChannelMessage message);
}
