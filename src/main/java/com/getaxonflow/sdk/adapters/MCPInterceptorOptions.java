// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
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
  private final String operation;

  private MCPInterceptorOptions(Builder builder) {
    this.connectorTypeFn = builder.connectorTypeFn;
    this.operation = builder.operation;
  }

  /**
   * Returns the function that maps an MCP request to a connector type string. May be null, in which
   * case the default {@link MCPToolRequest#getServerName()} is used.
   *
   * <p>Connector type identifies the MCP server/connector itself; it is sent separately from the
   * tool name (which is always {@link MCPToolRequest#getName()}) so policies can match on server
   * identity, tool identity, or both.
   *
   * @return the connector type function, or null
   */
  public Function<MCPToolRequest, String> getConnectorTypeFn() {
    return connectorTypeFn;
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
    private String operation = "execute";

    private Builder() {}

    /**
     * Sets a custom function to derive the connector type (MCP server identity) from an MCP
     * request. Defaults to {@link MCPToolRequest#getServerName()}.
     *
     * <p>There is deliberately no {@code toolFn} override: the tool identity is always {@link
     * MCPToolRequest#getName()} so a caller cannot write an arbitrary tool identity into the audit
     * trail (epic #2905, RULING 3).
     *
     * @param connectorTypeFn mapping function
     * @return this builder
     */
    public Builder connectorTypeFn(Function<MCPToolRequest, String> connectorTypeFn) {
      this.connectorTypeFn = connectorTypeFn;
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
