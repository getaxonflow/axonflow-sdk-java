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
 * Response from the MCP input policy check endpoint.
 *
 * <p>Indicates whether the input statement is allowed by configured policies. A 403 HTTP response
 * still returns a valid response body with {@code allowed=false} and details in {@code blockReason}
 * and {@code policyInfo}.
 *
 * <p>The five Plugin Batch 1 / ADR-042 / ADR-043 fields ({@code decisionId}, {@code riskLevel},
 * {@code policyMatches}, {@code overrideAvailable}, {@code overrideExistingId}) are populated
 * when the AxonFlow platform is v7.1.0+. Pre-v7.1.0 platforms leave these as {@code null}.
 * Source of truth: {@code platform/agent/mcp_server_handler.go:880-940}.
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

  @JsonProperty("decision_id")
  private final String decisionId;

  @JsonProperty("risk_level")
  private final String riskLevel;

  @JsonProperty("policy_matches")
  private final List<ExplainPolicy> policyMatches;

  @JsonProperty("override_available")
  private final Boolean overrideAvailable;

  @JsonProperty("override_existing_id")
  private final String overrideExistingId;

  @JsonCreator
  public MCPCheckInputResponse(
      @JsonProperty("allowed") boolean allowed,
      @JsonProperty("block_reason") String blockReason,
      @JsonProperty("policies_evaluated") int policiesEvaluated,
      @JsonProperty("policy_info") ConnectorPolicyInfo policyInfo,
      @JsonProperty("decision_id") String decisionId,
      @JsonProperty("risk_level") String riskLevel,
      @JsonProperty("policy_matches") List<ExplainPolicy> policyMatches,
      @JsonProperty("override_available") Boolean overrideAvailable,
      @JsonProperty("override_existing_id") String overrideExistingId) {
    this.allowed = allowed;
    this.blockReason = blockReason;
    this.policiesEvaluated = policiesEvaluated;
    this.policyInfo = policyInfo;
    this.decisionId = decisionId;
    this.riskLevel = riskLevel;
    this.policyMatches = policyMatches;
    this.overrideAvailable = overrideAvailable;
    this.overrideExistingId = overrideExistingId;
  }

  /**
   * Source-compat overload. Callers that build {@code MCPCheckInputResponse} instances locally
   * with the v6.0.0 4-argument shape continue to compile — the five Plugin Batch 1 fields default
   * to {@code null}. Server-side responses always go through the {@code @JsonCreator} 9-arg
   * constructor regardless.
   */
  public MCPCheckInputResponse(
      boolean allowed, String blockReason, int policiesEvaluated, ConnectorPolicyInfo policyInfo) {
    this(allowed, blockReason, policiesEvaluated, policyInfo, null, null, null, null, null);
  }

  /** Returns whether the input is allowed by policies. */
  public boolean isAllowed() {
    return allowed;
  }

  /** Returns the reason the input was blocked, or null if allowed. */
  public String getBlockReason() {
    return blockReason;
  }

  /** Returns the number of policies evaluated. */
  public int getPoliciesEvaluated() {
    return policiesEvaluated;
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
   * Returns the highest risk level across matched policies ({@code low} | {@code medium} |
   * {@code high} | {@code critical}; Plugin Batch 1, v7.1.0+). Null on older platforms.
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * Returns the per-policy explainability records (ADR-043, v7.1.0+). Null on older platforms.
   */
  public List<ExplainPolicy> getPolicyMatches() {
    return policyMatches;
  }

  /**
   * Returns whether at least one matched policy permits a session override (Plugin Batch 1,
   * v7.1.0+). Null on older platforms; callers should treat null as "context not available"
   * rather than {@code false}.
   */
  public Boolean getOverrideAvailable() {
    return overrideAvailable;
  }

  /**
   * Returns the ID of an active override consumed by this decision, if any (Plugin Batch 1,
   * v7.1.0+). Null on older platforms or when no override was consumed.
   */
  public String getOverrideExistingId() {
    return overrideExistingId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckInputResponse that = (MCPCheckInputResponse) o;
    return allowed == that.allowed
        && policiesEvaluated == that.policiesEvaluated
        && Objects.equals(blockReason, that.blockReason)
        && Objects.equals(policyInfo, that.policyInfo)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(riskLevel, that.riskLevel)
        && Objects.equals(policyMatches, that.policyMatches)
        && Objects.equals(overrideAvailable, that.overrideAvailable)
        && Objects.equals(overrideExistingId, that.overrideExistingId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowed,
        blockReason,
        policiesEvaluated,
        policyInfo,
        decisionId,
        riskLevel,
        policyMatches,
        overrideAvailable,
        overrideExistingId);
  }

  @Override
  public String toString() {
    return "MCPCheckInputResponse{"
        + "allowed="
        + allowed
        + ", blockReason='"
        + blockReason
        + '\''
        + ", policiesEvaluated="
        + policiesEvaluated
        + ", policyInfo="
        + policyInfo
        + ", decisionId='"
        + decisionId
        + '\''
        + ", riskLevel='"
        + riskLevel
        + '\''
        + ", policyMatches="
        + policyMatches
        + ", overrideAvailable="
        + overrideAvailable
        + ", overrideExistingId='"
        + overrideExistingId
        + '\''
        + '}';
  }
}
