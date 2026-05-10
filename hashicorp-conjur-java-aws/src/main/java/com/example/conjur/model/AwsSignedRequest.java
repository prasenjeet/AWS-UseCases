package com.example.conjur.model;

/** Contains the signed AWS request headers needed for Conjur AWS IAM authentication. */
public class AwsSignedRequest {

    private final String host;
    private final String xAmzDate;
    private final String xAmzSecurityToken;
    private final String authorization;

    public AwsSignedRequest(String host, String xAmzDate,
                            String xAmzSecurityToken, String authorization) {
        this.host = host;
        this.xAmzDate = xAmzDate;
        this.xAmzSecurityToken = xAmzSecurityToken;
        this.authorization = authorization;
    }

    public String getHost() { return host; }
    public String getXAmzDate() { return xAmzDate; }
    public String getXAmzSecurityToken() { return xAmzSecurityToken; }
    public String getAuthorization() { return authorization; }
}
