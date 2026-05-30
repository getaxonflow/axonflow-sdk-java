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
import java.util.Map;

/**
 * Slim 5-field row returned by {@code AxonFlow.listDecisions}.
 *
 * <p>Companion to {@link DecisionExplanation}; matches the platform
 * GET /api/v1/decisions wire shape. {@code policyId} and
 * {@code toolSignature} are optional because pre-α1 audit rows + dynamic-only
 * blocks may not populate them; additive new fields land via
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} per ADR-043
 * §"Versioning" (non-breaking).
 *
 * <p>Cross-SDK parity:
 * <ul>
 *   <li>Go: axonflow-sdk-go/decisions.go (DecisionSummary)
 *   <li>Python: axonflow-sdk-python/axonflow/decisions.py (DecisionSummary)
 *   <li>TS: axonflow-sdk-typescript/src/types/decisions.ts (DecisionSummary)
 *   <li>Rust: axonflow-sdk-rust/src/types/decisions.rs (DecisionSummary)
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecisionSummary {

  private final String decisionId;
  private final Instant timestamp;
  private final String decision;
  private final String policyId;
  private final String toolSignature;
  private final Map<String, String> context;

  @JsonCreator
  public DecisionSummary(
      @JsonProperty("decision_id") String decisionId,
      @JsonProperty("timestamp") Instant timestamp,
      @JsonProperty("decision") String decision,
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("tool_signature") String toolSignature,
      @JsonProperty("context") Map<String, String> context) {
    this.decisionId = decisionId;
    this.timestamp = timestamp;
    this.decision = decision;
    this.policyId = policyId;
    this.toolSignature = toolSignature;
    this.context = context;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  /** allow | deny | require_approval */
  public String getDecision() {
    return decision;
  }

  /** May be null for dynamic-only blocks or pre-α1 audit rows. */
  public String getPolicyId() {
    return policyId;
  }

  /** May be null when the decision had no tool context. */
  public String getToolSignature() {
    return toolSignature;
  }

  /**
   * The sanitized request context the PEP attached to the decision (canonical
   * {@code lower_snake_case} keys, string values), surfaced from the audit
   * row's {@code policy_details->'context'}. The list summary is truncated by
   * the platform to the 5 most-correlated keys; the full map is available via
   * {@code AxonFlow.explainDecision}. May be {@code null} for pre-v8.4.0 audit
   * rows or decisions with no context. (platform #2509 / epic #2508)
   */
  public Map<String, String> getContext() {
    return context;
  }
}
