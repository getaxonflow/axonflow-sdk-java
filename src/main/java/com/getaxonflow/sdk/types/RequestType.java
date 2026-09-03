// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonValue;

/** Types of requests that can be processed by AxonFlow. */
public enum RequestType {
  /** Standard chat/conversation request. */
  CHAT("chat"),

  /** SQL query request. */
  SQL("sql"),

  /** MCP (Model Context Protocol) connector query. */
  MCP_QUERY("mcp-query"),

  /** Multi-agent planning request. */
  MULTI_AGENT_PLAN("multi-agent-plan");

  private final String value;

  RequestType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Parses a string value to a RequestType enum.
   *
   * @param value the string value to parse
   * @return the corresponding RequestType
   * @throws IllegalArgumentException if the value is not recognized
   */
  public static RequestType fromValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Request type cannot be null");
    }
    for (RequestType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown request type: " + value);
  }
}
