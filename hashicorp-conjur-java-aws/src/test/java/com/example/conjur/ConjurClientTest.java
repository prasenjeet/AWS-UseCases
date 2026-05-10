package com.example.conjur;

import com.example.conjur.auth.ApiKeyAuthenticator;
import com.example.conjur.auth.ConjurAuthException;
import com.example.conjur.client.ConjurClient;
import com.example.conjur.client.ConjurClientException;
import com.example.conjur.model.ConjurConfig;
import com.example.conjur.model.ConjurSecret;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConjurClientTest {

    private MockWebServer server;
    private ConjurClient client;
    private ConjurConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        String baseUrl = server.url("").toString().replaceAll("/$", "");

        config = ConjurConfig.builder()
                .applianceUrl(baseUrl)
                .account("testaccount")
                .login("host/test-host")
                .apiKey("test-api-key")
                .sslVerify(false)
                .build();

        OkHttpClient httpClient = new OkHttpClient();

        // Enqueue the authentication response (called lazily on first secret request)
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("fake-conjur-token"));

        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(config, httpClient);
        client = new ConjurClient(config, httpClient, authenticator);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void retrieveSecret_returnsSecretValue() throws Exception {
        // Enqueue auth + secret responses
        server.enqueue(new MockResponse().setResponseCode(200).setBody("my-secret-value"));

        ConjurSecret secret = client.retrieveSecret("aws/rds/password");

        assertEquals("aws/rds/password", secret.getVariableId());
        assertEquals("my-secret-value", secret.getValue());
        assertNotNull(secret.getRetrievedAt());
    }

    @Test
    void retrieveSecret_throwsOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThrows(ConjurClientException.class,
                () -> client.retrieveSecret("aws/rds/password"));
    }

    @Test
    void retrieveSecrets_batchFetchesAll() throws Exception {
        String batchJson = """
                {
                  "testaccount:variable:aws/rds/host": "db.example.com",
                  "testaccount:variable:aws/rds/port": "5432"
                }
                """;
        server.enqueue(new MockResponse().setResponseCode(200).setBody(batchJson));

        Map<String, ConjurSecret> results = client.retrieveSecrets(
                List.of("aws/rds/host", "aws/rds/port"));

        assertEquals(2, results.size());
        assertEquals("db.example.com", results.get("aws/rds/host").getValue());
        assertEquals("5432", results.get("aws/rds/port").getValue());
    }

    @Test
    void retrieveSecret_refreshesTokenOn401() throws Exception {
        // First call returns 401, then a new token is fetched, then the secret
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("refreshed-token"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("secret-after-refresh"));

        ConjurSecret secret = client.retrieveSecret("aws/api/key");

        assertEquals("secret-after-refresh", secret.getValue());
        // Verify a re-auth request was made
        assertTrue(server.getRequestCount() >= 3);
    }

    @Test
    void setSecret_sendsPostRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));

        client.setSecret("aws/api/key", "new-value");

        RecordedRequest authReq = server.takeRequest();
        assertTrue(authReq.getPath().contains("authenticate"));

        RecordedRequest setReq = server.takeRequest();
        assertTrue(setReq.getPath().contains("secrets"));
        assertEquals("POST", setReq.getMethod());
    }
}
