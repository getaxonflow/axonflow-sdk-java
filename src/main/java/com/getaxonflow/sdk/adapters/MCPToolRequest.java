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
