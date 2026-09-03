// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Response from Git provider validation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ValidateGitProviderResponse {

  @JsonProperty("valid")
  private final boolean valid;

  @JsonProperty("message")
  private final String message;

  public ValidateGitProviderResponse(
      @JsonProperty("valid") boolean valid, @JsonProperty("message") String message) {
    this.valid = valid;
    this.message = message != null ? message : "";
  }

  public boolean isValid() {
    return valid;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ValidateGitProviderResponse that = (ValidateGitProviderResponse) o;
    return valid == that.valid && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valid, message);
  }

  @Override
  public String toString() {
    return "ValidateGitProviderResponse{" + "valid=" + valid + ", message='" + message + '\'' + '}';
  }
}
