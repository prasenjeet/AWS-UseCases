package com.example.conjur.auth;

import com.example.conjur.model.AwsSignedRequest;
import com.example.conjur.model.ConjurConfig;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticates with Conjur using AWS IAM identity.
 *
 * Flow:
 *   1. Build a signed AWS GetCallerIdentity request (STS, Signature V4)
 *   2. POST the signed headers as JSON to Conjur's AWS authenticator
 *   3. Conjur validates the signed request with AWS STS and issues an access token
 */
public class AwsIamAuthenticator implements ConjurAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(AwsIamAuthenticator.class);
    private static final String STS_HOST = "sts.amazonaws.com";
    private static final String STS_URL = "https://" + STS_HOST + "/?Action=GetCallerIdentity&Version=2011-06-15";

    private final ConjurConfig config;
    private final OkHttpClient httpClient;
    private final AwsCredentialsProvider credentialsProvider;
    private final Region region;
    private final Gson gson = new Gson();

    public AwsIamAuthenticator(ConjurConfig config, OkHttpClient httpClient) {
        this(config, httpClient, DefaultCredentialsProvider.create(), Region.US_EAST_1);
    }

    public AwsIamAuthenticator(ConjurConfig config, OkHttpClient httpClient,
                                AwsCredentialsProvider credentialsProvider, Region region) {
        this.config = config;
        this.httpClient = httpClient;
        this.credentialsProvider = credentialsProvider;
        this.region = region;
    }

    @Override
    public String authenticate() throws ConjurAuthException {
        log.debug("Authenticating with Conjur via AWS IAM");

        AwsSignedRequest signed = buildSignedRequest();
        String token = postToConjur(signed);

        log.info("Successfully authenticated with Conjur (AWS IAM)");
        // Conjur returns the token already Base64-encoded (Accept-Encoding: base64)
        return token;
    }

    /** Signs a GetCallerIdentity request using AWS SigV4. */
    private AwsSignedRequest buildSignedRequest() {
        try {
            SdkHttpFullRequest unsignedRequest = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(URI.create(STS_URL))
                    .putHeader("host", List.of(STS_HOST))
                    .build();

            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .signingRegion(region)
                    .signingName("sts")
                    .awsCredentials(credentialsProvider.resolveCredentials())
                    .signingClockOverride(Clock.systemUTC())
                    .build();

            Aws4Signer signer = Aws4Signer.create();
            SdkHttpFullRequest signed = signer.sign(unsignedRequest, signerParams);

            Map<String, List<String>> headers = signed.headers();
            String host = getFirstHeader(headers, "host");
            String xAmzDate = getFirstHeader(headers, "x-amz-date");
            String securityToken = getFirstHeader(headers, "x-amz-security-token");
            String authorization = getFirstHeader(headers, "Authorization");

            return new AwsSignedRequest(host, xAmzDate, securityToken, authorization);
        } catch (Exception e) {
            throw new ConjurAuthException("Failed to build AWS signed request", e);
        }
    }

    /** Sends the signed headers to Conjur's AWS authenticator endpoint. */
    private String postToConjur(AwsSignedRequest signed) {
        String encodedLogin = URLEncoder.encode(config.getLogin(), StandardCharsets.UTF_8);
        String url = String.format("%s/authn-iam/%s/%s/%s/authenticate",
                config.getApplianceUrl(),
                config.getAwsServiceId(),
                config.getAccount(),
                encodedLogin);

        Map<String, String> payload = new HashMap<>();
        payload.put("host", signed.getHost());
        payload.put("x-amz-date", signed.getXAmzDate());
        payload.put("x-amz-security-token", signed.getXAmzSecurityToken());
        payload.put("authorization", signed.getAuthorization());

        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Encoding", "base64")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ConjurAuthException(
                        "Conjur AWS IAM authentication failed: HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        } catch (IOException e) {
            throw new ConjurAuthException("Network error during Conjur IAM authentication", e);
        }
    }

    private String getFirstHeader(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : "";
    }
}
