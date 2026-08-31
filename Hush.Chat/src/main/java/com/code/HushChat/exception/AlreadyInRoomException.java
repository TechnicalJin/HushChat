package com.code.HushChat.exception;

public class AlreadyInRoomException extends RuntimeException {
    public AlreadyInRoomException(String message) {
        super(message);
    }
}
