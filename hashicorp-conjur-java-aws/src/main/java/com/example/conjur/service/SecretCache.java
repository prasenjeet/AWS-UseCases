package com.example.conjur.service;

import com.example.conjur.model.ConjurSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, TTL-based in-memory cache for Conjur secrets.
 * Reduces round trips for frequently read secrets.
 */
public class SecretCache {

    private static final Logger log = LoggerFactory.getLogger(SecretCache.class);

    private final Duration ttl;
    private final Map<String, CachedEntry> store = new ConcurrentHashMap<>();

    public SecretCache(Duration ttl) {
        this.ttl = ttl;
    }

    public Optional<ConjurSecret> get(String variableId) {
        CachedEntry entry = store.get(variableId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(variableId);
            log.debug("Cache expired for: {}", variableId);
            return Optional.empty();
        }
        log.debug("Cache hit: {}", variableId);
        return Optional.of(entry.secret);
    }

    public void put(String variableId, ConjurSecret secret) {
        store.put(variableId, new CachedEntry(secret, Instant.now().plus(ttl)));
        log.debug("Cached secret: {}", variableId);
    }

    public void invalidate(String variableId) {
        store.remove(variableId);
    }

    public void invalidateAll() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    private static final class CachedEntry {
        final ConjurSecret secret;
        final Instant expiresAt;

        CachedEntry(ConjurSecret secret, Instant expiresAt) {
            this.secret = secret;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
