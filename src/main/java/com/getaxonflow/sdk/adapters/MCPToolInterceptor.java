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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Intercepts MCP tool calls with AxonFlow policy enforcement.
 *
 * <p>Wraps each MCP tool invocation with pre-call input validation and post-call output validation.
 * If a policy blocks the input or output, a {@link PolicyViolationException} is thrown. If
 * redaction is active, the redacted data is returned instead of the raw result.
 *
 * <p>Obtain an instance via {@link LangGraphAdapter#mcpToolInterceptor()}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
 * Object result = interceptor.intercept(request, req -> callMCPServer(req));
 * }</pre>
 */
public final class MCPToolInterceptor {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AxonFlow client;
  private final Function<MCPToolRequest, String> connectorTypeFn;
  private final String operation;

  MCPToolInterceptor(
      AxonFlow client, Function<MCPToolRequest, String> connectorTypeFn, String operation) {
    this.client = client;
    this.connectorTypeFn = connectorTypeFn;
    this.operation = operation;
  }

  /**
   * Intercepts an MCP tool call with policy enforcement.
   *
   * <p>Flow:
   *
   * <ol>
   *   <li>Derive connector type from the request
   *   <li>Build a statement string and call {@code mcpCheckInput}
   *   <li>If blocked, throw {@link PolicyViolationException}
   *   <li>Execute the handler
   *   <li>Call {@code mcpCheckOutput} with the result
   *   <li>If blocked, throw {@link PolicyViolationException}
   *   <li>If redacted data is present, return it instead of the raw result
   * </ol>
   *
   * @param request the MCP tool request
   * @param handler the downstream handler that executes the actual tool call
   * @return the tool result, or redacted data if redaction policies are active
   * @throws PolicyViolationException if input or output is blocked by policy
   * @throws Exception if the handler throws
   */
  public Object intercept(MCPToolRequest request, MCPToolHandler handler) throws Exception {
    String connectorType = connectorTypeFn.apply(request);
    String argsStr = serializeArgs(request.getArgs());
    String statement = connectorType + "(" + argsStr + ")";

    // Pre-check: validate input
    Map<String, Object> inputOptions = new HashMap<>();
    inputOptions.put("operation", operation);
    if (request.getArgs() != null && !request.getArgs().isEmpty()) {
      inputOptions.put("parameters", request.getArgs());
    }

    MCPCheckInputResponse preCheck = client.mcpCheckInput(connectorType, statement, inputOptions);
    if (!preCheck.isAllowed()) {
      String reason =
          preCheck.getBlockReason() != null
              ? preCheck.getBlockReason()
              : "Tool call blocked by policy";
      throw new PolicyViolationException(reason);
    }

    // Execute the tool
    Object result = handler.handle(request);

    // Post-check: validate output
    String resultStr;
    try {
      resultStr = OBJECT_MAPPER.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      resultStr = String.valueOf(result);
    }

    Map<String, Object> outputOptions = new HashMap<>();
    outputOptions.put("message", resultStr);

    MCPCheckOutputResponse outputCheck = client.mcpCheckOutput(connectorType, null, outputOptions);
    if (!outputCheck.isAllowed()) {
      String reason =
          outputCheck.getBlockReason() != null
              ? outputCheck.getBlockReason()
              : "Tool result blocked by policy";
      throw new PolicyViolationException(reason);
    }

    if (outputCheck.getRedactedData() != null) {
      return outputCheck.getRedactedData();
    }

    return result;
  }

  private static String serializeArgs(Map<String, Object> args) {
    if (args == null || args.isEmpty()) {
      return "{}";
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(args);
    } catch (JsonProcessingException e) {
      return args.toString();
    }
  }
}
