// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/** A Decision API call held for a person's review. A hold is never permission to forward. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecisionPendingApproval {
  @JsonProperty("approval_id")
  private final String approvalId;

  @JsonProperty("status")
  private final String status;

  @JsonProperty("plane")
  private final String plane;

  @JsonProperty("expires_at")
  private final Instant expiresAt;

  @JsonProperty("retry")
  private final Retry retry;

  @JsonCreator
  public DecisionPendingApproval(
      @JsonProperty("approval_id") String approvalId,
      @JsonProperty("status") String status,
      @JsonProperty("plane") String plane,
      @JsonProperty("expires_at") Instant expiresAt,
      @JsonProperty("retry") Retry retry) {
    this.approvalId = approvalId;
    this.status = status;
    this.plane = plane;
    this.expiresAt = expiresAt;
    this.retry = retry;
  }

  public String getApprovalId() {
    return approvalId;
  }

  public String getStatus() {
    return status;
  }

  public String getPlane() {
    return plane;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Retry getRetry() {
    return retry;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DecisionPendingApproval)) return false;
    DecisionPendingApproval that = (DecisionPendingApproval) o;
    return Objects.equals(approvalId, that.approvalId)
        && Objects.equals(status, that.status)
        && Objects.equals(plane, that.plane)
        && Objects.equals(expiresAt, that.expiresAt)
        && Objects.equals(retry, that.retry);
  }

  @Override
  public int hashCode() {
    return Objects.hash(approvalId, status, plane, expiresAt, retry);
  }

  /** The header, request argument, or body field that names a held call on retry. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Retry {
    @JsonProperty("header")
    private final String header;

    @JsonProperty("argument")
    private final String argument;

    @JsonProperty("body_field")
    private final String bodyField;

    @JsonCreator
    public Retry(
        @JsonProperty("header") String header,
        @JsonProperty("argument") String argument,
        @JsonProperty("body_field") String bodyField) {
      this.header = header;
      this.argument = argument;
      this.bodyField = bodyField;
    }

    public String getHeader() {
      return header;
    }

    public String getArgument() {
      return argument;
    }

    public String getBodyField() {
      return bodyField;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Retry)) return false;
      Retry that = (Retry) o;
      return Objects.equals(header, that.header)
          && Objects.equals(argument, that.argument)
          && Objects.equals(bodyField, that.bodyField);
    }

    @Override
    public int hashCode() {
      return Objects.hash(header, argument, bodyField);
    }
  }
}
