package com.example.conjur.model;

import java.time.Instant;

/** Holds a secret value retrieved from Conjur along with metadata. */
public class ConjurSecret {

    private final String variableId;
    private final String value;
    private final Instant retrievedAt;

    public ConjurSecret(String variableId, String value) {
        this.variableId = variableId;
        this.value = value;
        this.retrievedAt = Instant.now();
    }

    public String getVariableId() { return variableId; }
    public String getValue() { return value; }
    public Instant getRetrievedAt() { return retrievedAt; }

    @Override
    public String toString() {
        return "ConjurSecret{variableId='" + variableId + "', retrievedAt=" + retrievedAt + "}";
    }
}
