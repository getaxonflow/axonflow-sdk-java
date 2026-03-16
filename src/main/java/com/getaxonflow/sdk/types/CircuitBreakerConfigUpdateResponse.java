package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from updating circuit breaker configuration.
 *
 * <p>The backend returns a confirmation with tenant_id and message,
 * not the full config object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CircuitBreakerConfigUpdateResponse {

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("message")
    private final String message;

    public CircuitBreakerConfigUpdateResponse(
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("message") String message) {
        this.tenantId = tenantId;
        this.message = message;
    }

    public String getTenantId() { return tenantId; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "CircuitBreakerConfigUpdateResponse{tenantId='" + tenantId + "', message='" + message + "'}";
    }
}
