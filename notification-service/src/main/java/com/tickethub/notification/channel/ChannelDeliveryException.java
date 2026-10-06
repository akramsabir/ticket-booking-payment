package com.tickethub.notification.channel;

/** Delivery failed. retryable=false means "do not try again" (e.g. the address is malformed). */
public class ChannelDeliveryException extends RuntimeException {

    private final boolean retryable;

    public ChannelDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
