package com.example.conjur.auth;

import com.example.conjur.model.ConjurConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Authenticates with Conjur using an API key.
 *
 * Flow:
 *   POST /authn/{account}/{login}/authenticate  with body = API key
 *   → With "Accept-Encoding: base64", Conjur returns the access token
 *     already Base64-encoded, ready for the Authorization header
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
            // Conjur returns the token already Base64-encoded (Accept-Encoding: base64)
            log.info("Successfully authenticated with Conjur (API key)");
            return token;
        } catch (IOException e) {
            throw new ConjurAuthException("Network error during Conjur authentication", e);
        }
    }
}
