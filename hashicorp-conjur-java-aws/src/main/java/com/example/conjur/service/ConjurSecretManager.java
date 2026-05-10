package com.example.conjur.service;

import com.example.conjur.client.ConjurClient;
import com.example.conjur.model.ConjurSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * High-level service for managing secrets stored in Conjur.
 * Provides caching, bulk retrieval, and typed accessors used by AWS-integrated applications.
 */
public class ConjurSecretManager {

    private static final Logger log = LoggerFactory.getLogger(ConjurSecretManager.class);

    private final ConjurClient client;
    private final SecretCache cache;

    public ConjurSecretManager(ConjurClient client, SecretCache cache) {
        this.client = client;
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Core secret retrieval
    // -------------------------------------------------------------------------

    /**
     * Retrieve a secret, using the cache when available.
     *
     * @param variableId fully-qualified Conjur variable path (e.g. "aws/rds/password")
     * @return secret value
     */
    public String getSecret(String variableId) {
        Optional<ConjurSecret> cached = cache.get(variableId);
        if (cached.isPresent()) {
            return cached.get().getValue();
        }

        ConjurSecret secret = client.retrieveSecret(variableId);
        cache.put(variableId, secret);
        return secret.getValue();
    }

    /**
     * Retrieve multiple secrets in a single Conjur round trip.
     *
     * @param variableIds list of variable paths to fetch
     * @return map of variableId → secret value
     */
    public Map<String, String> getSecrets(List<String> variableIds) {
        // Only fetch IDs that are not in the cache
        List<String> uncached = variableIds.stream()
                .filter(id -> cache.get(id).isEmpty())
                .collect(Collectors.toList());

        if (!uncached.isEmpty()) {
            Map<String, ConjurSecret> fetched = client.retrieveSecrets(uncached);
            fetched.forEach((id, secret) -> cache.put(id, secret));
        }

        return variableIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> cache.get(id)
                                .map(ConjurSecret::getValue)
                                .orElse("")
                ));
    }

    /**
     * Update a secret value in Conjur and invalidate its cache entry.
     *
     * @param variableId Conjur variable path
     * @param newValue   new secret value
     */
    public void rotateSecret(String variableId, String newValue) {
        log.info("Rotating secret: {}", variableId);
        client.setSecret(variableId, newValue);
        cache.invalidate(variableId);
    }

    // -------------------------------------------------------------------------
    // Typed AWS-oriented accessors
    // -------------------------------------------------------------------------

    /** Retrieve a database connection URL composed from individual Conjur variables. */
    public String getDatabaseUrl(String hostVar, String portVar, String dbNameVar,
                                  String userVar, String passwordVar) {
        Map<String, String> secrets = getSecrets(List.of(hostVar, portVar, dbNameVar, userVar, passwordVar));
        return String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",
                secrets.get(hostVar),
                secrets.get(portVar),
                secrets.get(dbNameVar),
                secrets.get(userVar),
                secrets.get(passwordVar));
    }

    /** Retrieve AWS access credentials stored as Conjur variables. */
    public AwsCredentials getAwsCredentials(String accessKeyIdVar, String secretAccessKeyVar) {
        Map<String, String> secrets = getSecrets(List.of(accessKeyIdVar, secretAccessKeyVar));
        return new AwsCredentials(
                secrets.get(accessKeyIdVar),
                secrets.get(secretAccessKeyVar));
    }

    /** Invalidate all cached secrets, forcing fresh retrieval on next access. */
    public void invalidateCache() {
        cache.invalidateAll();
        log.info("Secret cache invalidated");
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    public static final class AwsCredentials {
        private final String accessKeyId;
        private final String secretAccessKey;

        public AwsCredentials(String accessKeyId, String secretAccessKey) {
            this.accessKeyId = accessKeyId;
            this.secretAccessKey = secretAccessKey;
        }

        public String getAccessKeyId() { return accessKeyId; }
        public String getSecretAccessKey() { return secretAccessKey; }

        @Override
        public String toString() {
            return "AwsCredentials{accessKeyId='" + accessKeyId + "', secretAccessKey='[REDACTED]'}";
        }
    }
}
