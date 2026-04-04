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
 * Response from the MCP output policy check endpoint.
 *
 * <p>Indicates whether the output data passes configured policies. May include redacted data if PII
 * redaction policies are active, and exfiltration check information if data volume limits are
 * configured.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MCPCheckOutputResponse {

  @JsonProperty("allowed")
  private final boolean allowed;

  @JsonProperty("block_reason")
  private final String blockReason;

  @JsonProperty("redacted_data")
  private final Object redactedData;

  @JsonProperty("policies_evaluated")
  private final int policiesEvaluated;

  @JsonProperty("exfiltration_info")
  private final ExfiltrationCheckInfo exfiltrationInfo;

  @JsonProperty("policy_info")
  private final ConnectorPolicyInfo policyInfo;

  @JsonCreator
  public MCPCheckOutputResponse(
      @JsonProperty("allowed") boolean allowed,
      @JsonProperty("block_reason") String blockReason,
      @JsonProperty("redacted_data") Object redactedData,
      @JsonProperty("policies_evaluated") int policiesEvaluated,
      @JsonProperty("exfiltration_info") ExfiltrationCheckInfo exfiltrationInfo,
      @JsonProperty("policy_info") ConnectorPolicyInfo policyInfo) {
    this.allowed = allowed;
    this.blockReason = blockReason;
    this.redactedData = redactedData;
    this.policiesEvaluated = policiesEvaluated;
    this.exfiltrationInfo = exfiltrationInfo;
    this.policyInfo = policyInfo;
  }

  /** Returns whether the output data is allowed by policies. */
  public boolean isAllowed() {
    return allowed;
  }

  /** Returns the reason the output was blocked, or null if allowed. */
  public String getBlockReason() {
    return blockReason;
  }

  /** Returns the redacted version of the data, or null if no redaction was applied. */
  public Object getRedactedData() {
    return redactedData;
  }

  /** Returns the number of policies evaluated. */
  public int getPoliciesEvaluated() {
    return policiesEvaluated;
  }

  /** Returns exfiltration check information. May be null if exfiltration checking is disabled. */
  public ExfiltrationCheckInfo getExfiltrationInfo() {
    return exfiltrationInfo;
  }

  /** Returns detailed policy evaluation information. */
  public ConnectorPolicyInfo getPolicyInfo() {
    return policyInfo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckOutputResponse that = (MCPCheckOutputResponse) o;
    return allowed == that.allowed
        && policiesEvaluated == that.policiesEvaluated
        && Objects.equals(blockReason, that.blockReason)
        && Objects.equals(redactedData, that.redactedData)
        && Objects.equals(exfiltrationInfo, that.exfiltrationInfo)
        && Objects.equals(policyInfo, that.policyInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowed, blockReason, redactedData, policiesEvaluated, exfiltrationInfo, policyInfo);
  }

  @Override
  public String toString() {
    return "MCPCheckOutputResponse{"
        + "allowed="
        + allowed
        + ", blockReason='"
        + blockReason
        + '\''
        + ", policiesEvaluated="
        + policiesEvaluated
        + ", exfiltrationInfo="
        + exfiltrationInfo
        + ", policyInfo="
        + policyInfo
        + '}';
  }
}
