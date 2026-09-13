// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
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

  @JsonProperty("engine")
  private final String engine;

  @JsonProperty("subject_type")
  private final String subjectType;

  @JsonProperty("policy_bundle")
  private final String policyBundle;

  @JsonProperty("legacy_validators")
  private final List<LegacyValidatorAction> legacyValidators;

  @JsonProperty("policy_identities")
  private final List<PolicyIdentity> policyIdentities;

  @JsonProperty("policy_packs")
  private final List<String> policyPacks;

  @JsonProperty("document_version")
  private final Integer documentVersion;

  /**
   * Creates a decide response, as Jackson reads it from the wire (v11.0.0 shape).
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
   * @param engine the engine that authored the verdict, or null on an older platform
   * @param subjectType the type of principal the verdict was decided for, or null
   * @param policyBundle the digest of the policy set that decided, or null
   * @param legacyValidators the validators that acted before the engine; null is normalized to an
   *     empty list
   * @param policyIdentities each evaluated policy named, in order; null is normalized to an empty
   *     list
   * @param policyPacks the add-on packs that composed into the bundle; null is normalized to an
   *     empty list
   * @param documentVersion the organization document's published version, or null
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
      @JsonProperty("error") String error,
      @JsonProperty("engine") String engine,
      @JsonProperty("subject_type") String subjectType,
      @JsonProperty("policy_bundle") String policyBundle,
      @JsonProperty("legacy_validators") List<LegacyValidatorAction> legacyValidators,
      @JsonProperty("policy_identities") List<PolicyIdentity> policyIdentities,
      @JsonProperty("policy_packs") List<String> policyPacks,
      @JsonProperty("document_version") Integer documentVersion) {
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
    this.engine = engine;
    this.subjectType = subjectType;
    this.policyBundle = policyBundle;
    this.legacyValidators =
        legacyValidators != null
            ? Collections.unmodifiableList(new ArrayList<>(legacyValidators))
            : Collections.emptyList();
    this.policyIdentities =
        policyIdentities != null
            ? Collections.unmodifiableList(new ArrayList<>(policyIdentities))
            : Collections.emptyList();
    this.policyPacks =
        policyPacks != null
            ? Collections.unmodifiableList(new ArrayList<>(policyPacks))
            : Collections.emptyList();
    this.documentVersion = documentVersion;
  }

  /**
   * Source-compat overload preserving the pre-v11 shape; the v11.0.0 provenance fields are unset.
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
  public DecideResponse(
      String verdict,
      String decisionId,
      String traceId,
      List<String> reasons,
      List<Obligation> obligations,
      List<String> evaluatedPolicies,
      String stage,
      Instant expiresAt,
      String error) {
    this(
        verdict,
        decisionId,
        traceId,
        reasons,
        obligations,
        evaluatedPolicies,
        stage,
        expiresAt,
        error,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
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

  /**
   * Returns the policy engine that authored this verdict: {@code anchored}, the v11 decision plane
   * (PRD v11 §1.1). Null on a platform older than v11.0.0.
   */
  public String getEngine() {
    return engine;
  }

  /** Returns the type of principal the verdict was decided for, or null when not reported. */
  public String getSubjectType() {
    return subjectType;
  }

  /** Returns the digest of the policy set that decided, or null when not reported. */
  public String getPolicyBundle() {
    return policyBundle;
  }

  /** Returns the validators that acted before the engine decided; empty when none did. */
  public List<LegacyValidatorAction> getLegacyValidators() {
    return legacyValidators;
  }

  /**
   * Returns each entry of {@link #getEvaluatedPolicies()} named, in the same order (PRD v11 §1.14);
   * empty on an older platform.
   */
  public List<PolicyIdentity> getPolicyIdentities() {
    return policyIdentities;
  }

  /**
   * Returns the add-on policy packs that composed into the bundle, each as {@code <pack
   * id>@<digest>}, sorted; empty when none did.
   */
  public List<String> getPolicyPacks() {
    return policyPacks;
  }

  /**
   * Returns the published version of the organization's active typed document, or null while it has
   * published nothing.
   */
  public Integer getDocumentVersion() {
    return documentVersion;
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
        && Objects.equals(error, that.error)
        && Objects.equals(engine, that.engine)
        && Objects.equals(subjectType, that.subjectType)
        && Objects.equals(policyBundle, that.policyBundle)
        && Objects.equals(legacyValidators, that.legacyValidators)
        && Objects.equals(policyIdentities, that.policyIdentities)
        && Objects.equals(policyPacks, that.policyPacks)
        && Objects.equals(documentVersion, that.documentVersion);
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
        error,
        engine,
        subjectType,
        policyBundle,
        legacyValidators,
        policyIdentities,
        policyPacks,
        documentVersion);
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
        + ", engine='"
        + engine
        + '\''
        + ", subjectType='"
        + subjectType
        + '\''
        + ", policyBundle='"
        + policyBundle
        + '\''
        + ", legacyValidators="
        + legacyValidators
        + ", policyIdentities="
        + policyIdentities
        + ", policyPacks="
        + policyPacks
        + ", documentVersion="
        + documentVersion
        + '}';
  }
}
