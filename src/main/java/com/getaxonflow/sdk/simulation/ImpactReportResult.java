// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Result for a single input in an impact report. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImpactReportResult {

  @JsonProperty("input_index")
  private final int inputIndex;

  @JsonProperty("matched")
  private final boolean matched;

  @JsonProperty("blocked")
  private final boolean blocked;

  @JsonProperty("actions")
  private final List<String> actions;

  public ImpactReportResult(
      @JsonProperty("input_index") int inputIndex,
      @JsonProperty("matched") boolean matched,
      @JsonProperty("blocked") boolean blocked,
      @JsonProperty("actions") List<String> actions) {
    this.inputIndex = inputIndex;
    this.matched = matched;
    this.blocked = blocked;
    this.actions = actions != null ? List.copyOf(actions) : List.of();
  }

  public int getInputIndex() {
    return inputIndex;
  }

  public boolean isMatched() {
    return matched;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public List<String> getActions() {
    return actions;
  }
}
