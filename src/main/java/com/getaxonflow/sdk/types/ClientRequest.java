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
 * Represents a request to the AxonFlow Agent for policy evaluation.
 *
 * <p>This is the primary request type for Proxy Mode operations where AxonFlow
 * handles both policy enforcement and LLM routing.
 *
 * <p>Example usage:
 * <pre>{@code
 * ClientRequest request = ClientRequest.builder()
 *     .query("What is the weather today?")
 *     .userToken("user-123")
 *     .requestType(RequestType.CHAT)
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ClientRequest {

    @JsonProperty("query")
    private final String query;

    @JsonProperty("user_token")
    private final String userToken;

    @JsonProperty("client_id")
    private final String clientId;

    @JsonProperty("request_type")
    private final String requestType;

    @JsonProperty("context")
    private final Map<String, Object> context;

    @JsonProperty("llm_provider")
    private final String llmProvider;

    @JsonProperty("model")
    private final String model;

    private ClientRequest(Builder builder) {
        this.query = Objects.requireNonNull(builder.query, "query cannot be null");
        this.userToken = builder.userToken;
        this.clientId = builder.clientId;
        this.requestType = builder.requestType != null ? builder.requestType.getValue() : RequestType.CHAT.getValue();
        this.context = builder.context != null ? Collections.unmodifiableMap(new HashMap<>(builder.context)) : null;
        this.llmProvider = builder.llmProvider;
        this.model = builder.model;
    }

    public String getQuery() {
        return query;
    }

    public String getUserToken() {
        return userToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRequestType() {
        return requestType;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getModel() {
        return model;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientRequest that = (ClientRequest) o;
        return Objects.equals(query, that.query) &&
               Objects.equals(userToken, that.userToken) &&
               Objects.equals(clientId, that.clientId) &&
               Objects.equals(requestType, that.requestType) &&
               Objects.equals(context, that.context) &&
               Objects.equals(llmProvider, that.llmProvider) &&
               Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, userToken, clientId, requestType, context, llmProvider, model);
    }

    @Override
    public String toString() {
        return "ClientRequest{" +
               "query='" + query + '\'' +
               ", userToken='" + userToken + '\'' +
               ", clientId='" + clientId + '\'' +
               ", requestType='" + requestType + '\'' +
               ", llmProvider='" + llmProvider + '\'' +
               ", model='" + model + '\'' +
               '}';
    }

    /**
     * Builder for creating ClientRequest instances.
     */
    public static final class Builder {
        private String query;
        private String userToken;
        private String clientId;
        private RequestType requestType = RequestType.CHAT;
        private Map<String, Object> context;
        private String llmProvider;
        private String model;

        private Builder() {}

        /**
         * Sets the query text to be processed.
         *
         * @param query the query or prompt text
         * @return this builder
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * Sets the user token for identifying the requesting user.
         *
         * @param userToken the user identifier token
         * @return this builder
         */
        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        /**
         * Sets the client ID for multi-tenant scenarios.
         *
         * @param clientId the client identifier
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the type of request.
         *
         * @param requestType the request type
         * @return this builder
         */
        public Builder requestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        /**
         * Sets additional context for policy evaluation.
         *
         * @param context key-value pairs of contextual information
         * @return this builder
         */
        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        /**
         * Adds a single context entry.
         *
         * @param key the context key
         * @param value the context value
         * @return this builder
         */
        public Builder addContext(String key, Object value) {
            if (this.context == null) {
                this.context = new HashMap<>();
            }
            this.context.put(key, value);
            return this;
        }

        /**
         * Sets the LLM provider to use (for Proxy Mode).
         *
         * @param llmProvider the provider name (e.g., "openai", "anthropic")
         * @return this builder
         */
        public Builder llmProvider(String llmProvider) {
            this.llmProvider = llmProvider;
            return this;
        }

        /**
         * Sets the model to use (for Proxy Mode).
         *
         * @param model the model identifier (e.g., "gpt-4", "claude-3-opus")
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Builds the ClientRequest instance.
         *
         * @return a new ClientRequest
         * @throws NullPointerException if query is null
         */
        public ClientRequest build() {
            return new ClientRequest(this);
        }
    }
}
