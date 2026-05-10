package com.example.conjur;

import com.example.conjur.model.ConjurSecret;
import com.example.conjur.service.SecretCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SecretCacheTest {

    @Test
    void get_returnsEmptyWhenCacheMiss() {
        SecretCache cache = new SecretCache(Duration.ofMinutes(5));
        assertTrue(cache.get("aws/rds/password").isEmpty());
    }

    @Test
    void put_thenGet_returnsSecret() {
        SecretCache cache = new SecretCache(Duration.ofMinutes(5));
        ConjurSecret secret = new ConjurSecret("aws/rds/password", "value123");
        cache.put("aws/rds/password", secret);

        Optional<ConjurSecret> result = cache.get("aws/rds/password");
        assertTrue(result.isPresent());
        assertEquals("value123", result.get().getValue());
    }

    @Test
    void get_returnsEmptyAfterTtlExpiry() throws InterruptedException {
        SecretCache cache = new SecretCache(Duration.ofMillis(50));
        cache.put("aws/key", new ConjurSecret("aws/key", "temp"));

        Thread.sleep(100);

        assertTrue(cache.get("aws/key").isEmpty());
    }

    @Test
    void invalidate_removesEntry() {
        SecretCache cache = new SecretCache(Duration.ofMinutes(5));
        cache.put("aws/key", new ConjurSecret("aws/key", "value"));
        cache.invalidate("aws/key");

        assertTrue(cache.get("aws/key").isEmpty());
    }

    @Test
    void invalidateAll_clearsCache() {
        SecretCache cache = new SecretCache(Duration.ofMinutes(5));
        cache.put("k1", new ConjurSecret("k1", "v1"));
        cache.put("k2", new ConjurSecret("k2", "v2"));
        cache.invalidateAll();

        assertEquals(0, cache.size());
    }
}
