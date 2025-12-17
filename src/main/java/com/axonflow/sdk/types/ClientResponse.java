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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a response from the AxonFlow Agent.
 *
 * <p>This is the primary response type for Proxy Mode operations. It contains:
 * <ul>
 *   <li>Success indicator and data payload</li>
 *   <li>Blocking status and reason (if policy violation occurred)</li>
 *   <li>Policy information including evaluated policies and processing time</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ClientResponse {

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("data")
    private final Object data;

    @JsonProperty("result")
    private final String result;

    @JsonProperty("plan_id")
    private final String planId;

    @JsonProperty("blocked")
    private final boolean blocked;

    @JsonProperty("block_reason")
    private final String blockReason;

    @JsonProperty("policy_info")
    private final PolicyInfo policyInfo;

    @JsonProperty("error")
    private final String error;

    public ClientResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") Object data,
            @JsonProperty("result") String result,
            @JsonProperty("plan_id") String planId,
            @JsonProperty("blocked") boolean blocked,
            @JsonProperty("block_reason") String blockReason,
            @JsonProperty("policy_info") PolicyInfo policyInfo,
            @JsonProperty("error") String error) {
        this.success = success;
        this.data = data;
        this.result = result;
        this.planId = planId;
        this.blocked = blocked;
        this.blockReason = blockReason;
        this.policyInfo = policyInfo;
        this.error = error;
    }

    /**
     * Returns whether the request was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the data payload from the response.
     *
     * @return the response data, may be null
     */
    public Object getData() {
        return data;
    }

    /**
     * Returns the result string (used for planning responses).
     *
     * @return the result text, may be null
     */
    public String getResult() {
        return result;
    }

    /**
     * Returns the plan ID (for planning operations).
     *
     * @return the plan identifier, may be null
     */
    public String getPlanId() {
        return planId;
    }

    /**
     * Returns whether the request was blocked by policy.
     *
     * @return true if blocked, false otherwise
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Returns the reason the request was blocked.
     *
     * @return the block reason, may be null if not blocked
     */
    public String getBlockReason() {
        return blockReason;
    }

    /**
     * Extracts the policy name from the block reason.
     *
     * <p>Block reasons typically follow the format: "Request blocked by policy: policy_name"
     *
     * @return the extracted policy name, or the full block reason if extraction fails
     */
    public String getBlockingPolicyName() {
        if (blockReason == null || blockReason.isEmpty()) {
            return null;
        }
        // Handle format: "Request blocked by policy: policy_name"
        String prefix = "Request blocked by policy: ";
        if (blockReason.startsWith(prefix)) {
            return blockReason.substring(prefix.length()).trim();
        }
        // Handle format: "Blocked by policy: policy_name"
        prefix = "Blocked by policy: ";
        if (blockReason.startsWith(prefix)) {
            return blockReason.substring(prefix.length()).trim();
        }
        // Handle format with brackets: "[policy_name] description"
        if (blockReason.startsWith("[")) {
            int endBracket = blockReason.indexOf(']');
            if (endBracket > 1) {
                return blockReason.substring(1, endBracket).trim();
            }
        }
        return blockReason;
    }

    /**
     * Returns information about the policies evaluated.
     *
     * @return the policy info, may be null
     */
    public PolicyInfo getPolicyInfo() {
        return policyInfo;
    }

    /**
     * Returns the error message if the request failed.
     *
     * @return the error message, may be null
     */
    public String getError() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientResponse that = (ClientResponse) o;
        return success == that.success &&
               blocked == that.blocked &&
               Objects.equals(data, that.data) &&
               Objects.equals(result, that.result) &&
               Objects.equals(planId, that.planId) &&
               Objects.equals(blockReason, that.blockReason) &&
               Objects.equals(policyInfo, that.policyInfo) &&
               Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, data, result, planId, blocked, blockReason, policyInfo, error);
    }

    @Override
    public String toString() {
        return "ClientResponse{" +
               "success=" + success +
               ", blocked=" + blocked +
               ", blockReason='" + blockReason + '\'' +
               ", policyInfo=" + policyInfo +
               ", error='" + error + '\'' +
               '}';
    }
}
