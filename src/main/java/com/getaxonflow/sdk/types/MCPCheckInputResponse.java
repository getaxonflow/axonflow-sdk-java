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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Response from the MCP input policy check endpoint.
 *
 * <p>Indicates whether the input statement is allowed by configured policies.
 * A 403 HTTP response still returns a valid response body with {@code allowed=false}
 * and details in {@code blockReason} and {@code policyInfo}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MCPCheckInputResponse {

    @JsonProperty("allowed")
    private final boolean allowed;

    @JsonProperty("block_reason")
    private final String blockReason;

    @JsonProperty("policies_evaluated")
    private final int policiesEvaluated;

    @JsonProperty("policy_info")
    private final ConnectorPolicyInfo policyInfo;

    @JsonCreator
    public MCPCheckInputResponse(
            @JsonProperty("allowed") boolean allowed,
            @JsonProperty("block_reason") String blockReason,
            @JsonProperty("policies_evaluated") int policiesEvaluated,
            @JsonProperty("policy_info") ConnectorPolicyInfo policyInfo) {
        this.allowed = allowed;
        this.blockReason = blockReason;
        this.policiesEvaluated = policiesEvaluated;
        this.policyInfo = policyInfo;
    }

    /**
     * Returns whether the input is allowed by policies.
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Returns the reason the input was blocked, or null if allowed.
     */
    public String getBlockReason() {
        return blockReason;
    }

    /**
     * Returns the number of policies evaluated.
     */
    public int getPoliciesEvaluated() {
        return policiesEvaluated;
    }

    /**
     * Returns detailed policy evaluation information.
     */
    public ConnectorPolicyInfo getPolicyInfo() {
        return policyInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPCheckInputResponse that = (MCPCheckInputResponse) o;
        return allowed == that.allowed &&
               policiesEvaluated == that.policiesEvaluated &&
               Objects.equals(blockReason, that.blockReason) &&
               Objects.equals(policyInfo, that.policyInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, blockReason, policiesEvaluated, policyInfo);
    }

    @Override
    public String toString() {
        return "MCPCheckInputResponse{" +
               "allowed=" + allowed +
               ", blockReason='" + blockReason + '\'' +
               ", policiesEvaluated=" + policiesEvaluated +
               ", policyInfo=" + policyInfo +
               '}';
    }
}
