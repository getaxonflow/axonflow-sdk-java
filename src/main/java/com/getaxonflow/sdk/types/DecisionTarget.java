// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Describes what the gateway is about to call on a {@code POST /api/v1/decide} request (ADR-056,
 * epic #2563). Mirrors the platform {@code DecisionTarget}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecisionTarget {

  @JsonProperty("type")
  private final String type;

  @JsonProperty("model")
  private final String model;

  @JsonProperty("provider")
  private final String provider;

  @JsonProperty("tool")
  private final String tool;

  /**
   * Creates a target descriptor. All fields are optional.
   *
   * @param type {@code "llm"}, {@code "tool"}, or {@code "agent"} (may be null)
   * @param model the model name when {@code type=llm} (may be null)
   * @param provider the provider when {@code type=llm} (may be null)
   * @param tool the tool name when {@code type=tool} (may be null)
   */
  @JsonCreator
  public DecisionTarget(
      @JsonProperty("type") String type,
      @JsonProperty("model") String model,
      @JsonProperty("provider") String provider,
      @JsonProperty("tool") String tool) {
    this.type = type;
    this.model = model;
    this.provider = provider;
    this.tool = tool;
  }

  /** Returns the target type: {@code "llm"}, {@code "tool"}, or {@code "agent"}, or null. */
  public String getType() {
    return type;
  }

  /** Returns the model name (when {@code type=llm}), or null. */
  public String getModel() {
    return model;
  }

  /** Returns the provider (when {@code type=llm}), or null. */
  public String getProvider() {
    return provider;
  }

  /** Returns the tool name (when {@code type=tool}), or null. */
  public String getTool() {
    return tool;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionTarget that = (DecisionTarget) o;
    return Objects.equals(type, that.type)
        && Objects.equals(model, that.model)
        && Objects.equals(provider, that.provider)
        && Objects.equals(tool, that.tool);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, model, provider, tool);
  }

  @Override
  public String toString() {
    return "DecisionTarget{"
        + "type='"
        + type
        + '\''
        + ", model='"
        + model
        + '\''
        + ", provider='"
        + provider
        + '\''
        + ", tool='"
        + tool
        + '\''
        + '}';
  }
}
