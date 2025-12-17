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
import java.util.Map;
import java.util.Objects;

/**
 * Request for querying an MCP connector.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConnectorQuery {

    @JsonProperty("connector_id")
    private final String connectorId;

    @JsonProperty("operation")
    private final String operation;

    @JsonProperty("parameters")
    private final Map<String, Object> parameters;

    @JsonProperty("user_token")
    private final String userToken;

    @JsonProperty("timeout_ms")
    private final Integer timeoutMs;

    private ConnectorQuery(Builder builder) {
        this.connectorId = Objects.requireNonNull(builder.connectorId, "connectorId cannot be null");
        this.operation = Objects.requireNonNull(builder.operation, "operation cannot be null");
        this.parameters = builder.parameters != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.parameters))
            : Collections.emptyMap();
        this.userToken = builder.userToken;
        this.timeoutMs = builder.timeoutMs;
    }

    public String getConnectorId() {
        return connectorId;
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getUserToken() {
        return userToken;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorQuery that = (ConnectorQuery) o;
        return Objects.equals(connectorId, that.connectorId) &&
               Objects.equals(operation, that.operation) &&
               Objects.equals(parameters, that.parameters) &&
               Objects.equals(userToken, that.userToken) &&
               Objects.equals(timeoutMs, that.timeoutMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectorId, operation, parameters, userToken, timeoutMs);
    }

    @Override
    public String toString() {
        return "ConnectorQuery{" +
               "connectorId='" + connectorId + '\'' +
               ", operation='" + operation + '\'' +
               ", userToken='" + userToken + '\'' +
               ", timeoutMs=" + timeoutMs +
               '}';
    }

    public static final class Builder {
        private String connectorId;
        private String operation;
        private Map<String, Object> parameters;
        private String userToken;
        private Integer timeoutMs;

        private Builder() {}

        public Builder connectorId(String connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder addParameter(String key, Object value) {
            if (this.parameters == null) {
                this.parameters = new HashMap<>();
            }
            this.parameters.put(key, value);
            return this;
        }

        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public ConnectorQuery build() {
            return new ConnectorQuery(this);
        }
    }
}
