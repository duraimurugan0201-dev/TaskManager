package com.taskmanager.exception;

/** Unique constraint conflict, e.g. duplicate username or email. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
