package com.tickethub.booking.service.exception;

public class InvalidJsonDataException extends RuntimeException {
    public InvalidJsonDataException(String msg){
        super(msg);
    }
    public InvalidJsonDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
