package com.example.conjur;

import com.example.conjur.client.ConjurClient;
import com.example.conjur.model.ConjurSecret;
import com.example.conjur.service.ConjurSecretManager;
import com.example.conjur.service.SecretCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConjurSecretManagerTest {

    @Mock
    private ConjurClient mockClient;

    private ConjurSecretManager manager;

    @BeforeEach
    void setUp() {
        SecretCache cache = new SecretCache(Duration.ofMinutes(5));
        manager = new ConjurSecretManager(mockClient, cache);
    }

    @Test
    void getSecret_fetchesFromConjurOnCacheMiss() {
        when(mockClient.retrieveSecret("aws/rds/password"))
                .thenReturn(new ConjurSecret("aws/rds/password", "secret123"));

        String result = manager.getSecret("aws/rds/password");

        assertEquals("secret123", result);
        verify(mockClient, times(1)).retrieveSecret("aws/rds/password");
    }

    @Test
    void getSecret_returnsFromCacheOnSecondCall() {
        when(mockClient.retrieveSecret("aws/rds/password"))
                .thenReturn(new ConjurSecret("aws/rds/password", "secret123"));

        manager.getSecret("aws/rds/password");
        manager.getSecret("aws/rds/password");

        // Client called only once; second call served from cache
        verify(mockClient, times(1)).retrieveSecret("aws/rds/password");
    }

    @Test
    void getSecrets_batchFetchesUncachedIds() {
        Map<String, ConjurSecret> batchResult = Map.of(
                "aws/rds/host", new ConjurSecret("aws/rds/host", "db.example.com"),
                "aws/rds/port", new ConjurSecret("aws/rds/port", "5432")
        );
        when(mockClient.retrieveSecrets(anyList())).thenReturn(batchResult);

        Map<String, String> result = manager.getSecrets(List.of("aws/rds/host", "aws/rds/port"));

        assertEquals("db.example.com", result.get("aws/rds/host"));
        assertEquals("5432", result.get("aws/rds/port"));
        verify(mockClient, times(1)).retrieveSecrets(anyList());
    }

    @Test
    void getSecrets_onlyFetchesUncachedIds() {
        // Pre-populate cache with one secret
        when(mockClient.retrieveSecret("aws/rds/host"))
                .thenReturn(new ConjurSecret("aws/rds/host", "db.example.com"));
        manager.getSecret("aws/rds/host");

        // Batch fetch – only aws/rds/port should be requested from Conjur
        when(mockClient.retrieveSecrets(List.of("aws/rds/port")))
                .thenReturn(Map.of("aws/rds/port", new ConjurSecret("aws/rds/port", "5432")));

        Map<String, String> result = manager.getSecrets(List.of("aws/rds/host", "aws/rds/port"));

        assertEquals("db.example.com", result.get("aws/rds/host"));
        assertEquals("5432", result.get("aws/rds/port"));
        verify(mockClient, times(1)).retrieveSecrets(List.of("aws/rds/port"));
    }

    @Test
    void rotateSecret_updatesConjurAndInvalidatesCache() {
        when(mockClient.retrieveSecret("aws/api/key"))
                .thenReturn(new ConjurSecret("aws/api/key", "old-key"));

        // Cache the secret
        manager.getSecret("aws/api/key");

        // Rotate
        doNothing().when(mockClient).setSecret(anyString(), anyString());
        manager.rotateSecret("aws/api/key", "new-key");

        // After rotation, cache is invalidated, so next call fetches from Conjur
        when(mockClient.retrieveSecret("aws/api/key"))
                .thenReturn(new ConjurSecret("aws/api/key", "new-key"));
        String afterRotation = manager.getSecret("aws/api/key");

        assertEquals("new-key", afterRotation);
        verify(mockClient, times(1)).setSecret("aws/api/key", "new-key");
        verify(mockClient, times(2)).retrieveSecret("aws/api/key");
    }

    @Test
    void getDatabaseUrl_composesJdbcUrl() {
        Map<String, ConjurSecret> secrets = Map.of(
                "aws/rds/host",   new ConjurSecret("aws/rds/host", "db.example.com"),
                "aws/rds/port",   new ConjurSecret("aws/rds/port", "5432"),
                "aws/rds/dbname", new ConjurSecret("aws/rds/dbname", "mydb"),
                "aws/rds/user",   new ConjurSecret("aws/rds/user", "admin"),
                "aws/rds/pass",   new ConjurSecret("aws/rds/pass", "s3cr3t")
        );
        when(mockClient.retrieveSecrets(anyList())).thenReturn(secrets);

        String url = manager.getDatabaseUrl(
                "aws/rds/host", "aws/rds/port", "aws/rds/dbname",
                "aws/rds/user", "aws/rds/pass");

        assertTrue(url.startsWith("jdbc:postgresql://db.example.com:5432/mydb"));
        assertTrue(url.contains("user=admin"));
    }

    @Test
    void getAwsCredentials_returnsCredentials() {
        when(mockClient.retrieveSecrets(anyList())).thenReturn(Map.of(
                "aws/iam/access-key-id",     new ConjurSecret("aws/iam/access-key-id", "AKIA..."),
                "aws/iam/secret-access-key", new ConjurSecret("aws/iam/secret-access-key", "secret")
        ));

        ConjurSecretManager.AwsCredentials creds = manager.getAwsCredentials(
                "aws/iam/access-key-id", "aws/iam/secret-access-key");

        assertEquals("AKIA...", creds.getAccessKeyId());
        assertEquals("secret", creds.getSecretAccessKey());
        assertTrue(creds.toString().contains("[REDACTED]"));
    }
}
