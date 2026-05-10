# HashiCorp Conjur Java AWS Integration

A Java library and demo application for managing secrets stored in **CyberArk Conjur** from within AWS environments. Supports both API key and AWS IAM authentication, batch secret retrieval, TTL-based caching, and secret rotation.

## Project Structure

```
hashicorp-conjur-java-aws/
├── pom.xml
└── src/
    ├── main/java/com/example/conjur/
    │   ├── Main.java                          # Demo entry point
    │   ├── auth/
    │   │   ├── ConjurAuthenticator.java       # Authentication interface
    │   │   ├── ApiKeyAuthenticator.java       # API key auth implementation
    │   │   ├── AwsIamAuthenticator.java       # AWS IAM (SigV4) auth implementation
    │   │   └── ConjurAuthException.java
    │   ├── client/
    │   │   ├── ConjurClient.java              # Low-level Conjur REST API client
    │   │   └── ConjurClientException.java
    │   ├── model/
    │   │   ├── ConjurConfig.java              # Connection configuration (builder)
    │   │   ├── ConjurSecret.java              # Retrieved secret + metadata
    │   │   └── AwsSignedRequest.java          # AWS SigV4 signed headers
    │   └── service/
    │       ├── ConjurSecretManager.java       # High-level secret management service
    │       ├── ConjurClientFactory.java       # Wires all components together
    │       └── SecretCache.java              # TTL-based in-memory secret cache
    └── test/java/com/example/conjur/
        ├── ConjurClientTest.java              # Client tests (MockWebServer)
        ├── ConjurSecretManagerTest.java       # Manager tests (Mockito)
        └── SecretCacheTest.java              # Cache unit tests
```

## Authentication Modes

### API Key (default)

```java
ConjurConfig config = ConjurConfig.builder()
    .applianceUrl("https://conjur.example.com")
    .account("myorg")
    .login("host/aws/my-ec2-instance")
    .apiKey("3zt5qs1yx...")
    .build();
```

### AWS IAM (recommended for EC2 / ECS / Lambda)

No static credentials needed – Conjur validates the identity via AWS STS.

```java
ConjurConfig config = ConjurConfig.builder()
    .applianceUrl("https://conjur.example.com")
    .account("myorg")
    .login("host/aws/my-ec2-instance")
    .authMethod(ConjurConfig.AuthMethod.AWS_IAM)
    .awsServiceId("prod")
    .build();
```

## Quick Start

### 1. Set environment variables

```bash
export CONJUR_APPLIANCE_URL=https://conjur.example.com
export CONJUR_ACCOUNT=myorg
export CONJUR_AUTHN_LOGIN=host/aws/my-ec2-instance
export CONJUR_AUTHN_API_KEY=<your-api-key>
# or for AWS IAM auth:
# export CONJUR_AUTH_METHOD=AWS_IAM
```

### 2. Build and run

```bash
mvn clean package
java -jar target/hashicorp-conjur-java-aws-1.0.0.jar
```

### 3. Run tests

```bash
mvn test
```

## Usage in your application

```java
// Build the manager (5-minute secret cache TTL)
ConjurSecretManager manager = ConjurClientFactory.create(config, Duration.ofMinutes(5));

// Single secret
String dbPassword = manager.getSecret("aws/rds/password");

// Batch retrieval (single network round trip)
Map<String, String> secrets = manager.getSecrets(List.of(
    "aws/rds/host", "aws/rds/port", "aws/rds/password"
));

// Pre-composed JDBC URL
String jdbcUrl = manager.getDatabaseUrl(
    "aws/rds/host", "aws/rds/port", "aws/rds/dbname",
    "aws/rds/username", "aws/rds/password"
);

// AWS IAM credentials stored in Conjur
ConjurSecretManager.AwsCredentials creds =
    manager.getAwsCredentials("aws/iam/access-key-id", "aws/iam/secret-access-key");

// Rotate a secret (updates Conjur and invalidates cache)
manager.rotateSecret("aws/api/third-party-key", newKeyValue);
```

## Conjur Policy Example

```yaml
# policy.yml – grant the EC2 host access to RDS secrets
- !policy
  id: aws
  body:
    - !host my-ec2-instance

    - !policy
      id: rds
      body:
        - !variable host
        - !variable port
        - !variable dbname
        - !variable username
        - !variable password

    - !permit
      role: !host my-ec2-instance
      privileges: [read, execute]
      resources: !policy rds
```

Load with:
```bash
conjur policy load -b root -f policy.yml
conjur variable set -i aws/rds/host -v db.example.com
conjur variable set -i aws/rds/password -v supersecret
```

## AWS IAM Conjur Policy

```yaml
- !policy
  id: conjur/authn-iam/prod
  body:
    - !webservice

- !host
  id: aws/my-ec2-instance
  annotations:
    authn-iam/account: "123456789012"
    authn-iam/iam-role-name: "MyEC2Role"

- !permit
  role: !host aws/my-ec2-instance
  privilege: authenticate
  resource: !webservice conjur/authn-iam/prod
```

## Dependencies

| Library | Purpose |
|---------|---------|
| `software.amazon.awssdk:sts` | AWS STS for IAM authentication |
| `software.amazon.awssdk:secretsmanager` | AWS Secrets Manager integration |
| `com.squareup.okhttp3:okhttp` | HTTP client for Conjur REST API |
| `com.google.code.gson:gson` | JSON serialization |
| `ch.qos.logback:logback-classic` | Logging |
| `org.junit.jupiter:junit-jupiter` | Unit testing |
| `org.mockito:mockito-core` | Mocking in tests |
| `com.squareup.okhttp3:mockwebserver` | HTTP mock server for integration tests |
