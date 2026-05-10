package com.example.conjur;

import com.example.conjur.model.ConjurConfig;
import com.example.conjur.service.ConjurClientFactory;
import com.example.conjur.service.ConjurSecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates retrieving AWS-related secrets from Conjur.
 *
 * Required environment variables:
 *   CONJUR_APPLIANCE_URL  – e.g. https://conjur.example.com
 *   CONJUR_ACCOUNT        – e.g. myorg
 *   CONJUR_AUTHN_LOGIN    – e.g. host/aws/my-ec2-instance
 *   CONJUR_AUTHN_API_KEY  – API key (for API_KEY mode)
 *   CONJUR_AUTH_METHOD    – "API_KEY" or "AWS_IAM" (default: API_KEY)
 *   CONJUR_AWS_SERVICE_ID – Conjur AWS authn service ID (default: prod)
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ConjurConfig config = ConjurConfig.builder()
                .fromEnvironment()
                .build();

        ConjurSecretManager manager = ConjurClientFactory.create(config, Duration.ofMinutes(10));

        log.info("=== HashiCorp Conjur + AWS Secret Management Demo ===");

        // --- Example 1: Retrieve a single secret ---
        log.info("Retrieving single secret: aws/rds/password");
        String dbPassword = manager.getSecret("aws/rds/password");
        log.info("Retrieved secret (length={})", dbPassword.length());

        // --- Example 2: Batch retrieval ---
        log.info("Batch retrieving secrets...");
        Map<String, String> secrets = manager.getSecrets(List.of(
                "aws/rds/host",
                "aws/rds/port",
                "aws/rds/dbname",
                "aws/rds/username",
                "aws/rds/password"
        ));
        log.info("Retrieved {} secrets", secrets.size());

        // --- Example 3: Compose a JDBC URL ---
        String jdbcUrl = manager.getDatabaseUrl(
                "aws/rds/host",
                "aws/rds/port",
                "aws/rds/dbname",
                "aws/rds/username",
                "aws/rds/password"
        );
        log.info("JDBC URL (password redacted): {}",
                jdbcUrl.replaceAll("password=[^&]*", "password=[REDACTED]"));

        // --- Example 4: AWS credentials from Conjur ---
        log.info("Retrieving AWS credentials from Conjur...");
        ConjurSecretManager.AwsCredentials awsCredentials = manager.getAwsCredentials(
                "aws/iam/access-key-id",
                "aws/iam/secret-access-key"
        );
        log.info("AWS credentials: {}", awsCredentials);

        // --- Example 5: Secret rotation ---
        log.info("Rotating an API key in Conjur...");
        manager.rotateSecret("aws/api/third-party-key", generateNewApiKey());
        log.info("Rotation complete");

        log.info("=== Demo complete ===");
    }

    private static String generateNewApiKey() {
        // In production use a cryptographically secure random generator
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
