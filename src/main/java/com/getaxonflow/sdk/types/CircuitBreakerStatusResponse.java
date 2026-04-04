/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
