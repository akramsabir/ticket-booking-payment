package com.tickethub.booking.service.exception;

public class DuplicateResourceException extends  RuntimeException{
    public DuplicateResourceException(String msg){
        super(msg);
    }
}
