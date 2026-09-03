// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Daily usage counters for policy simulation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SimulationDailyUsage {

  @JsonProperty("used")
  private final int used;

  @JsonProperty("limit")
  private final int limit;

  public SimulationDailyUsage(@JsonProperty("used") int used, @JsonProperty("limit") int limit) {
    this.used = used;
    this.limit = limit;
  }

  public int getUsed() {
    return used;
  }

  public int getLimit() {
    return limit;
  }
}
