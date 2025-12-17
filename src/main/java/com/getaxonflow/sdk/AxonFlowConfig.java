/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.exceptions.ConfigurationException;
import com.getaxonflow.sdk.types.Mode;
import com.getaxonflow.sdk.util.RetryConfig;
import com.getaxonflow.sdk.util.CacheConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the AxonFlow client.
 *
 * <p>Use the builder to create a configuration:
 * <pre>{@code
 * AxonFlowConfig config = AxonFlowConfig.builder()
 *     .agentUrl("http://localhost:8080")
 *     .clientId("my-client")
 *     .clientSecret("my-secret")
 *     .build();
 * }</pre>
 *
 * <p>Configuration can also be loaded from environment variables:
 * <pre>{@code
 * AxonFlowConfig config = AxonFlowConfig.fromEnvironment();
 * }</pre>
 */
public final class AxonFlowConfig {

    /** Default timeout for HTTP requests. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** Default Agent URL. */
    public static final String DEFAULT_AGENT_URL = "http://localhost:8080";

    private final String agentUrl;
    private final String clientId;
    private final String clientSecret;
    private final String licenseKey;
    private final Mode mode;
    private final Duration timeout;
    private final boolean debug;
    private final boolean insecureSkipVerify;
    private final RetryConfig retryConfig;
    private final CacheConfig cacheConfig;
    private final String userAgent;

    private AxonFlowConfig(Builder builder) {
        this.agentUrl = normalizeUrl(builder.agentUrl != null ? builder.agentUrl : DEFAULT_AGENT_URL);
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.licenseKey = builder.licenseKey;
        this.mode = builder.mode != null ? builder.mode : Mode.PRODUCTION;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.debug = builder.debug;
        this.insecureSkipVerify = builder.insecureSkipVerify;
        this.retryConfig = builder.retryConfig != null ? builder.retryConfig : RetryConfig.defaults();
        this.cacheConfig = builder.cacheConfig != null ? builder.cacheConfig : CacheConfig.defaults();
        this.userAgent = builder.userAgent != null ? builder.userAgent : "axonflow-java-sdk/1.0.0";

        validate();
    }

