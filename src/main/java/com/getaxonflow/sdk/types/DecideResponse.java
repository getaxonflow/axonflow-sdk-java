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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PDP verdict returned by {@code POST /api/v1/decide} (ADR-056, epic #2563). Mirrors the platform
 * {@code DecideResponse}.
 *
 * <p>{@code obligations} is always a (possibly empty) list so PEP code can iterate without a
 * null-check. {@code traceId} is W3C-format (32 lowercase hex chars). {@code error} is set on the
 * deny path when the request was malformed (still HTTP 200).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecideResponse {

  @JsonProperty("verdict")
  private final String verdict;

  @JsonProperty("decision_id")
  private final String decisionId;

  @JsonProperty("trace_id")
  private final String traceId;

  @JsonProperty("reasons")
  private final List<String> reasons;

  @JsonProperty("obligations")
  private final List<Obligation> obligations;

  @JsonProperty("evaluated_policies")
  private final List<String> evaluatedPolicies;

  @JsonProperty("stage")
  private final String stage;

  @JsonProperty("expires_at")
  private final Instant expiresAt;

  @JsonProperty("error")
  private final String error;

  /**
   * Creates a decide response.
   *
   * @param verdict the verdict: {@code allow}, {@code deny}, or {@code needs_approval}
   * @param decisionId the audit correlator for this decision
   * @param traceId the W3C trace id (32 lowercase hex chars)
   * @param reasons human-readable reasons, or null
   * @param obligations engine-fulfillable obligations; null is normalized to an empty list
   * @param evaluatedPolicies the policies evaluated; null is normalized to an empty list
   * @param stage the echoed decision stage, or null
   * @param expiresAt the verdict expiry, or null
   * @param error the error message on the malformed-deny path, or null
   */
  @JsonCreator
  public DecideResponse(
      @JsonProperty("verdict") String verdict,
      @JsonProperty("decision_id") String decisionId,
      @JsonProperty("trace_id") String traceId,
      @JsonProperty("reasons") List<String> reasons,
      @JsonProperty("obligations") List<Obligation> obligations,
      @JsonProperty("evaluated_policies") List<String> evaluatedPolicies,
      @JsonProperty("stage") String stage,
      @JsonProperty("expires_at") Instant expiresAt,
      @JsonProperty("error") String error) {
    this.verdict = verdict;
    this.decisionId = decisionId;
    this.traceId = traceId;
    this.reasons = reasons;
    // obligations is always a list so PEP code can iterate without a null-check.
    this.obligations =
        obligations != null
            ? Collections.unmodifiableList(new ArrayList<>(obligations))
            : Collections.emptyList();
    this.evaluatedPolicies =
        evaluatedPolicies != null
            ? Collections.unmodifiableList(new ArrayList<>(evaluatedPolicies))
            : Collections.emptyList();
    this.stage = stage;
    this.expiresAt = expiresAt;
    this.error = error;
  }

  /** Returns the verdict: {@code allow}, {@code deny}, or {@code needs_approval}. */
  public String getVerdict() {
    return verdict;
  }

  /** Returns the audit correlator for this decision, or null. */
  public String getDecisionId() {
    return decisionId;
  }

  /** Returns the W3C trace id (32 lowercase hex chars), or null. */
  public String getTraceId() {
    return traceId;
  }

  /** Returns the human-readable reasons, or null. */
  public List<String> getReasons() {
    return reasons;
  }

  /** Returns the engine-fulfillable obligations; never null. */
  public List<Obligation> getObligations() {
    return obligations;
  }

  /** Returns the policies evaluated; never null. */
  public List<String> getEvaluatedPolicies() {
    return evaluatedPolicies;
  }

  /** Returns the echoed decision stage, or null. */
  public String getStage() {
    return stage;
  }

  /** Returns the verdict expiry, or null. */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** Returns the error message on the malformed-deny path, or null. */
  public String getError() {
    return error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecideResponse that = (DecideResponse) o;
    return Objects.equals(verdict, that.verdict)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(traceId, that.traceId)
        && Objects.equals(reasons, that.reasons)
        && Objects.equals(obligations, that.obligations)
        && Objects.equals(evaluatedPolicies, that.evaluatedPolicies)
        && Objects.equals(stage, that.stage)
        && Objects.equals(expiresAt, that.expiresAt)
        && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        verdict,
        decisionId,
        traceId,
        reasons,
        obligations,
        evaluatedPolicies,
        stage,
        expiresAt,
        error);
  }

  @Override
  public String toString() {
    return "DecideResponse{"
        + "verdict='"
        + verdict
        + '\''
        + ", decisionId='"
        + decisionId
        + '\''
        + ", traceId='"
        + traceId
        + '\''
        + ", reasons="
        + reasons
        + ", obligations="
        + obligations
        + ", evaluatedPolicies="
        + evaluatedPolicies
        + ", stage='"
        + stage
        + '\''
        + ", expiresAt="
        + expiresAt
        + ", error='"
        + error
        + '\''
        + '}';
  }
}
