package com.example.conjur.client;

/** Thrown when a Conjur API call fails. */
public class ConjurClientException extends RuntimeException {

    public ConjurClientException(String message) {
        super(message);
    }

    public ConjurClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
