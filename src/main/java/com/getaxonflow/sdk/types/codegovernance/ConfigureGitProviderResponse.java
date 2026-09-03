// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Response from Git provider configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConfigureGitProviderResponse {

  @JsonProperty("message")
  private final String message;

  @JsonProperty("type")
  private final String type;

  public ConfigureGitProviderResponse(
      @JsonProperty("message") String message, @JsonProperty("type") String type) {
    this.message = message != null ? message : "";
    this.type = type != null ? type : "";
  }

  public String getMessage() {
    return message;
  }

  public String getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConfigureGitProviderResponse that = (ConfigureGitProviderResponse) o;
    return Objects.equals(message, that.message) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, type);
  }

  @Override
  public String toString() {
    return "ConfigureGitProviderResponse{"
        + "message='"
        + message
        + '\''
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
