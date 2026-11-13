package com.taskmanager.exception;

/** Account exists but is disabled or locked. */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
