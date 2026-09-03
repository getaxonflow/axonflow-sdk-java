// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response from the circuit breaker history endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CircuitBreakerHistoryResponse {

  @JsonProperty("history")
  private final List<CircuitBreakerHistoryEntry> history;

  @JsonProperty("count")
  private final int count;

  public CircuitBreakerHistoryResponse(
      @JsonProperty("history") List<CircuitBreakerHistoryEntry> history,
      @JsonProperty("count") int count) {
    this.history = history != null ? List.copyOf(history) : List.of();
    this.count = count;
  }

  /**
   * Returns the list of circuit breaker history entries.
   *
   * @return the history entries
   */
  public List<CircuitBreakerHistoryEntry> getHistory() {
    return history;
  }

  /**
   * Returns the total number of history entries.
   *
   * @return the count
   */
  public int getCount() {
    return count;
  }
}
