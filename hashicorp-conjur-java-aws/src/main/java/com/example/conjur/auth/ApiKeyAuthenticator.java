package com.example.conjur.auth;

import com.example.conjur.model.ConjurConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Authenticates with Conjur using an API key.
 *
 * Flow:
 *   POST /authn/{account}/{login}/authenticate  with body = API key
 *   → Conjur returns a short-lived access token (raw JSON)
 *   → We Base64-encode it for use in subsequent requests
 */
public class ApiKeyAuthenticator implements ConjurAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticator.class);

    private final ConjurConfig config;
    private final OkHttpClient httpClient;

    public ApiKeyAuthenticator(ConjurConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public String authenticate() throws ConjurAuthException {
        String encodedLogin = URLEncoder.encode(config.getLogin(), StandardCharsets.UTF_8);
        String url = String.format("%s/authn/%s/%s/authenticate",
                config.getApplianceUrl(), config.getAccount(), encodedLogin);

        RequestBody body = RequestBody.create(
                config.getApiKey(), MediaType.parse("text/plain"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Encoding", "base64")
                .build();

        log.debug("Authenticating with Conjur via API key: {}", url);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ConjurAuthException(
                        "Conjur API key authentication failed: HTTP " + response.code());
            }
            String token = response.body() != null ? response.body().string() : "";
            // Conjur returns the token as a raw JSON string; Base64-encode for the Authorization header
            String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            log.info("Successfully authenticated with Conjur (API key)");
            return encoded;
        } catch (IOException e) {
            throw new ConjurAuthException("Network error during Conjur authentication", e);
        }
    }
}
