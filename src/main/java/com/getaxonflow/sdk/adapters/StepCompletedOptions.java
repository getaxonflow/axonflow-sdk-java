// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.adapters;

import java.util.Map;

/** Options for {@link LangGraphAdapter#stepCompleted}. */
public final class StepCompletedOptions {

  private final String stepId;
  private final Map<String, Object> output;
  private final Map<String, Object> metadata;
  private final Integer tokensIn;
  private final Integer tokensOut;
  private final Double costUsd;

  private StepCompletedOptions(Builder builder) {
    this.stepId = builder.stepId;
    this.output = builder.output;
    this.metadata = builder.metadata;
    this.tokensIn = builder.tokensIn;
    this.tokensOut = builder.tokensOut;
    this.costUsd = builder.costUsd;
  }

  public String getStepId() {
    return stepId;
  }

  public Map<String, Object> getOutput() {
    return output;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Integer getTokensIn() {
    return tokensIn;
  }

  public Integer getTokensOut() {
    return tokensOut;
  }

  public Double getCostUsd() {
    return costUsd;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String stepId;
    private Map<String, Object> output;
    private Map<String, Object> metadata;
    private Integer tokensIn;
    private Integer tokensOut;
    private Double costUsd;

    private Builder() {}

    public Builder stepId(String stepId) {
      this.stepId = stepId;
      return this;
    }

    public Builder output(Map<String, Object> output) {
      this.output = output;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder tokensIn(Integer tokensIn) {
      this.tokensIn = tokensIn;
      return this;
    }

    public Builder tokensOut(Integer tokensOut) {
      this.tokensOut = tokensOut;
      return this;
    }

    public Builder costUsd(Double costUsd) {
      this.costUsd = costUsd;
      return this;
    }

    public StepCompletedOptions build() {
      return new StepCompletedOptions(this);
    }
  }
}
