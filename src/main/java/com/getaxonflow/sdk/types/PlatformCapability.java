// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Represents a capability advertised by the AxonFlow platform. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlatformCapability {

  @JsonProperty("name")
  private final String name;

  @JsonProperty("since")
  private final String since;

  @JsonProperty("description")
  private final String description;

  public PlatformCapability(
      @JsonProperty("name") String name,
      @JsonProperty("since") String since,
      @JsonProperty("description") String description) {
    this.name = name;
    this.since = since;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getSince() {
    return since;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlatformCapability that = (PlatformCapability) o;
    return Objects.equals(name, that.name)
        && Objects.equals(since, that.since)
        && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, since, description);
  }

  @Override
  public String toString() {
    return "PlatformCapability{name='"
        + name
        + "', since='"
        + since
        + "', description='"
        + description
        + "'}";
  }
}
