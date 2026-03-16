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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request to update circuit breaker configuration for a tenant.
 *
 * <p>Use the {@link Builder} to construct instances:
 * <pre>{@code
 * CircuitBreakerConfigUpdate update = CircuitBreakerConfigUpdate.builder()
 *     .tenantId("tenant_123")
 *     .errorThreshold(10)
 *     .enableAutoRecovery(true)
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CircuitBreakerConfigUpdate {

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("error_threshold")
    private final Integer errorThreshold;

    @JsonProperty("violation_threshold")
    private final Integer violationThreshold;

    @JsonProperty("window_seconds")
    private final Integer windowSeconds;

    @JsonProperty("default_timeout_seconds")
    private final Integer defaultTimeoutSeconds;

    @JsonProperty("max_timeout_seconds")
    private final Integer maxTimeoutSeconds;

    @JsonProperty("enable_auto_recovery")
    private final Boolean enableAutoRecovery;

    private CircuitBreakerConfigUpdate(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId cannot be null");
        if (this.tenantId.isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be empty");
        }
        this.errorThreshold = builder.errorThreshold;
        this.violationThreshold = builder.violationThreshold;
        this.windowSeconds = builder.windowSeconds;
        this.defaultTimeoutSeconds = builder.defaultTimeoutSeconds;
        this.maxTimeoutSeconds = builder.maxTimeoutSeconds;
        this.enableAutoRecovery = builder.enableAutoRecovery;
    }

    /**
     * Creates a new builder for CircuitBreakerConfigUpdate.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getTenantId() { return tenantId; }
    public Integer getErrorThreshold() { return errorThreshold; }
    public Integer getViolationThreshold() { return violationThreshold; }
    public Integer getWindowSeconds() { return windowSeconds; }
    public Integer getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public Integer getMaxTimeoutSeconds() { return maxTimeoutSeconds; }
    public Boolean getEnableAutoRecovery() { return enableAutoRecovery; }

    /**
     * Builder for {@link CircuitBreakerConfigUpdate}.
     */
    public static final class Builder {
        private String tenantId;
        private Integer errorThreshold;
        private Integer violationThreshold;
        private Integer windowSeconds;
        private Integer defaultTimeoutSeconds;
        private Integer maxTimeoutSeconds;
        private Boolean enableAutoRecovery;

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder errorThreshold(int errorThreshold) { this.errorThreshold = errorThreshold; return this; }
        public Builder violationThreshold(int violationThreshold) { this.violationThreshold = violationThreshold; return this; }
        public Builder windowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; return this; }
        public Builder defaultTimeoutSeconds(int defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; return this; }
        public Builder maxTimeoutSeconds(int maxTimeoutSeconds) { this.maxTimeoutSeconds = maxTimeoutSeconds; return this; }
        public Builder enableAutoRecovery(boolean enableAutoRecovery) { this.enableAutoRecovery = enableAutoRecovery; return this; }

        /**
         * Builds the CircuitBreakerConfigUpdate.
         *
         * @return the config update
         * @throws NullPointerException if tenantId is null
         * @throws IllegalArgumentException if tenantId is empty
         */
        public CircuitBreakerConfigUpdate build() {
            return new CircuitBreakerConfigUpdate(this);
        }
    }
}
