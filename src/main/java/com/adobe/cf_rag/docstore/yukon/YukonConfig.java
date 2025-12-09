package com.adobe.cf_rag.docstore.yukon;

/**
 * Configuration for connecting to Yukon API with IMS authentication.
 */
public class YukonConfig {

    private final String clientId;
    private final String clientSecret;
    private final String authorizationCode;
    private final String imsHost;
    private final String yukonBaseUrl;

    private YukonConfig(Builder builder) {
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.authorizationCode = builder.authorizationCode;
        this.imsHost = builder.imsHost;
        this.yukonBaseUrl = builder.yukonBaseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public String getImsHost() {
        return imsHost;
    }

    public String getYukonBaseUrl() {
        return yukonBaseUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String clientId;
        private String clientSecret;
        private String authorizationCode;
        private String imsHost = "ims-na1.adobelogin.com";
        private String yukonBaseUrl = "https://yukon.adobe.io";

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder authorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
            return this;
        }

        public Builder imsHost(String imsHost) {
            this.imsHost = imsHost;
            return this;
        }

        public Builder yukonBaseUrl(String yukonBaseUrl) {
            this.yukonBaseUrl = yukonBaseUrl;
            return this;
        }

        public YukonConfig build() {
            if (clientId == null || clientId.isEmpty()) {
                throw new IllegalArgumentException("clientId is required");
            }
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new IllegalArgumentException("clientSecret is required");
            }
            if (authorizationCode == null || authorizationCode.isEmpty()) {
                throw new IllegalArgumentException("authorizationCode is required");
            }
            return new YukonConfig(this);
        }
    }
}

