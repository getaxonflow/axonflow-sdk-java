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
 *     .endpoint("http://localhost:8080")
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

    /** SDK version string. */
    public static final String SDK_VERSION = "3.8.0";

    /** Default timeout for HTTP requests. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** Default endpoint URL. */
    public static final String DEFAULT_ENDPOINT = "http://localhost:8080";

    private final String endpoint;
    private final String clientId;
    private final String clientSecret;
    private final Mode mode;
    private final Duration timeout;
    private final boolean debug;
    private final boolean insecureSkipVerify;
    private final RetryConfig retryConfig;
    private final CacheConfig cacheConfig;
    private final String userAgent;
    private final Boolean telemetry;

    private AxonFlowConfig(Builder builder) {
        this.endpoint = normalizeUrl(builder.endpoint != null ? builder.endpoint : DEFAULT_ENDPOINT);
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.mode = builder.mode != null ? builder.mode : Mode.PRODUCTION;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.debug = builder.debug;
        this.insecureSkipVerify = builder.insecureSkipVerify;
        this.retryConfig = builder.retryConfig != null ? builder.retryConfig : RetryConfig.defaults();
        this.cacheConfig = builder.cacheConfig != null ? builder.cacheConfig : CacheConfig.defaults();
        this.userAgent = builder.userAgent != null ? builder.userAgent : "axonflow-sdk-java/" + SDK_VERSION;
        this.telemetry = builder.telemetry;

        validate();
    }

    private void validate() {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new ConfigurationException("endpoint is required", "endpoint");
        }
        // Credentials are optional for community/self-hosted deployments
        // Enterprise features require credentials (validated at method call time)
    }

    /**
     * Checks if credentials are configured.
     *
     * <p>Returns true if clientId is set.
     * clientSecret is optional for community mode but required for enterprise.
     *
     * @return true if clientId is available
     */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isEmpty();
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Checks if the configured endpoint is localhost.
     *
     * @return true if connecting to localhost
     */
    public boolean isLocalhost() {
        return endpoint != null && (
            endpoint.contains("localhost") ||
            endpoint.contains("127.0.0.1") ||
            endpoint.contains("[::1]")
        );
    }

    /**
     * Creates a configuration from environment variables.
     *
     * <p>Supported environment variables:
     * <ul>
     *   <li>AXONFLOW_AGENT_URL - The endpoint URL (kept for backwards compatibility)</li>
     *   <li>AXONFLOW_CLIENT_ID - The client ID</li>
     *   <li>AXONFLOW_CLIENT_SECRET - The client secret</li>
     *   <li>AXONFLOW_MODE - Operating mode (production/sandbox)</li>
     *   <li>AXONFLOW_TIMEOUT_SECONDS - Request timeout in seconds</li>
     *   <li>AXONFLOW_DEBUG - Enable debug mode (true/false)</li>
     * </ul>
     *
     * @return a new configuration based on environment variables
     */
    public static AxonFlowConfig fromEnvironment() {
        Builder builder = builder();

        // Keep AXONFLOW_AGENT_URL for backwards compatibility, map to endpoint
        String endpoint = System.getenv("AXONFLOW_AGENT_URL");
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpoint(endpoint);
        }

        String clientId = System.getenv("AXONFLOW_CLIENT_ID");
        if (clientId != null && !clientId.isEmpty()) {
            builder.clientId(clientId);
        }

        String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
        if (clientSecret != null && !clientSecret.isEmpty()) {
            builder.clientSecret(clientSecret);
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

    public String getEndpoint() {
        return endpoint;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
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

    /**
     * Returns the telemetry config override.
     *
     * <p>{@code null} means use the default behavior (ON for production, OFF for sandbox).
     * {@code Boolean.TRUE} forces telemetry on, {@code Boolean.FALSE} forces it off.
     *
     * @return the telemetry override, or null for default behavior
     */
    public Boolean getTelemetry() {
        return telemetry;
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
               Objects.equals(endpoint, that.endpoint) &&
               Objects.equals(clientId, that.clientId) &&
               mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, clientId, mode, debug, insecureSkipVerify);
    }

    @Override
    public String toString() {
        return "AxonFlowConfig{" +
               "endpoint='" + endpoint + '\'' +
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
        private String endpoint;
        private String clientId;
        private String clientSecret;
        private Mode mode;
        private Duration timeout;
        private boolean debug;
        private boolean insecureSkipVerify;
        private RetryConfig retryConfig;
        private CacheConfig cacheConfig;
        private String userAgent;
        private Boolean telemetry;

        private Builder() {}

        /**
         * Sets the AxonFlow endpoint URL.
         * All routes now go through a single endpoint (ADR-026 Single Entry Point).
         *
         * @param endpoint the endpoint URL
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the AxonFlow Agent URL.
         * @deprecated Use {@link #endpoint(String)} instead. This method is kept for backwards compatibility.
         *
         * @param agentUrl the Agent URL
         * @return this builder
         */
        @Deprecated
        public Builder agentUrl(String agentUrl) {
            this.endpoint = agentUrl;
            return this;
        }

        // Note: portalUrl() and orchestratorUrl() methods were removed in v2.0.0
        // All routes now go through a single endpoint (ADR-026 Single Entry Point)

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
         * Sets the telemetry override.
         *
         * <p>{@code null} (default) uses the mode-based default: ON for production, OFF for sandbox.
         * {@code Boolean.TRUE} forces telemetry on, {@code Boolean.FALSE} forces it off.
         *
         * <p>Telemetry can also be disabled globally via environment variables:
         * {@code DO_NOT_TRACK=1} or {@code AXONFLOW_TELEMETRY=off}.
         *
         * @param telemetry true to enable, false to disable, null for default behavior
         * @return this builder
         */
        public Builder telemetry(Boolean telemetry) {
            this.telemetry = telemetry;
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
