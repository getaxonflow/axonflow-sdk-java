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
package com.axonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request for policy pre-check in Gateway Mode.
 *
 * <p>This is the first step of the Gateway Mode pattern:
 * <ol>
 *   <li>Pre-check: Get policy approval using this request</li>
 *   <li>Direct LLM call: Make your own call to the LLM provider</li>
 *   <li>Audit: Log the LLM call for compliance tracking</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>{@code
 * PolicyApprovalRequest request = PolicyApprovalRequest.builder()
 *     .userToken("user-123")
 *     .query("What is the capital of France?")
 *     .dataSources(List.of("public-knowledge"))
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PolicyApprovalRequest {

    @JsonProperty("user_token")
    private final String userToken;

    @JsonProperty("query")
    private final String query;

    @JsonProperty("data_sources")
    private final List<String> dataSources;

    @JsonProperty("context")
    private final Map<String, Object> context;

    @JsonProperty("client_id")
    private final String clientId;

    private PolicyApprovalRequest(Builder builder) {
        this.userToken = Objects.requireNonNull(builder.userToken, "userToken cannot be null");
        this.query = Objects.requireNonNull(builder.query, "query cannot be null");
        this.dataSources = builder.dataSources != null
            ? Collections.unmodifiableList(builder.dataSources)
            : Collections.emptyList();
        this.context = builder.context != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.context))
            : Collections.emptyMap();
        this.clientId = builder.clientId;
    }

    public String getUserToken() {
        return userToken;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getDataSources() {
        return dataSources;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public String getClientId() {
        return clientId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyApprovalRequest that = (PolicyApprovalRequest) o;
        return Objects.equals(userToken, that.userToken) &&
               Objects.equals(query, that.query) &&
               Objects.equals(dataSources, that.dataSources) &&
               Objects.equals(context, that.context) &&
               Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userToken, query, dataSources, context, clientId);
    }

    @Override
    public String toString() {
        return "PolicyApprovalRequest{" +
               "userToken='" + userToken + '\'' +
               ", query='" + query + '\'' +
               ", dataSources=" + dataSources +
               ", clientId='" + clientId + '\'' +
               '}';
    }

    /**
     * Builder for PolicyApprovalRequest.
     */
    public static final class Builder {
        private String userToken;
        private String query;
        private List<String> dataSources;
        private Map<String, Object> context;
        private String clientId;

        private Builder() {}

        /**
         * Sets the user token identifying the requesting user.
         *
         * @param userToken the user identifier
         * @return this builder
         */
        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        /**
         * Sets the query or prompt to be evaluated.
         *
         * @param query the query text
         * @return this builder
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * Sets the data sources that will be accessed.
         *
         * @param dataSources list of data source identifiers
         * @return this builder
         */
        public Builder dataSources(List<String> dataSources) {
            this.dataSources = dataSources;
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
         * @param key   the context key
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
         * Builds the PolicyApprovalRequest.
         *
         * @return a new PolicyApprovalRequest instance
         * @throws NullPointerException if required fields are null
         */
        public PolicyApprovalRequest build() {
            return new PolicyApprovalRequest(this);
        }
    }
}
