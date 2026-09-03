// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Request parameters for searching audit logs.
 *
 * <p>All fields are optional - omit to search all logs with default limit.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * AuditSearchRequest request = AuditSearchRequest.builder()
 *     .userEmail("analyst@company.com")
 *     .startTime(Instant.now().minus(Duration.ofDays(1)))
 *     .limit(100)
 *     .build();
 *
 * AuditSearchResponse response = axonflow.searchAuditLogs(request);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AuditSearchRequest {

  @JsonProperty("user_email")
  private final String userEmail;

  @JsonProperty("client_id")
  private final String clientId;

  @JsonProperty("start_time")
  private final String startTime;

  @JsonProperty("end_time")
  private final String endTime;

  @JsonProperty("request_type")
  private final String requestType;

  /**
   * Filters by action/request type with verdict normalization on the server side. This is the
   * filter the 9.x server actually reads; {@code request_type} is silently ignored (#3254).
   */
  @JsonProperty("action")
  private final String action;

  /** Filter by decision ID (ADR-043). Gathers every audit record tied to one decision. */
  @JsonProperty("decision_id")
  private final String decisionId;

  /** Filter by matched policy name (ADR-043). */
  @JsonProperty("policy_name")
  private final String policyName;

  /**
   * Filter by session override ID (ADR-042). Reconstructs an override's full lifecycle:
   * override_created → override_used → override_expired | override_revoked.
   */
  @JsonProperty("override_id")
  private final String overrideId;

  @JsonProperty("limit")
  private final Integer limit;

  @JsonProperty("offset")
  private final Integer offset;

  private AuditSearchRequest(Builder builder) {
    this.userEmail = builder.userEmail;
    this.clientId = builder.clientId;
    this.startTime = builder.startTime != null ? builder.startTime.toString() : null;
    this.endTime = builder.endTime != null ? builder.endTime.toString() : null;
    this.requestType = builder.requestType;
    this.action = builder.action;
    this.decisionId = builder.decisionId;
    this.policyName = builder.policyName;
    this.overrideId = builder.overrideId;
    this.limit = builder.limit != null ? Math.min(builder.limit, 1000) : 100;
    this.offset = builder.offset;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public String getClientId() {
    return clientId;
  }

  public String getStartTime() {
    return startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  /**
   * Returns the request-type filter.
   *
   * @deprecated the 9.x server does not read this filter; a search filtered only by it returns
   *     unfiltered results. Use {@link #getAction()} / {@link Builder#action(String)}. The SDK
   *     keeps sending it (harmless, ignored). Scheduled for removal in the next major (#3254).
   */
  @Deprecated
  public String getRequestType() {
    return requestType;
  }

  /** Returns the action filter (server-side verdict normalization applies). */
  public String getAction() {
    return action;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public String getPolicyName() {
    return policyName;
  }

  public String getOverrideId() {
    return overrideId;
  }

  public Integer getLimit() {
    return limit;
  }

  public Integer getOffset() {
    return offset;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuditSearchRequest that = (AuditSearchRequest) o;
    return Objects.equals(userEmail, that.userEmail)
        && Objects.equals(clientId, that.clientId)
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && Objects.equals(requestType, that.requestType)
        && Objects.equals(limit, that.limit)
        && Objects.equals(offset, that.offset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userEmail, clientId, startTime, endTime, requestType, limit, offset);
  }

  @Override
  public String toString() {
    return "AuditSearchRequest{"
        + "userEmail='"
        + userEmail
        + '\''
        + ", clientId='"
        + clientId
        + '\''
        + ", requestType='"
        + requestType
        + '\''
        + ", limit="
        + limit
        + ", offset="
        + offset
        + '}';
  }

  /** Builder for AuditSearchRequest. */
  public static final class Builder {
    private String userEmail;
    private String clientId;
    private Instant startTime;
    private Instant endTime;
    private String requestType;
    private String action;
    private String decisionId;
    private String policyName;
    private String overrideId;
    private Integer limit;
    private Integer offset;

    private Builder() {}

    /** Filter by user email. */
    public Builder userEmail(String userEmail) {
      this.userEmail = userEmail;
      return this;
    }

    /** Filter by client/application ID. */
    public Builder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    /** Start of time range to search. */
    public Builder startTime(Instant startTime) {
      this.startTime = startTime;
      return this;
    }

    /** End of time range to search. */
    public Builder endTime(Instant endTime) {
      this.endTime = endTime;
      return this;
    }

    /**
     * Filter by request type (e.g., "llm_chat", "policy_check").
     *
     * @deprecated the 9.x server does not read this filter; a search filtered only by it returns
     *     unfiltered results. Use {@link #action(String)}. The SDK keeps sending it (harmless,
     *     ignored). Scheduled for removal in the next major (#3254).
     */
    @Deprecated
    public Builder requestType(String requestType) {
      this.requestType = requestType;
      return this;
    }

    /**
     * Filters by action/request type with verdict normalization on the server side. The value is
     * normalized to its canonical verdict (e.g. {@code allowed}, {@code blocked}, {@code redacted},
     * {@code error}) and expanded to every historical spelling of that verdict, so it matches both
     * current and legacy rows.
     */
    public Builder action(String action) {
      this.action = action;
      return this;
    }

    /**
     * Filter by decision ID (ADR-043). Use to gather every audit record tied to a single decision -
     * the explain-flow cross-reference pivot.
     */
    public Builder decisionId(String decisionId) {
      this.decisionId = decisionId;
      return this;
    }

    /** Filter by matched policy name (ADR-043). */
    public Builder policyName(String policyName) {
      this.policyName = policyName;
      return this;
    }

    /**
     * Filter by session override ID (ADR-042). Use to reconstruct an override's full lifecycle
     * (override_created → override_used → override_expired | override_revoked).
     */
    public Builder overrideId(String overrideId) {
      this.overrideId = overrideId;
      return this;
    }

    /** Maximum results to return (default: 100, max: 1000). */
    public Builder limit(int limit) {
      this.limit = limit;
      return this;
    }

    /** Pagination offset (default: 0). */
    public Builder offset(int offset) {
      this.offset = offset;
      return this;
    }

    public AuditSearchRequest build() {
      return new AuditSearchRequest(this);
    }
  }
}
