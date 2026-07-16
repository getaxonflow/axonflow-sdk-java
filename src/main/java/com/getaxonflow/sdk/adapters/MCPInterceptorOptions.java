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

import java.util.function.Function;

/**
 * Options for {@link LangGraphAdapter#mcpToolInterceptor}.
 *
 * <p>Controls how MCP tool requests are mapped to connector types and what operation type is used
 * for policy checks.
 */
public final class MCPInterceptorOptions {

  private final Function<MCPToolRequest, String> connectorTypeFn;
  private final Function<MCPToolRequest, String> toolFn;
  private final String operation;

  private MCPInterceptorOptions(Builder builder) {
    this.connectorTypeFn = builder.connectorTypeFn;
    this.toolFn = builder.toolFn;
    this.operation = builder.operation;
  }

  /**
   * Returns the function that maps an MCP request to a connector type string. May be null, in
   * which case the default {@link MCPToolRequest#getServerName()} is used.
   *
   * <p>Connector type identifies the MCP server/connector itself; it is sent separately from the
   * tool name (see {@link #getToolFn()}) so policies can match on server identity, tool identity,
   * or both.
   *
   * @return the connector type function, or null
   */
  public Function<MCPToolRequest, String> getConnectorTypeFn() {
    return connectorTypeFn;
  }

  /**
   * Returns the function that maps an MCP request to a tool name string. May be null, in which
   * case the default {@link MCPToolRequest#getName()} is used.
   *
   * @return the tool name function, or null
   */
  public Function<MCPToolRequest, String> getToolFn() {
    return toolFn;
  }

  /**
   * Returns the operation type passed to {@code mcpCheckInput}. Defaults to "execute".
   *
   * @return the operation type
   */
  public String getOperation() {
    return operation;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Function<MCPToolRequest, String> connectorTypeFn;
    private Function<MCPToolRequest, String> toolFn;
    private String operation = "execute";

    private Builder() {}

    /**
     * Sets a custom function to derive the connector type (MCP server identity) from an MCP
     * request. Defaults to {@link MCPToolRequest#getServerName()}.
     *
     * @param connectorTypeFn mapping function
     * @return this builder
     */
    public Builder connectorTypeFn(Function<MCPToolRequest, String> connectorTypeFn) {
      this.connectorTypeFn = connectorTypeFn;
      return this;
    }

    /**
     * Sets a custom function to derive the tool name from an MCP request. Defaults to {@link
     * MCPToolRequest#getName()}.
     *
     * @param toolFn mapping function
     * @return this builder
     */
    public Builder toolFn(Function<MCPToolRequest, String> toolFn) {
      this.toolFn = toolFn;
      return this;
    }

    /**
     * Sets the operation type. Defaults to "execute". Use "query" for known read-only tool calls.
     *
     * @param operation the operation type
     * @return this builder
     */
    public Builder operation(String operation) {
      this.operation = operation;
      return this;
    }

    public MCPInterceptorOptions build() {
      return new MCPInterceptorOptions(this);
    }
  }
}
