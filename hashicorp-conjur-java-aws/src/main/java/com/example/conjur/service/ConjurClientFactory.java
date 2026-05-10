package com.example.conjur.service;

import com.example.conjur.auth.ApiKeyAuthenticator;
import com.example.conjur.auth.AwsIamAuthenticator;
import com.example.conjur.auth.ConjurAuthenticator;
import com.example.conjur.client.ConjurClient;
import com.example.conjur.model.ConjurConfig;
import okhttp3.OkHttpClient;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Factory that wires together config, authenticator, HTTP client,
 * and cache into a ready-to-use ConjurSecretManager.
 */
public class ConjurClientFactory {

    private ConjurClientFactory() {}

    /**
     * Build a ConjurSecretManager from the provided config.
     *
     * @param config  Conjur connection config
     * @param cacheTtl how long secrets are cached before re-fetching
     */
    public static ConjurSecretManager create(ConjurConfig config, Duration cacheTtl) {
        OkHttpClient httpClient = buildHttpClient(config);
        ConjurAuthenticator authenticator = selectAuthenticator(config, httpClient);
        ConjurClient client = new ConjurClient(config, httpClient, authenticator);
        SecretCache cache = new SecretCache(cacheTtl);
        return new ConjurSecretManager(client, cache);
    }

    /** Convenience overload with a 5-minute default cache TTL. */
    public static ConjurSecretManager create(ConjurConfig config) {
        return create(config, Duration.ofMinutes(5));
    }

    private static ConjurAuthenticator selectAuthenticator(ConjurConfig config, OkHttpClient client) {
        return switch (config.getAuthMethod()) {
            case AWS_IAM -> new AwsIamAuthenticator(config, client);
            default -> new ApiKeyAuthenticator(config, client);
        };
    }

    private static OkHttpClient buildHttpClient(ConjurConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS);

        if (!config.isSslVerify()) {
            // Development/test only – never disable SSL verification in production
            builder = applyTrustAllSsl(builder);
        }

        return builder.build();
    }

    private static OkHttpClient.Builder applyTrustAllSsl(OkHttpClient.Builder builder) {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            return builder
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure trust-all SSL", e);
        }
    }
}
