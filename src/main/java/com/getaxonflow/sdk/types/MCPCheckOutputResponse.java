// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
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
 * <p>The three Plugin Batch 1 / ADR-043 fields ({@code decisionId}, {@code policyMatches}, {@code
 * redactedMessage}) are populated when the AxonFlow platform is v7.1.0+. Pre-v7.1.0 platforms leave
 * these as {@code null}. Source of truth: {@code platform/agent/mcp_server_handler.go:988, 1005,
 * 1051}.
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

  /**
   * Reports whether the response-phase redaction detector actually RAN (ADR-056 / #2563). A PEP
   * fulfilling a response-phase {@code redact_pii} obligation MUST fail closed when this is {@code
   * false} — the redactor did not run, so absence of redacted output cannot be trusted as "nothing
   * to mask". Default {@code false} keeps a PEP fail-closed when the platform predates the field.
   * Source of truth: {@code platform/agent/mcp_handler.go}.
   */
  @JsonProperty("redaction_evaluated")
  private final boolean redactionEvaluated;

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
      @JsonProperty("policy_matches") List<ExplainPolicy> policyMatches,
      @JsonProperty("redaction_evaluated") boolean redactionEvaluated) {
    this.allowed = allowed;
    this.blockReason = blockReason;
    this.redactedData = redactedData;
    this.redactedMessage = redactedMessage;
    this.policiesEvaluated = policiesEvaluated;
    this.exfiltrationInfo = exfiltrationInfo;
    this.policyInfo = policyInfo;
    this.decisionId = decisionId;
    this.policyMatches = policyMatches;
    this.redactionEvaluated = redactionEvaluated;
  }

  /**
   * Source-compat overload. Callers that build {@code MCPCheckOutputResponse} instances locally
   * with the v6.0.0 6-argument shape continue to compile — {@code redactedMessage}, {@code
   * decisionId}, {@code policyMatches}, and {@code redactionEvaluated} default to {@code null} /
   * {@code false}. Server-side responses always go through the {@code @JsonCreator} constructor
   * regardless.
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
        null,
        false);
  }

  /**
   * Source-compat overload preserving the v7.1.0 9-argument shape — {@code redactionEvaluated}
   * defaults to {@code false}.
   */
  public MCPCheckOutputResponse(
      boolean allowed,
      String blockReason,
      Object redactedData,
      String redactedMessage,
      int policiesEvaluated,
      ExfiltrationCheckInfo exfiltrationInfo,
      ConnectorPolicyInfo policyInfo,
      String decisionId,
      List<ExplainPolicy> policyMatches) {
    this(
        allowed,
        blockReason,
        redactedData,
        redactedMessage,
        policiesEvaluated,
        exfiltrationInfo,
        policyInfo,
        decisionId,
        policyMatches,
        false);
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
   * Returns the audit correlator for this policy decision (Plugin Batch 1, v7.1.0+). Null on older
   * platforms.
   */
  public String getDecisionId() {
    return decisionId;
  }

  /** Returns the per-policy explainability records (ADR-043, v7.1.0+). Null on older platforms. */
  public List<ExplainPolicy> getPolicyMatches() {
    return policyMatches;
  }

  /**
   * Returns whether the response-phase redaction detector actually ran (ADR-056 / #2563). A PEP
   * MUST fail closed when this is false.
   */
  public boolean isRedactionEvaluated() {
    return redactionEvaluated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckOutputResponse that = (MCPCheckOutputResponse) o;
    return allowed == that.allowed
        && policiesEvaluated == that.policiesEvaluated
        && redactionEvaluated == that.redactionEvaluated
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
        policyMatches,
        redactionEvaluated);
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
        + ", redactionEvaluated="
        + redactionEvaluated
        + '}';
  }
}
