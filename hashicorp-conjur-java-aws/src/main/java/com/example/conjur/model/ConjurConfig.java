package com.example.conjur.model;

/**
 * Immutable configuration for connecting to a Conjur server.
 * Supports API key and AWS IAM authentication modes.
 */
public class ConjurConfig {

    public enum AuthMethod {
        API_KEY,
        AWS_IAM
    }

    private final String applianceUrl;
    private final String account;
    private final String login;
    private final String apiKey;
    private final AuthMethod authMethod;
    private final String awsServiceId;
    private final boolean sslVerify;

    private ConjurConfig(Builder builder) {
        this.applianceUrl = builder.applianceUrl;
        this.account = builder.account;
        this.login = builder.login;
        this.apiKey = builder.apiKey;
        this.authMethod = builder.authMethod;
        this.awsServiceId = builder.awsServiceId;
        this.sslVerify = builder.sslVerify;
    }

    public String getApplianceUrl() { return applianceUrl; }
    public String getAccount() { return account; }
    public String getLogin() { return login; }
    public String getApiKey() { return apiKey; }
    public AuthMethod getAuthMethod() { return authMethod; }
    public String getAwsServiceId() { return awsServiceId; }
    public boolean isSslVerify() { return sslVerify; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String applianceUrl;
        private String account;
        private String login;
        private String apiKey;
        private AuthMethod authMethod = AuthMethod.API_KEY;
        private String awsServiceId = "prod";
        private boolean sslVerify = true;

        public Builder applianceUrl(String applianceUrl) {
            this.applianceUrl = applianceUrl;
            return this;
        }

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public Builder login(String login) {
            this.login = login;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder authMethod(AuthMethod authMethod) {
            this.authMethod = authMethod;
            return this;
        }

        public Builder awsServiceId(String awsServiceId) {
            this.awsServiceId = awsServiceId;
            return this;
        }

        public Builder sslVerify(boolean sslVerify) {
            this.sslVerify = sslVerify;
            return this;
        }

        public ConjurConfig build() {
            if (applianceUrl == null || applianceUrl.isBlank()) {
                throw new IllegalArgumentException("applianceUrl is required");
            }
            if (account == null || account.isBlank()) {
                throw new IllegalArgumentException("account is required");
            }
            if (login == null || login.isBlank()) {
                throw new IllegalArgumentException("login is required");
            }
            if (authMethod == AuthMethod.API_KEY && (apiKey == null || apiKey.isBlank())) {
                throw new IllegalArgumentException("apiKey is required for API_KEY auth method");
            }
            return new ConjurConfig(this);
        }

        /** Populate config from environment variables. */
        public Builder fromEnvironment() {
            String url = System.getenv("CONJUR_APPLIANCE_URL");
            String acct = System.getenv("CONJUR_ACCOUNT");
            String lgn = System.getenv("CONJUR_AUTHN_LOGIN");
            String key = System.getenv("CONJUR_AUTHN_API_KEY");
            String method = System.getenv("CONJUR_AUTH_METHOD");
            String serviceId = System.getenv("CONJUR_AWS_SERVICE_ID");

            if (url != null) this.applianceUrl = url;
            if (acct != null) this.account = acct;
            if (lgn != null) this.login = lgn;
            if (key != null) this.apiKey = key;
            if (serviceId != null) this.awsServiceId = serviceId;
            if ("AWS_IAM".equalsIgnoreCase(method)) {
                this.authMethod = AuthMethod.AWS_IAM;
            }
            return this;
        }
    }
}
