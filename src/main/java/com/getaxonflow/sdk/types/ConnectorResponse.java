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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Response from an MCP connector query.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectorResponse {

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("data")
    private final Object data;

    @JsonProperty("error")
    private final String error;

    @JsonProperty("connector_id")
    private final String connectorId;

    @JsonProperty("operation")
    private final String operation;

    @JsonProperty("processing_time")
    private final String processingTime;

    public ConnectorResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") Object data,
            @JsonProperty("error") String error,
            @JsonProperty("connector_id") String connectorId,
            @JsonProperty("operation") String operation,
            @JsonProperty("processing_time") String processingTime) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.connectorId = connectorId;
        this.operation = operation;
        this.processingTime = processingTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public String getConnectorId() {
        return connectorId;
    }

    public String getOperation() {
        return operation;
    }

    public String getProcessingTime() {
        return processingTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorResponse that = (ConnectorResponse) o;
        return success == that.success &&
               Objects.equals(data, that.data) &&
               Objects.equals(error, that.error) &&
               Objects.equals(connectorId, that.connectorId) &&
               Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, data, error, connectorId, operation);
    }

    @Override
    public String toString() {
        return "ConnectorResponse{" +
               "success=" + success +
               ", connectorId='" + connectorId + '\'' +
               ", operation='" + operation + '\'' +
               ", error='" + error + '\'' +
               '}';
    }
}
