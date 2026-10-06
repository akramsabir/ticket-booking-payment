package com.tickethub.notification.exception;

/**
 * A Kafka message that can never be processed (broken JSON, missing fields). Extends IllegalArgumentException
 * so the Kafka error handler does NOT retry it and sends it straight to the dead-letter topic.
 */
public class InvalidEventException extends IllegalArgumentException {
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidEventException(String message) {
        super(message);
    }
}
