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
