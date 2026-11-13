package com.taskmanager.exception;

/** Bad username/password combination. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
