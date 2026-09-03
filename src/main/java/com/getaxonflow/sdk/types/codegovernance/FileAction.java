// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonValue;

/** File action for PR files. */
public enum FileAction {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete");

  private final String value;

  FileAction(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static FileAction fromValue(String value) {
    for (FileAction action : values()) {
      if (action.value.equalsIgnoreCase(value)) {
        return action;
      }
    }
    throw new IllegalArgumentException("Unknown file action: " + value);
  }
}
