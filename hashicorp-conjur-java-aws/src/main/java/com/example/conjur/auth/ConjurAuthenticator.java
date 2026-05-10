package com.example.conjur.auth;

/** Contract for all Conjur authentication strategies. */
public interface ConjurAuthenticator {

    /**
     * Authenticate against Conjur and return a short-lived access token.
     *
     * @return Base64-encoded Conjur access token
     * @throws ConjurAuthException when authentication fails
     */
    String authenticate() throws ConjurAuthException;
}
