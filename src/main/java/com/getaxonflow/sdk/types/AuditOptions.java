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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Options for auditing an LLM call in Gateway Mode.
 *
 * <p>This is the third step of the Gateway Mode pattern, used to log
 * the LLM call for compliance and observability.
 *
 * <p>Example usage:
 * <pre>{@code
 * AuditOptions options = AuditOptions.builder()
 *     .contextId(policyResult.getContextId())
 *     .responseSummary("Generated response about weather")
 *     .provider("openai")
 *     .model("gpt-4")
 *     .tokenUsage(TokenUsage.of(150, 200))
 *     .latencyMs(1234)
 *     .build();
 *
 * AuditResult result = axonflow.auditLLMCall(options);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AuditOptions {

    @JsonProperty("context_id")
    private final String contextId;

    @JsonProperty("client_id")
    private final String clientId;

    @JsonProperty("response_summary")
    private final String responseSummary;

    @JsonProperty("provider")
    private final String provider;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("token_usage")
    private final TokenUsage tokenUsage;

    @JsonProperty("latency_ms")
    private final Long latencyMs;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("success")
    private final Boolean success;

    @JsonProperty("error_message")
    private final String errorMessage;

    private AuditOptions(Builder builder) {
        this.contextId = Objects.requireNonNull(builder.contextId, "contextId cannot be null");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId cannot be null");
        this.responseSummary = builder.responseSummary;
        this.provider = builder.provider;
        this.model = builder.model;
        this.tokenUsage = builder.tokenUsage;
        this.latencyMs = builder.latencyMs;
        this.metadata = builder.metadata != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
            : null;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getContextId() {
        return contextId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditOptions that = (AuditOptions) o;
        return Objects.equals(contextId, that.contextId) &&
               Objects.equals(clientId, that.clientId) &&
               Objects.equals(responseSummary, that.responseSummary) &&
               Objects.equals(provider, that.provider) &&
               Objects.equals(model, that.model) &&
               Objects.equals(tokenUsage, that.tokenUsage) &&
               Objects.equals(latencyMs, that.latencyMs) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(success, that.success) &&
               Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextId, clientId, responseSummary, provider, model, tokenUsage,
                           latencyMs, metadata, success, errorMessage);
    }

    @Override
    public String toString() {
        return "AuditOptions{" +
               "contextId='" + contextId + '\'' +
               ", clientId='" + clientId + '\'' +
               ", provider='" + provider + '\'' +
               ", model='" + model + '\'' +
               ", tokenUsage=" + tokenUsage +
               ", latencyMs=" + latencyMs +
               ", success=" + success +
               '}';
    }

    /**
     * Builder for AuditOptions.
     */
    public static final class Builder {
        private String contextId;
        private String clientId;
        private String responseSummary;
        private String provider;
        private String model;
        private TokenUsage tokenUsage;
        private Long latencyMs;
        private Map<String, Object> metadata;
        private Boolean success = true;
        private String errorMessage;

        private Builder() {}

        /**
         * Sets the context ID from the policy pre-check.
         *
         * @param contextId the context identifier from PolicyApprovalResult
         * @return this builder
         */
        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        /**
         * Sets the client identifier.
         *
         * @param clientId the client identifier
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets a summary of the LLM response.
         *
         * <p>This should be a brief description or the actual response text.
         * Sensitive information should be redacted.
         *
         * @param responseSummary the response summary
         * @return this builder
         */
        public Builder responseSummary(String responseSummary) {
            this.responseSummary = responseSummary;
            return this;
        }

        /**
         * Sets the LLM provider name.
         *
         * @param provider the provider (e.g., "openai", "anthropic", "bedrock")
         * @return this builder
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Sets the model used for the LLM call.
         *
         * @param model the model identifier (e.g., "gpt-4", "claude-3-opus")
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the token usage statistics.
         *
         * @param tokenUsage the token usage from the LLM response
         * @return this builder
         */
        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        /**
         * Sets the latency of the LLM call in milliseconds.
         *
         * @param latencyMs the latency in milliseconds
         * @return this builder
         */
        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * Sets additional metadata for the audit record.
         *
         * @param metadata key-value pairs of additional information
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Adds a single metadata entry.
         *
         * @param key   the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets whether the LLM call was successful.
         *
         * @param success true if successful, false if failed
         * @return this builder
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the error message if the LLM call failed.
         *
         * @param errorMessage the error message
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Builds the AuditOptions.
         *
         * @return a new AuditOptions instance
         * @throws NullPointerException if contextId is null
         */
        public AuditOptions build() {
            return new AuditOptions(this);
        }
    }
}
