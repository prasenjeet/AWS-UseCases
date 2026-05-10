package com.example.conjur.auth;

/** Thrown when Conjur authentication fails. */
public class ConjurAuthException extends RuntimeException {

    public ConjurAuthException(String message) {
        super(message);
    }

    public ConjurAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
