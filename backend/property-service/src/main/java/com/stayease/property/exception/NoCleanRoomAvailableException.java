package com.stayease.property.exception;

public class NoCleanRoomAvailableException extends RuntimeException {
    public NoCleanRoomAvailableException(String message) {
        super(message);
    }
}
