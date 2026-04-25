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
import java.util.List;
import java.util.Objects;

/**
 * Response from the MCP output policy check endpoint.
 *
 * <p>Indicates whether the output data passes configured policies. May include redacted data
 * (tabular) or a redacted message (text) if PII redaction policies are active, and exfiltration
 * check information if data volume limits are configured.
 *
 * <p>The three Plugin Batch 1 / ADR-043 fields ({@code decisionId}, {@code policyMatches},
 * {@code redactedMessage}) are populated when the AxonFlow platform is v7.1.0+. Pre-v7.1.0
 * platforms leave these as {@code null}. Source of truth: {@code
 * platform/agent/mcp_server_handler.go:988, 1005, 1051}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MCPCheckOutputResponse {

  @JsonProperty("allowed")
  private final boolean allowed;

  @JsonProperty("block_reason")
  private final String blockReason;

  @JsonProperty("redacted_data")
  private final Object redactedData;

  @JsonProperty("redacted_message")
  private final String redactedMessage;

  @JsonProperty("policies_evaluated")
  private final int policiesEvaluated;

  @JsonProperty("exfiltration_info")
  private final ExfiltrationCheckInfo exfiltrationInfo;

  @JsonProperty("policy_info")
  private final ConnectorPolicyInfo policyInfo;

  @JsonProperty("decision_id")
  private final String decisionId;

  @JsonProperty("policy_matches")
  private final List<ExplainPolicy> policyMatches;

  @JsonCreator
  public MCPCheckOutputResponse(
      @JsonProperty("allowed") boolean allowed,
      @JsonProperty("block_reason") String blockReason,
      @JsonProperty("redacted_data") Object redactedData,
      @JsonProperty("redacted_message") String redactedMessage,
      @JsonProperty("policies_evaluated") int policiesEvaluated,
      @JsonProperty("exfiltration_info") ExfiltrationCheckInfo exfiltrationInfo,
      @JsonProperty("policy_info") ConnectorPolicyInfo policyInfo,
      @JsonProperty("decision_id") String decisionId,
      @JsonProperty("policy_matches") List<ExplainPolicy> policyMatches) {
    this.allowed = allowed;
    this.blockReason = blockReason;
    this.redactedData = redactedData;
    this.redactedMessage = redactedMessage;
    this.policiesEvaluated = policiesEvaluated;
    this.exfiltrationInfo = exfiltrationInfo;
    this.policyInfo = policyInfo;
    this.decisionId = decisionId;
    this.policyMatches = policyMatches;
  }

  /**
   * Source-compat overload. Callers that build {@code MCPCheckOutputResponse} instances locally
   * with the v6.0.0 6-argument shape continue to compile — {@code redactedMessage}, {@code
   * decisionId}, and {@code policyMatches} default to {@code null}. Server-side responses always
   * go through the {@code @JsonCreator} 9-arg constructor regardless.
   */
  public MCPCheckOutputResponse(
      boolean allowed,
      String blockReason,
      Object redactedData,
      int policiesEvaluated,
      ExfiltrationCheckInfo exfiltrationInfo,
      ConnectorPolicyInfo policyInfo) {
    this(
        allowed,
        blockReason,
        redactedData,
        null,
        policiesEvaluated,
        exfiltrationInfo,
        policyInfo,
        null,
        null);
  }

  /** Returns whether the output data is allowed by policies. */
  public boolean isAllowed() {
    return allowed;
  }

  /** Returns the reason the output was blocked, or null if allowed. */
  public String getBlockReason() {
    return blockReason;
  }

  /**
   * Returns the redacted tabular data with PII fields masked (used when the connector returned
   * rows; e.g. SQL/CSV results). Null if no redaction was applied or if the response was a text
   * message.
   */
  public Object getRedactedData() {
    return redactedData;
  }

  /**
   * Returns the redacted text message with PII fields masked (used when the connector returned a
   * string message rather than tabular rows; e.g. execute-style responses). Null if no redaction
   * was applied or if the response was tabular.
   */
  public String getRedactedMessage() {
    return redactedMessage;
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

  /**
   * Returns the audit correlator for this policy decision (Plugin Batch 1, v7.1.0+). Null on
   * older platforms.
   */
  public String getDecisionId() {
    return decisionId;
  }

  /**
   * Returns the per-policy explainability records (ADR-043, v7.1.0+). Null on older platforms.
   */
  public List<ExplainPolicy> getPolicyMatches() {
    return policyMatches;
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
        && Objects.equals(redactedMessage, that.redactedMessage)
        && Objects.equals(exfiltrationInfo, that.exfiltrationInfo)
        && Objects.equals(policyInfo, that.policyInfo)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(policyMatches, that.policyMatches);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowed,
        blockReason,
        redactedData,
        redactedMessage,
        policiesEvaluated,
        exfiltrationInfo,
        policyInfo,
        decisionId,
        policyMatches);
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
        + ", decisionId='"
        + decisionId
        + '\''
        + ", policyMatches="
        + policyMatches
        + '}';
  }
}
