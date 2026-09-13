// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a response from the AxonFlow Agent.
 *
 * <p>This is the primary response type for Proxy Mode operations. It contains:
 *
 * <ul>
 *   <li>Success indicator and data payload
 *   <li>Blocking status and reason (if policy violation occurred)
 *   <li>Policy information including evaluated policies and processing time
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

  @JsonProperty("budget_info")
  private final BudgetInfo budgetInfo;

  @JsonProperty("media_analysis")
  private final MediaAnalysisResponse mediaAnalysis;

  @JsonProperty("engine")
  private final String engine;

  @JsonProperty("subject_type")
  private final String subjectType;

  @JsonProperty("policy_bundle")
  private final String policyBundle;

  @JsonProperty("legacy_validators")
  private final List<LegacyValidatorAction> legacyValidators;

  /** Creates a client response, as Jackson reads it from the wire (v11.0.0 shape). */
  @JsonCreator
  public ClientResponse(
      @JsonProperty("success") boolean success,
      @JsonProperty("data") Object data,
      @JsonProperty("result") String result,
      @JsonProperty("plan_id") String planId,
      @JsonProperty("blocked") boolean blocked,
      @JsonProperty("block_reason") String blockReason,
      @JsonProperty("policy_info") PolicyInfo policyInfo,
      @JsonProperty("error") String error,
      @JsonProperty("budget_info") BudgetInfo budgetInfo,
      @JsonProperty("media_analysis") MediaAnalysisResponse mediaAnalysis,
      @JsonProperty("engine") String engine,
      @JsonProperty("subject_type") String subjectType,
      @JsonProperty("policy_bundle") String policyBundle,
      @JsonProperty("legacy_validators") List<LegacyValidatorAction> legacyValidators) {
    this.success = success;
    this.data = data;
    this.result = result;
    this.planId = planId;
    this.blocked = blocked;
    this.blockReason = blockReason;
    this.policyInfo = policyInfo;
    this.error = error;
    this.budgetInfo = budgetInfo;
    this.mediaAnalysis = mediaAnalysis;
    this.engine = engine;
    this.subjectType = subjectType;
    this.policyBundle = policyBundle;
    this.legacyValidators =
        legacyValidators != null
            ? Collections.unmodifiableList(new ArrayList<>(legacyValidators))
            : Collections.emptyList();
  }

  /** Source-compat overload preserving the pre-v11 shape; the provenance fields are unset. */
  public ClientResponse(
      boolean success,
      Object data,
      String result,
      String planId,
      boolean blocked,
      String blockReason,
      PolicyInfo policyInfo,
      String error,
      BudgetInfo budgetInfo,
      MediaAnalysisResponse mediaAnalysis) {
    this(
        success,
        data,
        result,
        planId,
        blocked,
        blockReason,
        policyInfo,
        error,
        budgetInfo,
        mediaAnalysis,
        null,
        null,
        null,
        null);
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

  /**
   * Returns budget enforcement status information.
   *
   * @return the budget info, may be null if no budget check was performed
   */
  public BudgetInfo getBudgetInfo() {
    return budgetInfo;
  }

  /**
   * Returns media analysis results if media was submitted.
   *
   * @return the media analysis response, may be null
   */
  public MediaAnalysisResponse getMediaAnalysis() {
    return mediaAnalysis;
  }

  /**
   * Returns the policy engine that authored this verdict: {@code anchored}, the v11 decision plane
   * (PRD v11 §1.1), or {@code legacy}, the proxy's tier engine. Null on a platform older than
   * v11.0.0.
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientResponse that = (ClientResponse) o;
    return success == that.success
        && blocked == that.blocked
        && Objects.equals(data, that.data)
        && Objects.equals(result, that.result)
        && Objects.equals(planId, that.planId)
        && Objects.equals(blockReason, that.blockReason)
        && Objects.equals(policyInfo, that.policyInfo)
        && Objects.equals(error, that.error)
        && Objects.equals(budgetInfo, that.budgetInfo)
        && Objects.equals(mediaAnalysis, that.mediaAnalysis)
        && Objects.equals(engine, that.engine)
        && Objects.equals(subjectType, that.subjectType)
        && Objects.equals(policyBundle, that.policyBundle)
        && Objects.equals(legacyValidators, that.legacyValidators);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        success,
        data,
        result,
        planId,
        blocked,
        blockReason,
        policyInfo,
        error,
        budgetInfo,
        mediaAnalysis,
        engine,
        subjectType,
        policyBundle,
        legacyValidators);
  }

  @Override
  public String toString() {
    return "ClientResponse{"
        + "success="
        + success
        + ", blocked="
        + blocked
        + ", blockReason='"
        + blockReason
        + '\''
        + ", policyInfo="
        + policyInfo
        + ", error='"
        + error
        + '\''
        + ", budgetInfo="
        + budgetInfo
        + ", mediaAnalysis="
        + mediaAnalysis
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
        + '}';
  }
}
