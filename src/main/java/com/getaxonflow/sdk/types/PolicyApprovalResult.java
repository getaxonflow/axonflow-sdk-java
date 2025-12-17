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
 * Result of a policy pre-check in Gateway Mode.
 *
 * <p>This response indicates whether the request is approved to proceed to the LLM call.
 * If approved, the {@code contextId} must be used in the subsequent audit call.
 *
 * <p>Example usage:
 * <pre>{@code
 * PolicyApprovalResult result = axonflow.getPolicyApprovedContext(request);
 * if (result.isApproved()) {
 *     // Proceed with LLM call
 *     String contextId = result.getContextId();
 *     // Make LLM call...
 *     // Audit the call
 *     axonflow.auditLLMCall(AuditOptions.builder()
 *         .contextId(contextId)
 *         .build());
 * } else {
 *     // Handle rejection
 *     System.out.println("Blocked: " + result.getBlockReason());
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyApprovalResult {

    @JsonProperty("context_id")
    private final String contextId;

    @JsonProperty("approved")
    private final boolean approved;

    @JsonProperty("approved_data")
    private final Map<String, Object> approvedData;

    @JsonProperty("policies")
    private final List<String> policies;

    @JsonProperty("expires_at")
    private final Instant expiresAt;

    @JsonProperty("block_reason")
    private final String blockReason;

    @JsonProperty("rate_limit_info")
    private final RateLimitInfo rateLimitInfo;

    @JsonProperty("processing_time")
    private final String processingTime;

    public PolicyApprovalResult(
            @JsonProperty("context_id") String contextId,
            @JsonProperty("approved") boolean approved,
            @JsonProperty("approved_data") Map<String, Object> approvedData,
            @JsonProperty("policies") List<String> policies,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("block_reason") String blockReason,
            @JsonProperty("rate_limit_info") RateLimitInfo rateLimitInfo,
            @JsonProperty("processing_time") String processingTime) {
        this.contextId = contextId;
        this.approved = approved;
        this.approvedData = approvedData != null ? Collections.unmodifiableMap(approvedData) : Collections.emptyMap();
        this.policies = policies != null ? Collections.unmodifiableList(policies) : Collections.emptyList();
        this.expiresAt = expiresAt;
        this.blockReason = blockReason;
        this.rateLimitInfo = rateLimitInfo;
        this.processingTime = processingTime;
    }

    /**
     * Returns the context ID for correlating with the audit call.
     *
     * <p>This ID must be passed to {@code auditLLMCall()} after making the LLM call.
     *
     * @return the context identifier
     */
    public String getContextId() {
        return contextId;
    }

    /**
     * Returns whether the request is approved to proceed.
     *
     * @return true if approved, false if blocked by policy
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * Returns data that has been approved/filtered by policies.
     *
     * <p>This may contain redacted or filtered versions of sensitive data
     * that is safe to send to the LLM.
     *
     * @return immutable map of approved data
     */
    public Map<String, Object> getApprovedData() {
        return approvedData;
    }

    /**
     * Returns the list of policies that were evaluated.
     *
     * @return immutable list of policy names
     */
    public List<String> getPolicies() {
        return policies;
    }

    /**
     * Returns when this approval expires.
     *
     * <p>The audit call must be made before this time, typically within 5 minutes.
     *
     * @return the expiration timestamp
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this approval has expired.
     *
     * @return true if the approval has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns the reason the request was blocked, if not approved.
     *
     * @return the block reason, or null if approved
     */
    public String getBlockReason() {
        return blockReason;
    }

    /**
     * Extracts the policy name from the block reason.
     *
     * @return the extracted policy name, or the full block reason
     */
    public String getBlockingPolicyName() {
        if (blockReason == null || blockReason.isEmpty()) {
            return null;
        }
        String prefix = "Request blocked by policy: ";
        if (blockReason.startsWith(prefix)) {
            return blockReason.substring(prefix.length()).trim();
        }
        prefix = "Blocked by policy: ";
        if (blockReason.startsWith(prefix)) {
            return blockReason.substring(prefix.length()).trim();
        }
        if (blockReason.startsWith("[")) {
            int endBracket = blockReason.indexOf(']');
            if (endBracket > 1) {
                return blockReason.substring(1, endBracket).trim();
            }
        }
        return blockReason;
    }

    /**
     * Returns rate limit information, if available.
     *
     * @return the rate limit info, or null
     */
    public RateLimitInfo getRateLimitInfo() {
        return rateLimitInfo;
    }

    /**
     * Returns the processing time for the policy evaluation.
     *
     * @return the processing time string (e.g., "5.23ms")
     */
    public String getProcessingTime() {
        return processingTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyApprovalResult that = (PolicyApprovalResult) o;
        return approved == that.approved &&
               Objects.equals(contextId, that.contextId) &&
               Objects.equals(approvedData, that.approvedData) &&
               Objects.equals(policies, that.policies) &&
               Objects.equals(expiresAt, that.expiresAt) &&
               Objects.equals(blockReason, that.blockReason) &&
               Objects.equals(rateLimitInfo, that.rateLimitInfo) &&
               Objects.equals(processingTime, that.processingTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextId, approved, approvedData, policies, expiresAt,
                           blockReason, rateLimitInfo, processingTime);
    }

    @Override
    public String toString() {
        return "PolicyApprovalResult{" +
               "contextId='" + contextId + '\'' +
               ", approved=" + approved +
               ", policies=" + policies +
               ", expiresAt=" + expiresAt +
               ", blockReason='" + blockReason + '\'' +
               ", processingTime='" + processingTime + '\'' +
               '}';
    }
}
