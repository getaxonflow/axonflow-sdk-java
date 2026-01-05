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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single audit log entry representing an audited request or event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuditLogEntry {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("request_id")
    private final String requestId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("user_email")
    private final String userEmail;

    @JsonProperty("client_id")
    private final String clientId;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("request_type")
    private final String requestType;

    @JsonProperty("query_summary")
    private final String querySummary;

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("blocked")
    private final boolean blocked;

    @JsonProperty("risk_score")
    private final double riskScore;

    @JsonProperty("provider")
    private final String provider;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("tokens_used")
    private final int tokensUsed;

    @JsonProperty("latency_ms")
    private final int latencyMs;

    @JsonProperty("policy_violations")
    private final List<String> policyViolations;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public AuditLogEntry(
            @JsonProperty("id") String id,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("user_email") String userEmail,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("request_type") String requestType,
            @JsonProperty("query_summary") String querySummary,
            @JsonProperty("success") Boolean success,
            @JsonProperty("blocked") Boolean blocked,
            @JsonProperty("risk_score") Double riskScore,
            @JsonProperty("provider") String provider,
            @JsonProperty("model") String model,
            @JsonProperty("tokens_used") Integer tokensUsed,
            @JsonProperty("latency_ms") Integer latencyMs,
            @JsonProperty("policy_violations") List<String> policyViolations,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.id = id != null ? id : "";
        this.requestId = requestId != null ? requestId : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.userEmail = userEmail != null ? userEmail : "";
        this.clientId = clientId != null ? clientId : "";
        this.tenantId = tenantId != null ? tenantId : "";
        this.requestType = requestType != null ? requestType : "";
        this.querySummary = querySummary != null ? querySummary : "";
        this.success = success != null ? success : true;
        this.blocked = blocked != null ? blocked : false;
        this.riskScore = riskScore != null ? riskScore : 0.0;
        this.provider = provider != null ? provider : "";
        this.model = model != null ? model : "";
        this.tokensUsed = tokensUsed != null ? tokensUsed : 0;
        this.latencyMs = latencyMs != null ? latencyMs : 0;
        this.policyViolations = policyViolations != null ? policyViolations : Collections.emptyList();
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    /** Returns the unique audit log ID. */
    public String getId() {
        return id;
    }

    /** Returns the correlation ID for the original request. */
    public String getRequestId() {
        return requestId;
    }

    /** Returns when the event occurred. */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** Returns the email of the user who made the request. */
    public String getUserEmail() {
        return userEmail;
    }

    /** Returns the client/application that made the request. */
    public String getClientId() {
        return clientId;
    }

    /** Returns the tenant identifier. */
    public String getTenantId() {
        return tenantId;
    }

    /** Returns the type of request (e.g., "llm_chat", "sql", "mcp-query"). */
    public String getRequestType() {
        return requestType;
    }

    /** Returns a summary of the query/request. */
    public String getQuerySummary() {
        return querySummary;
    }

    /** Returns whether the request succeeded. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns whether the request was blocked by policy. */
    public boolean isBlocked() {
        return blocked;
    }

    /** Returns the calculated risk score (0.0-1.0). */
    public double getRiskScore() {
        return riskScore;
    }

    /** Returns the LLM provider used (if applicable). */
    public String getProvider() {
        return provider;
    }

    /** Returns the model used (if applicable). */
    public String getModel() {
        return model;
    }

    /** Returns the total tokens consumed. */
    public int getTokensUsed() {
        return tokensUsed;
    }

    /** Returns the request latency in milliseconds. */
    public int getLatencyMs() {
        return latencyMs;
    }

    /** Returns the list of violated policy IDs (if any). */
    public List<String> getPolicyViolations() {
        return policyViolations;
    }

    /** Returns additional metadata. */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLogEntry that = (AuditLogEntry) o;
        return success == that.success &&
               blocked == that.blocked &&
               Double.compare(that.riskScore, riskScore) == 0 &&
               tokensUsed == that.tokensUsed &&
               latencyMs == that.latencyMs &&
               Objects.equals(id, that.id) &&
               Objects.equals(requestId, that.requestId) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(userEmail, that.userEmail) &&
               Objects.equals(clientId, that.clientId) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(requestType, that.requestType) &&
               Objects.equals(querySummary, that.querySummary) &&
               Objects.equals(provider, that.provider) &&
               Objects.equals(model, that.model) &&
               Objects.equals(policyViolations, that.policyViolations) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, requestId, timestamp, userEmail, clientId, tenantId, requestType,
                querySummary, success, blocked, riskScore, provider, model, tokensUsed, latencyMs,
                policyViolations, metadata);
    }

    @Override
    public String toString() {
        return "AuditLogEntry{" +
               "id='" + id + '\'' +
               ", requestId='" + requestId + '\'' +
               ", timestamp=" + timestamp +
               ", userEmail='" + userEmail + '\'' +
               ", requestType='" + requestType + '\'' +
               ", success=" + success +
               ", blocked=" + blocked +
               ", riskScore=" + riskScore +
               '}';
    }
}
