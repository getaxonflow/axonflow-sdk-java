// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * A single input to test against a policy in an impact report.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * ImpactReportInput input = ImpactReportInput.builder()
 *     .query("Transfer funds to external account")
 *     .requestType("execute")
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImpactReportInput {

  @JsonProperty("query")
  private final String query;

  @JsonProperty("request_type")
  private final String requestType;

  @JsonProperty("context")
  private final Map<String, Object> context;

  private ImpactReportInput(Builder builder) {
    this.query = Objects.requireNonNull(builder.query, "query cannot be null");
    this.requestType = builder.requestType;
    this.context = builder.context;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getQuery() {
    return query;
  }

  public String getRequestType() {
    return requestType;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  /** Builder for {@link ImpactReportInput}. */
  public static final class Builder {
    private String query;
    private String requestType;
    private Map<String, Object> context;

    public Builder query(String query) {
      this.query = query;
      return this;
    }

    public Builder requestType(String requestType) {
      this.requestType = requestType;
      return this;
    }

    public Builder context(Map<String, Object> context) {
      this.context = context;
      return this;
    }

    public ImpactReportInput build() {
      return new ImpactReportInput(this);
    }
  }
}
