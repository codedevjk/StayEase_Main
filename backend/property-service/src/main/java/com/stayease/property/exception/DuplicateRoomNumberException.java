package com.stayease.property.exception;

public class DuplicateRoomNumberException extends RuntimeException {
    public DuplicateRoomNumberException(String message) {
        super(message);
    }
}
