// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Reference to a policy involved in a conflict. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyConflictRef {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("type")
  private final String type;

  public PolicyConflictRef(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }
}
