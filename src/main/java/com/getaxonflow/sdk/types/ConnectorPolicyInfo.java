/*
 * Copyright 2026 AxonFlow
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Policy evaluation information included in MCP responses.
 *
 * Provides transparency into policy enforcement decisions for
 * request blocking and response redaction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectorPolicyInfo {

    @JsonProperty("policies_evaluated")
    private final int policiesEvaluated;

    @JsonProperty("blocked")
    private final boolean blocked;

    @JsonProperty("block_reason")
    private final String blockReason;

    @JsonProperty("redactions_applied")
    private final int redactionsApplied;

    @JsonProperty("processing_time_ms")
    private final long processingTimeMs;

    @JsonProperty("matched_policies")
    private final List<PolicyMatchInfo> matchedPolicies;

    @JsonProperty("exfiltration_check")
    private final ExfiltrationCheckInfo exfiltrationCheck;

    @JsonProperty("dynamic_policy_info")
    private final DynamicPolicyInfo dynamicPolicyInfo;

    public ConnectorPolicyInfo(
            @JsonProperty("policies_evaluated") int policiesEvaluated,
            @JsonProperty("blocked") boolean blocked,
            @JsonProperty("block_reason") String blockReason,
            @JsonProperty("redactions_applied") int redactionsApplied,
            @JsonProperty("processing_time_ms") long processingTimeMs,
            @JsonProperty("matched_policies") List<PolicyMatchInfo> matchedPolicies,
            @JsonProperty("exfiltration_check") ExfiltrationCheckInfo exfiltrationCheck,
            @JsonProperty("dynamic_policy_info") DynamicPolicyInfo dynamicPolicyInfo) {
        this.policiesEvaluated = policiesEvaluated;
        this.blocked = blocked;
        this.blockReason = blockReason;
        this.redactionsApplied = redactionsApplied;
        this.processingTimeMs = processingTimeMs;
        this.matchedPolicies = matchedPolicies != null ? matchedPolicies : Collections.emptyList();
        this.exfiltrationCheck = exfiltrationCheck;
        this.dynamicPolicyInfo = dynamicPolicyInfo;
    }

    /**
     * Backward-compatible constructor without exfiltration and dynamic policy fields.
     */
    public ConnectorPolicyInfo(
            int policiesEvaluated,
            boolean blocked,
            String blockReason,
            int redactionsApplied,
            long processingTimeMs,
            List<PolicyMatchInfo> matchedPolicies) {
        this(policiesEvaluated, blocked, blockReason, redactionsApplied,
             processingTimeMs, matchedPolicies, null, null);
    }

    public int getPoliciesEvaluated() {
        return policiesEvaluated;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public int getRedactionsApplied() {
        return redactionsApplied;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public List<PolicyMatchInfo> getMatchedPolicies() {
        return matchedPolicies;
    }

    /**
     * Returns exfiltration check information (Issue #966).
     * May be null if exfiltration checking is disabled.
     */
    public ExfiltrationCheckInfo getExfiltrationCheck() {
        return exfiltrationCheck;
    }

    /**
     * Returns dynamic policy evaluation information (Issue #968).
     * May be null if dynamic policies are disabled.
     */
    public DynamicPolicyInfo getDynamicPolicyInfo() {
        return dynamicPolicyInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorPolicyInfo that = (ConnectorPolicyInfo) o;
        return policiesEvaluated == that.policiesEvaluated &&
               blocked == that.blocked &&
               redactionsApplied == that.redactionsApplied &&
               processingTimeMs == that.processingTimeMs &&
               Objects.equals(blockReason, that.blockReason) &&
               Objects.equals(matchedPolicies, that.matchedPolicies) &&
               Objects.equals(exfiltrationCheck, that.exfiltrationCheck) &&
               Objects.equals(dynamicPolicyInfo, that.dynamicPolicyInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policiesEvaluated, blocked, blockReason, redactionsApplied,
                processingTimeMs, matchedPolicies, exfiltrationCheck, dynamicPolicyInfo);
    }

    @Override
    public String toString() {
        return "ConnectorPolicyInfo{" +
               "policiesEvaluated=" + policiesEvaluated +
               ", blocked=" + blocked +
               ", blockReason='" + blockReason + '\'' +
               ", redactionsApplied=" + redactionsApplied +
               ", processingTimeMs=" + processingTimeMs +
               ", matchedPolicies=" + matchedPolicies +
               ", exfiltrationCheck=" + exfiltrationCheck +
               ", dynamicPolicyInfo=" + dynamicPolicyInfo +
               '}';
    }
}
