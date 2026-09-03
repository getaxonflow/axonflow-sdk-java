// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Options for generating a multi-agent plan.
 *
 * <p>Provides additional configuration beyond what is in {@link PlanRequest}, such as execution
 * mode control.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * GeneratePlanOptions options = GeneratePlanOptions.builder()
 *     .executionMode(ExecutionMode.PARALLEL)
 *     .build();
 *
 * PlanResponse plan = axonflow.generatePlan(request, options);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GeneratePlanOptions {

  @JsonProperty("execution_mode")
  private final ExecutionMode executionMode;

  private GeneratePlanOptions(Builder builder) {
    this.executionMode = builder.executionMode;
  }

  /**
   * Returns the execution mode for the plan.
   *
   * @return the execution mode, or null if not specified
   */
  public ExecutionMode getExecutionMode() {
    return executionMode;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GeneratePlanOptions that = (GeneratePlanOptions) o;
    return executionMode == that.executionMode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionMode);
  }

  @Override
  public String toString() {
    return "GeneratePlanOptions{" + "executionMode=" + executionMode + '}';
  }

  /** Builder for GeneratePlanOptions. */
  public static final class Builder {
    private ExecutionMode executionMode;

    private Builder() {}

    /**
     * Sets the execution mode for plan generation.
     *
     * @param executionMode the execution mode
     * @return this builder
     */
    public Builder executionMode(ExecutionMode executionMode) {
      this.executionMode = executionMode;
      return this;
    }

    /**
     * Builds the GeneratePlanOptions.
     *
     * @return a new GeneratePlanOptions instance
     */
    public GeneratePlanOptions build() {
      return new GeneratePlanOptions(this);
    }
  }
}
