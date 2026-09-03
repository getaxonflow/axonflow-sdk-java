// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Inbound contract for {@code POST /api/v1/decide} (ADR-056, epic #2563). Mirrors the platform
 * {@code DecideRequest}.
 *
 * <p>Required: {@code stage} (one of {@code "llm"} | {@code "tool"} | {@code "agent"}) and {@code
 * query}. {@code userToken} is optional — a PEP that supplies one gets the validated-user record on
 * the audit row; one that doesn't gets a synthesized service user. Build instances with the fluent
 * {@link Builder}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DecideRequest {

  @JsonProperty("stage")
  private final String stage;

  @JsonProperty("query")
  private final String query;

  @JsonProperty("caller_identity")
  private final DecisionCallerIdentity callerIdentity;

  @JsonProperty("target")
  private final DecisionTarget target;

  @JsonProperty("user_token")
  private final String userToken;

  @JsonProperty("context")
  private final Map<String, Object> context;

  private DecideRequest(Builder b) {
    this.stage = b.stage;
    this.query = b.query;
    this.callerIdentity = b.callerIdentity;
    this.target = b.target;
    // Omit blank user_token per the wire contract (omit if empty).
    this.userToken = (b.userToken == null || b.userToken.isEmpty()) ? null : b.userToken;
    // Omit an empty context map per the wire contract (omit if empty).
    this.context = (b.context == null || b.context.isEmpty()) ? null : b.context;
  }

  /**
   * Creates a builder for a decide request.
   *
   * @param stage the decision stage: {@code "llm"}, {@code "tool"}, or {@code "agent"}
   * @param query the request content to be decided on
   * @return a new builder
   */
  public static Builder builder(String stage, String query) {
    return new Builder(stage, query);
  }

  /** Returns the decision stage: {@code "llm"}, {@code "tool"}, or {@code "agent"}. */
  public String getStage() {
    return stage;
  }

  /** Returns the request content to be decided on. */
  public String getQuery() {
    return query;
  }

  /** Returns the gateway-asserted caller identity, or null. */
  public DecisionCallerIdentity getCallerIdentity() {
    return callerIdentity;
  }

  /** Returns the target descriptor, or null. */
  public DecisionTarget getTarget() {
    return target;
  }

  /** Returns the user token, or null when omitted. */
  public String getUserToken() {
    return userToken;
  }

  /** Returns the additional context map, or null when omitted. */
  public Map<String, Object> getContext() {
    return context;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecideRequest that = (DecideRequest) o;
    return Objects.equals(stage, that.stage)
        && Objects.equals(query, that.query)
        && Objects.equals(callerIdentity, that.callerIdentity)
        && Objects.equals(target, that.target)
        && Objects.equals(userToken, that.userToken)
        && Objects.equals(context, that.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stage, query, callerIdentity, target, userToken, context);
  }

  @Override
  public String toString() {
    return "DecideRequest{"
        + "stage='"
        + stage
        + '\''
        + ", query='"
        + query
        + '\''
        + ", callerIdentity="
        + callerIdentity
        + ", target="
        + target
        + ", userToken='"
        + (userToken != null ? "<redacted>" : null)
        + '\''
        + ", context="
        + context
        + '}';
  }

  /** Fluent builder for {@link DecideRequest}. */
  public static final class Builder {
    private final String stage;
    private final String query;
    private DecisionCallerIdentity callerIdentity;
    private DecisionTarget target;
    private String userToken;
    private Map<String, Object> context;

    private Builder(String stage, String query) {
      this.stage = stage;
      this.query = query;
    }

    /**
     * Sets the gateway-asserted caller identity.
     *
     * @param callerIdentity the caller identity
     * @return this builder
     */
    public Builder callerIdentity(DecisionCallerIdentity callerIdentity) {
      this.callerIdentity = callerIdentity;
      return this;
    }

    /**
     * Sets the target descriptor.
     *
     * @param target the target
     * @return this builder
     */
    public Builder target(DecisionTarget target) {
      this.target = target;
      return this;
    }

    /**
     * Sets the user token (omitted from the wire when null or empty).
     *
     * @param userToken the user token
     * @return this builder
     */
    public Builder userToken(String userToken) {
      this.userToken = userToken;
      return this;
    }

    /**
     * Sets the additional context map (omitted from the wire when null or empty).
     *
     * @param context the context map
     * @return this builder
     */
    public Builder context(Map<String, Object> context) {
      this.context = context;
      return this;
    }

    /**
     * Builds the {@link DecideRequest}.
     *
     * @return the request
     */
    public DecideRequest build() {
      return new DecideRequest(this);
    }
  }
}