    private void validate() {
        if (agentUrl == null || agentUrl.isEmpty()) {
            throw new ConfigurationException("agentUrl is required", "agentUrl");
        }

        // For non-localhost, require either license key or client credentials
        if (!isLocalhost()) {
            if (licenseKey == null && (clientId == null || clientSecret == null)) {
                throw new ConfigurationException(
                    "Either licenseKey or both clientId and clientSecret are required for non-localhost connections",
                    "authentication"
                );
            }
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Checks if the configured agent URL is localhost.
     *
     * @return true if connecting to localhost
     */
    public boolean isLocalhost() {
        return agentUrl != null && (
            agentUrl.contains("localhost") ||
            agentUrl.contains("127.0.0.1") ||
            agentUrl.contains("[::1]")
        );
    }

    /**
     * Creates a configuration from environment variables.
     *
     * <p>Supported environment variables:
     * <ul>
     *   <li>AXONFLOW_AGENT_URL - The Agent URL</li>
     *   <li>AXONFLOW_CLIENT_ID - The client ID</li>
     *   <li>AXONFLOW_CLIENT_SECRET - The client secret</li>
     *   <li>AXONFLOW_LICENSE_KEY - The license key</li>
     *   <li>AXONFLOW_MODE - Operating mode (production/sandbox)</li>
     *   <li>AXONFLOW_TIMEOUT_SECONDS - Request timeout in seconds</li>
     *   <li>AXONFLOW_DEBUG - Enable debug mode (true/false)</li>
     * </ul>
     *
     * @return a new configuration based on environment variables
     */
    public static AxonFlowConfig fromEnvironment() {
        Builder builder = builder();

        String agentUrl = System.getenv("AXONFLOW_AGENT_URL");
        if (agentUrl != null && !agentUrl.isEmpty()) {
            builder.agentUrl(agentUrl);
        }

        String clientId = System.getenv("AXONFLOW_CLIENT_ID");
        if (clientId != null && !clientId.isEmpty()) {
            builder.clientId(clientId);
        }

        String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
        if (clientSecret != null && !clientSecret.isEmpty()) {
            builder.clientSecret(clientSecret);
        }

        String licenseKey = System.getenv("AXONFLOW_LICENSE_KEY");
        if (licenseKey != null && !licenseKey.isEmpty()) {
            builder.licenseKey(licenseKey);
        }

        String modeStr = System.getenv("AXONFLOW_MODE");
        if (modeStr != null && !modeStr.isEmpty()) {
            builder.mode(Mode.fromValue(modeStr));
        }

        String timeoutStr = System.getenv("AXONFLOW_TIMEOUT_SECONDS");
        if (timeoutStr != null && !timeoutStr.isEmpty()) {
            try {
                builder.timeout(Duration.ofSeconds(Long.parseLong(timeoutStr)));
            } catch (NumberFormatException e) {
                // Ignore invalid timeout, use default
            }
        }

        String debugStr = System.getenv("AXONFLOW_DEBUG");
        if ("true".equalsIgnoreCase(debugStr)) {
            builder.debug(true);
        }

        return builder.build();
    }

    public String getAgentUrl() {
        return agentUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public Mode getMode() {
        return mode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isInsecureSkipVerify() {
        return insecureSkipVerify;
    }

    public RetryConfig getRetryConfig() {
        return retryConfig;
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AxonFlowConfig that = (AxonFlowConfig) o;
        return debug == that.debug &&
               insecureSkipVerify == that.insecureSkipVerify &&
               Objects.equals(agentUrl, that.agentUrl) &&
               Objects.equals(clientId, that.clientId) &&
               Objects.equals(licenseKey, that.licenseKey) &&
               mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentUrl, clientId, licenseKey, mode, debug, insecureSkipVerify);
    }

    @Override
    public String toString() {
        return "AxonFlowConfig{" +
               "agentUrl='" + agentUrl + '\'' +
               ", clientId='" + clientId + '\'' +
               ", mode=" + mode +
               ", timeout=" + timeout +
               ", debug=" + debug +
               '}';
    }

    /**
     * Builder for AxonFlowConfig.
     */
    public static final class Builder {
        private String agentUrl;
        private String clientId;
        private String clientSecret;
        private String licenseKey;
        private Mode mode;
        private Duration timeout;
        private boolean debug;
        private boolean insecureSkipVerify;
        private RetryConfig retryConfig;
        private CacheConfig cacheConfig;
        private String userAgent;

        private Builder() {}

        /**
         * Sets the AxonFlow Agent URL.
         *
         * @param agentUrl the Agent URL
         * @return this builder
         */
        public Builder agentUrl(String agentUrl) {
            this.agentUrl = agentUrl;
            return this;
        }

        /**
         * Sets the client ID for authentication.
         *
         * @param clientId the client ID
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the client secret for authentication.
         *
         * @param clientSecret the client secret
         * @return this builder
         */
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /**
         * Sets the license key for SaaS authentication.
         *
         * @param licenseKey the license key
         * @return this builder
         */
        public Builder licenseKey(String licenseKey) {
            this.licenseKey = licenseKey;
            return this;
        }

        /**
         * Sets the operating mode.
         *
         * @param mode the mode (PRODUCTION or SANDBOX)
         * @return this builder
         */
        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the request timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables debug mode for verbose logging.
         *
         * @param debug true to enable debug mode
         * @return this builder
         */
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * Skips SSL certificate verification.
         *
         * <p><strong>Warning:</strong> Only use this in development/testing.
         *
         * @param insecureSkipVerify true to skip verification
         * @return this builder
         */
        public Builder insecureSkipVerify(boolean insecureSkipVerify) {
            this.insecureSkipVerify = insecureSkipVerify;
            return this;
        }

        /**
         * Sets the retry configuration.
         *
         * @param retryConfig the retry configuration
         * @return this builder
         */
        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        /**
         * Sets the cache configuration.
         *
         * @param cacheConfig the cache configuration
         * @return this builder
         */
        public Builder cacheConfig(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Sets a custom user agent string.
         *
         * @param userAgent the user agent string
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new AxonFlowConfig instance
         * @throws ConfigurationException if the configuration is invalid
         */
        public AxonFlowConfig build() {
            return new AxonFlowConfig(this);
        }
    }
}
