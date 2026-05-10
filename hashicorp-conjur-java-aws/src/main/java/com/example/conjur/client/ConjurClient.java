package com.example.conjur.client;

import com.example.conjur.auth.ConjurAuthenticator;
import com.example.conjur.model.ConjurConfig;
import com.example.conjur.model.ConjurSecret;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Low-level client for the Conjur REST API.
 * Handles token lifecycle and all HTTP interactions.
 */
public class ConjurClient {

    private static final Logger log = LoggerFactory.getLogger(ConjurClient.class);

    private final ConjurConfig config;
    private final OkHttpClient httpClient;
    private final ConjurAuthenticator authenticator;
    private final Gson gson = new Gson();

    /** Cached token – refreshed on 401 or expiry. */
    private volatile String accessToken;

    public ConjurClient(ConjurConfig config, OkHttpClient httpClient,
                        ConjurAuthenticator authenticator) {
        this.config = config;
        this.httpClient = httpClient;
        this.authenticator = authenticator;
    }

    // -------------------------------------------------------------------------
    // Secret retrieval
    // -------------------------------------------------------------------------

    /** Retrieve a single secret by its fully-qualified variable ID. */
    public ConjurSecret retrieveSecret(String variableId) {
        String encodedId = URLEncoder.encode(variableId, StandardCharsets.UTF_8);
        String url = String.format("%s/secrets/%s/variable/%s",
                config.getApplianceUrl(), config.getAccount(), encodedId);

        String value = executeGet(url);
        return new ConjurSecret(variableId, value);
    }

    /**
     * Batch-retrieve multiple secrets in a single round trip.
     * Returns a map of variableId → ConjurSecret.
     */
    public Map<String, ConjurSecret> retrieveSecrets(List<String> variableIds) {
        if (variableIds == null || variableIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        String joined = variableIds.stream()
                .map(id -> URLEncoder.encode(config.getAccount() + ":variable:" + id,
                        StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));

        String url = config.getApplianceUrl() + "/secrets?variable_ids=" + joined;
        String json = executeGet(url);

        Map<String, String> raw = gson.fromJson(json,
                new TypeToken<Map<String, String>>() {}.getType());

        Map<String, ConjurSecret> result = new LinkedHashMap<>();
        for (String variableId : variableIds) {
            String qualifiedKey = config.getAccount() + ":variable:" + variableId;
            String value = raw.getOrDefault(qualifiedKey, "");
            result.put(variableId, new ConjurSecret(variableId, value));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Secret management
    // -------------------------------------------------------------------------

    /** Set (or rotate) the value of a variable in Conjur. */
    public void setSecret(String variableId, String value) {
        String encodedId = URLEncoder.encode(variableId, StandardCharsets.UTF_8);
        String url = String.format("%s/secrets/%s/variable/%s",
                config.getApplianceUrl(), config.getAccount(), encodedId);

        Request request = buildRequest(url)
                .post(RequestBody.create(value, MediaType.parse("text/plain")))
                .build();

        executeRequest(request, "set secret for " + variableId);
        log.info("Secret updated: {}", variableId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String executeGet(String url) {
        Request request = buildRequest(url).get().build();
        return executeRequest(request, "GET " + url);
    }

    private String executeRequest(Request request, String description) {
        ensureAuthenticated();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                // Token may have expired – refresh once and retry
                log.debug("Received 401, refreshing token and retrying");
                accessToken = authenticator.authenticate();
                Request retried = request.newBuilder()
                        .header("Authorization", "Token token=\"" + accessToken + "\"")
                        .build();
                try (Response retryResponse = httpClient.newCall(retried).execute()) {
                    return extractBody(retryResponse, description);
                }
            }
            return extractBody(response, description);
        } catch (IOException e) {
            throw new ConjurClientException("Network error during: " + description, e);
        }
    }

    private String extractBody(Response response, String description) throws IOException {
        if (!response.isSuccessful()) {
            throw new ConjurClientException(
                    "Conjur request failed [" + description + "]: HTTP " + response.code());
        }
        return response.body() != null ? response.body().string() : "";
    }

    private Request.Builder buildRequest(String url) {
        ensureAuthenticated();
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Token token=\"" + accessToken + "\"");
    }

    private void ensureAuthenticated() {
        if (accessToken == null) {
            accessToken = authenticator.authenticate();
        }
    }
}
