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

/** Response from an MCP connector query. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectorResponse {

  @JsonProperty("success")
  private final boolean success;

  @JsonProperty("data")
  private final Object data;

  @JsonProperty("error")
  private final String error;

  @JsonProperty("connector_id")
  private final String connectorId;

  @JsonProperty("operation")
  private final String operation;

  @JsonProperty("processing_time")
  private final String processingTime;

  @JsonProperty("redacted")
  private final boolean redacted;

  @JsonProperty("redacted_fields")
  private final List<String> redactedFields;

  @JsonProperty("policy_info")
  private final ConnectorPolicyInfo policyInfo;

  @JsonProperty("engine")
  private final String engine;

  @JsonProperty("subject_type")
  private final String subjectType;

  @JsonProperty("policy_bundle")
  private final String policyBundle;

  @JsonProperty("legacy_validators")
  private final List<LegacyValidatorAction> legacyValidators;

  /** Creates a connector response, as Jackson reads it from the wire (v11.0.0 shape). */
  @JsonCreator
  public ConnectorResponse(
      @JsonProperty("success") boolean success,
      @JsonProperty("data") Object data,
      @JsonProperty("error") String error,
      @JsonProperty("connector_id") String connectorId,
      @JsonProperty("operation") String operation,
      @JsonProperty("processing_time") String processingTime,
      @JsonProperty("redacted") boolean redacted,
      @JsonProperty("redacted_fields") List<String> redactedFields,
      @JsonProperty("policy_info") ConnectorPolicyInfo policyInfo,
      @JsonProperty("engine") String engine,
      @JsonProperty("subject_type") String subjectType,
      @JsonProperty("policy_bundle") String policyBundle,
      @JsonProperty("legacy_validators") List<LegacyValidatorAction> legacyValidators) {
    this.success = success;
    this.data = data;
    this.error = error;
    this.connectorId = connectorId;
    this.operation = operation;
    this.processingTime = processingTime;
    this.redacted = redacted;
    this.redactedFields = redactedFields != null ? redactedFields : Collections.emptyList();
    this.policyInfo = policyInfo;
    this.engine = engine;
    this.subjectType = subjectType;
    this.policyBundle = policyBundle;
    this.legacyValidators =
        legacyValidators != null
            ? Collections.unmodifiableList(new ArrayList<>(legacyValidators))
            : Collections.emptyList();
  }

  /** Source-compat overload preserving the pre-v11 shape; the provenance fields are unset. */
  public ConnectorResponse(
      boolean success,
      Object data,
      String error,
      String connectorId,
      String operation,
      String processingTime,
      boolean redacted,
      List<String> redactedFields,
      ConnectorPolicyInfo policyInfo) {
    this(
        success,
        data,
        error,
        connectorId,
        operation,
        processingTime,
        redacted,
        redactedFields,
        policyInfo,
        null,
        null,
        null,
        null);
  }

  /**
   * Backward-compatible constructor without policy fields. Creates a ConnectorResponse with default
   * values for redacted (false), redactedFields (empty list), and policyInfo (null).
   */
  public ConnectorResponse(
      boolean success,
      Object data,
      String error,
      String connectorId,
      String operation,
      String processingTime) {
    this(success, data, error, connectorId, operation, processingTime, false, null, null);
  }

  public boolean isSuccess() {
    return success;
  }

  public Object getData() {
    return data;
  }

  public String getError() {
    return error;
  }

  public String getConnectorId() {
    return connectorId;
  }

  public String getOperation() {
    return operation;
  }

  public String getProcessingTime() {
    return processingTime;
  }

  public boolean isRedacted() {
    return redacted;
  }

  public List<String> getRedactedFields() {
    return redactedFields;
  }

  public ConnectorPolicyInfo getPolicyInfo() {
    return policyInfo;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConnectorResponse that = (ConnectorResponse) o;
    return success == that.success
        && redacted == that.redacted
        && Objects.equals(data, that.data)
        && Objects.equals(error, that.error)
        && Objects.equals(connectorId, that.connectorId)
        && Objects.equals(operation, that.operation)
        && Objects.equals(redactedFields, that.redactedFields)
        && Objects.equals(policyInfo, that.policyInfo)
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
        error,
        connectorId,
        operation,
        redacted,
        redactedFields,
        policyInfo,
        engine,
        subjectType,
        policyBundle,
        legacyValidators);
  }

  @Override
  public String toString() {
    return "ConnectorResponse{"
        + "success="
        + success
        + ", connectorId='"
        + connectorId
        + '\''
        + ", operation='"
        + operation
        + '\''
        + ", redacted="
        + redacted
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
        + '}';
  }
}
