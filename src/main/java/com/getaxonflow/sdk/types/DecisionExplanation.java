/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Canonical payload returned by {@code AxonFlow.explainDecision}.
 *
 * <p>Shape frozen per ADR-043 (Explainability Data Contract). Additive-only changes are
 * non-breaking; renames or removals require a major version bump.
 *
 * <p>Unknown fields from future platform versions are ignored to preserve forward compatibility —
 * see the {@code @JsonIgnoreProperties(ignoreUnknown = true)} annotation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecisionExplanation {

  private final String decisionId;
  private final Instant timestamp;
  private final List<ExplainPolicy> policyMatches;
  private final List<ExplainRule> matchedRules;
  private final String decision;
  private final String reason;
  private final String riskLevel;
  private final boolean overrideAvailable;
  private final String overrideExistingId;
  private final int historicalHitCountSession;
  private final String policySourceLink;
  private final String toolSignature;
  private final Map<String, String> context;
  private final boolean contextTruncated;

  @JsonCreator
  public DecisionExplanation(
      @JsonProperty("decision_id") String decisionId,
      @JsonProperty("timestamp") Instant timestamp,
      @JsonProperty("policy_matches") List<ExplainPolicy> policyMatches,
      @JsonProperty("matched_rules") List<ExplainRule> matchedRules,
      @JsonProperty("decision") String decision,
      @JsonProperty("reason") String reason,
      @JsonProperty("risk_level") String riskLevel,
      @JsonProperty("override_available") boolean overrideAvailable,
      @JsonProperty("override_existing_id") String overrideExistingId,
      @JsonProperty("historical_hit_count_session") int historicalHitCountSession,
      @JsonProperty("policy_source_link") String policySourceLink,
      @JsonProperty("tool_signature") String toolSignature,
      @JsonProperty("context") Map<String, String> context,
      @JsonProperty("context_truncated") boolean contextTruncated) {
    this.decisionId = decisionId;
    this.timestamp = timestamp;
    this.policyMatches = policyMatches != null ? policyMatches : Collections.emptyList();
    this.matchedRules = matchedRules;
    this.decision = decision;
    this.reason = reason;
    this.riskLevel = riskLevel;
    this.overrideAvailable = overrideAvailable;
    this.overrideExistingId = overrideExistingId;
    this.historicalHitCountSession = historicalHitCountSession;
    this.policySourceLink = policySourceLink;
    this.toolSignature = toolSignature;
    this.context = context;
    this.contextTruncated = contextTruncated;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public List<ExplainPolicy> getPolicyMatches() {
    return policyMatches;
  }

  public List<ExplainRule> getMatchedRules() {
    return matchedRules;
  }

  /** Canonical audit verdict: allowed | blocked | redacted | needs_approval | error (9.0.0+). */
  public String getDecision() {
    return decision;
  }

  public String getReason() {
    return reason;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public boolean isOverrideAvailable() {
    return overrideAvailable;
  }

  public String getOverrideExistingId() {
    return overrideExistingId;
  }

  public int getHistoricalHitCountSession() {
    return historicalHitCountSession;
  }

  public String getPolicySourceLink() {
    return policySourceLink;
  }

  public String getToolSignature() {
    return toolSignature;
  }

  /**
   * The FULL sanitized request context the PEP attached to the decision (canonical {@code
   * lower_snake_case} keys, string values), read from the audit row's {@code
   * policy_details->'context'}. Unlike {@link DecisionSummary} (truncated to 5 keys), explain
   * returns every persisted key up to the platform's 10-key cap. May be {@code null} for pre-v8.4.0
   * audit rows. (platform #2509 / epic #2508)
   */
  public Map<String, String> getContext() {
    return context;
  }

  /** True when the agent dropped surplus context keys at write time. */
  public boolean isContextTruncated() {
    return contextTruncated;
  }
}
