// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.adapters;

/**
 * Functional interface for handling an MCP tool request.
 *
 * <p>Implementations execute the actual tool call and return the result. Used by {@link
 * MCPToolInterceptor} as the downstream handler.
 */
@FunctionalInterface
public interface MCPToolHandler {

  /**
   * Handles an MCP tool request.
   *
   * @param request the tool request to handle
   * @return the result of the tool invocation
   * @throws Exception if the tool call fails
   */
  Object handle(MCPToolRequest request) throws Exception;
}
