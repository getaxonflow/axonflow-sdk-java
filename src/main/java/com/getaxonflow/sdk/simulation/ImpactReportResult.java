/*
 * Copyright 2026 AxonFlow
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
