// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Response from the circuit breaker status endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CircuitBreakerStatusResponse {

  @JsonProperty("active_circuits")
  private final List<Map<String, Object>> activeCircuits;

  @JsonProperty("count")
  private final int count;

  @JsonProperty("emergency_stop_active")
  private final boolean emergencyStopActive;

  public CircuitBreakerStatusResponse(
      @JsonProperty("active_circuits") List<Map<String, Object>> activeCircuits,
      @JsonProperty("count") int count,
      @JsonProperty("emergency_stop_active") boolean emergencyStopActive) {
    this.activeCircuits = activeCircuits != null ? List.copyOf(activeCircuits) : List.of();
    this.count = count;
    this.emergencyStopActive = emergencyStopActive;
  }

  /**
   * Returns the list of currently active (tripped) circuits.
   *
   * @return the active circuits
   */
  public List<Map<String, Object>> getActiveCircuits() {
    return activeCircuits;
  }

  /**
   * Returns the number of active circuits.
   *
   * @return the count
   */
  public int getCount() {
    return count;
  }

  /**
   * Returns whether the emergency stop is currently active.
   *
   * @return true if emergency stop is active
   */
  public boolean isEmergencyStopActive() {
    return emergencyStopActive;
  }
}
