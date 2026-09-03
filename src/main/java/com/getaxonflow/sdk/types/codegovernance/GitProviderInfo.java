// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Basic info about a configured Git provider. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GitProviderInfo {

  @JsonProperty("type")
  private final GitProviderType type;

  public GitProviderInfo(@JsonProperty("type") GitProviderType type) {
    this.type = type;
  }

  public GitProviderType getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitProviderInfo that = (GitProviderInfo) o;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return "GitProviderInfo{type=" + type + '}';
  }
}
