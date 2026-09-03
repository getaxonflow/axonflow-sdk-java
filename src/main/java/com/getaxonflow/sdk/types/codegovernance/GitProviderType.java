// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonValue;

/** Supported Git providers for code governance. */
public enum GitProviderType {
  GITHUB("github"),
  GITLAB("gitlab"),
  BITBUCKET("bitbucket");

  private final String value;

  GitProviderType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static GitProviderType fromValue(String value) {
    for (GitProviderType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown Git provider type: " + value);
  }
}
