// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.adapters;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an MCP tool invocation request.
 *
 * <p>Used by {@link MCPToolInterceptor} to pass tool call information through the interceptor
 * chain.
 */
public final class MCPToolRequest {

  private final String serverName;
  private final String name;
  private final Map<String, Object> args;

  /**
   * Creates a new MCPToolRequest.
   *
   * @param serverName the MCP server name
   * @param name the tool name
   * @param args the tool arguments
   */
  public MCPToolRequest(String serverName, String name, Map<String, Object> args) {
    this.serverName = Objects.requireNonNull(serverName, "serverName cannot be null");
    this.name = Objects.requireNonNull(name, "name cannot be null");
    this.args = args != null ? Collections.unmodifiableMap(args) : Collections.emptyMap();
  }

  /**
   * Returns the MCP server name.
   *
   * @return the server name
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * Returns the tool name.
   *
   * @return the tool name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the tool arguments.
   *
   * @return immutable map of arguments
   */
  public Map<String, Object> getArgs() {
    return args;
  }
}
