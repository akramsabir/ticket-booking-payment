package com.tickethub.booking.service.exception;

public class ResourceNotFoundException  extends RuntimeException{
    public ResourceNotFoundException(String msg){
        super(msg);
    }
    // Flexible constructor for structured messages: e.g. "Seat not found with ID: 1"
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
