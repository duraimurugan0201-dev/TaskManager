package com.taskmanager.exception;

/** Client sent a well-formed but semantically invalid request. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
