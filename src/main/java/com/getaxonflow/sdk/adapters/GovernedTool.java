// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Wraps a {@link Tool} with AxonFlow input/output policy enforcement.
 *
 * <p>Every {@link #invoke} call runs through two policy checks:
 *
 * <ol>
 *   <li><strong>Input check</strong> ({@code mcpCheckInput}): evaluates tool arguments before
 *       execution. Blocked calls throw {@link PolicyViolationException} and the tool never runs.
 *   <li><strong>Output check</strong> ({@code mcpCheckOutput}): evaluates tool results after
 *       execution. Can block (throw), redact (return cleaned data), or allow.
 * </ol>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Simple wrapping
 * GovernedTool governed = GovernedTool.wrap(myTool, axonflow);
 * Object result = governed.invoke("SELECT * FROM users");
 *
 * // Builder pattern with custom connector type
 * GovernedTool governed = GovernedTool.builder(myTool, axonflow)
 *     .connectorTypeFn(name -> "custom." + name)
 *     .operation("query")
 *     .build();
 *
 * // Batch wrapping
 * List<GovernedTool> governed = GovernedTool.governTools(tools, axonflow);
 * }</pre>
 */
public final class GovernedTool implements Tool {

  private final Tool wrapped;
  private final AxonFlow client;
  private final String connectorType;
  private final String operation;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GovernedTool(Tool tool, AxonFlow client, String connectorType, String operation) {
    this.wrapped = Objects.requireNonNull(tool, "tool cannot be null");
    this.client = Objects.requireNonNull(client, "client cannot be null");
    this.connectorType = Objects.requireNonNull(connectorType, "connectorType cannot be null");
    this.operation = Objects.requireNonNull(operation, "operation cannot be null");
  }

  /**
   * Wraps a tool with AxonFlow governance using default settings.
   *
   * <p>Uses the tool's {@link Tool#name()} as the connector type and {@code "execute"} as the
   * operation.
   *
   * @param tool the tool to wrap
   * @param client the AxonFlow client
   * @return a governed tool
   */
  public static GovernedTool wrap(Tool tool, AxonFlow client) {
    Objects.requireNonNull(tool, "tool cannot be null");
    return new GovernedTool(tool, client, tool.name(), "execute");
  }

  /**
   * Creates a builder for a GovernedTool with custom options.
   *
   * @param tool the tool to wrap
   * @param client the AxonFlow client
   * @return a new builder
   */
  public static Builder builder(Tool tool, AxonFlow client) {
    return new Builder(tool, client);
  }

  /**
   * Wraps a list of tools with AxonFlow governance using default settings.
   *
   * @param tools the tools to wrap
   * @param client the AxonFlow client
   * @return list of governed tools
   */
  public static List<GovernedTool> governTools(List<Tool> tools, AxonFlow client) {
    return governTools(tools, client, null, "execute");
  }

  /**
   * Wraps a list of tools with AxonFlow governance using custom settings.
   *
   * @param tools the tools to wrap
   * @param client the AxonFlow client
   * @param connectorTypeFn optional function mapping tool name to connector type (null for default)
   * @param operation the operation type ({@code "execute"} or {@code "query"})
   * @return list of governed tools
   */
  public static List<GovernedTool> governTools(
      List<Tool> tools,
      AxonFlow client,
      Function<String, String> connectorTypeFn,
      String operation) {
    Objects.requireNonNull(tools, "tools cannot be null");
    Objects.requireNonNull(client, "client cannot be null");

    List<GovernedTool> result = new ArrayList<>(tools.size());
    for (Tool tool : tools) {
      String ct = connectorTypeFn != null ? connectorTypeFn.apply(tool.name()) : tool.name();
      result.add(new GovernedTool(tool, client, ct, operation != null ? operation : "execute"));
    }
    return result;
  }

  @Override
  public String name() {
    return wrapped.name();
  }

  @Override
  public String description() {
    return wrapped.description();
  }

  /**
   * Invokes the wrapped tool with AxonFlow input/output governance.
   *
   * <ol>
   *   <li>Serializes the input (strings pass through, objects are JSON-serialized)
   *   <li>Checks input against policies via {@code mcpCheckInput}
   *   <li>If blocked, throws {@link PolicyViolationException} without running the tool
   *   <li>Invokes the wrapped tool
   *   <li>Serializes the output and checks it against policies via {@code mcpCheckOutput}
   *   <li>If blocked, throws {@link PolicyViolationException}
   *   <li>If redacted, returns the redacted data
   *   <li>Otherwise, returns the original result
   * </ol>
   *
   * @param input the tool input
   * @return the tool result (possibly redacted)
   * @throws PolicyViolationException if the input or output violates a policy
   * @throws Exception if the tool invocation fails
   */
  @Override
  public Object invoke(Object input) throws Exception {
    // 1. Serialize input
    String serialized = serializeContent(input);

    // 2. Check input policy
    MCPCheckInputResponse inputCheck = client.mcpCheckInput(connectorType, serialized);

    // 3. If blocked, throw
    if (!inputCheck.isAllowed()) {
      throw new PolicyViolationException(
          inputCheck.getBlockReason() != null
              ? inputCheck.getBlockReason()
              : "Tool call blocked by input policy");
    }

    // 4. Invoke wrapped tool
    Object result = wrapped.invoke(input);

    // 5. Serialize output and check
    String serializedResult = serializeContent(result);
    Map<String, Object> options = new HashMap<>();
    options.put("message", serializedResult);
    MCPCheckOutputResponse outputCheck = client.mcpCheckOutput(connectorType, null, options);

    // 6. If blocked, throw
    if (!outputCheck.isAllowed()) {
      throw new PolicyViolationException(
          outputCheck.getBlockReason() != null
              ? outputCheck.getBlockReason()
              : "Tool output blocked by policy");
    }

    // 7. If redacted, return redacted data
    if (outputCheck.getRedactedData() != null) {
      return outputCheck.getRedactedData();
    }

    // 8. Return original
    return result;
  }

  @Override
  public String toString() {
    return String.format("GovernedTool(name=%s, connectorType=%s)", wrapped.name(), connectorType);
  }

  /**
   * Serializes content to a string for policy evaluation. Strings pass through directly; other
   * objects are JSON-serialized.
   */
  private static String serializeContent(Object content) {
    if (content instanceof String) {
      return (String) content;
    }
    try {
      return MAPPER.writeValueAsString(content);
    } catch (Exception e) {
      return String.valueOf(content);
    }
  }

  /** Builder for {@link GovernedTool}. */
  public static final class Builder {

    private final Tool tool;
    private final AxonFlow client;
    private Function<String, String> connectorTypeFn;
    private String operation = "execute";

    Builder(Tool tool, AxonFlow client) {
      this.tool = Objects.requireNonNull(tool, "tool cannot be null");
      this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    /**
     * Sets a function to derive the connector type from the tool name.
     *
     * <p>If not set, the tool's {@link Tool#name()} is used directly.
     *
     * @param fn the connector type function
     * @return this builder
     */
    public Builder connectorTypeFn(Function<String, String> fn) {
      this.connectorTypeFn = fn;
      return this;
    }

    /**
     * Sets the operation type for input checks.
     *
     * <p>Defaults to {@code "execute"}. Use {@code "query"} for read-only tools.
     *
     * @param operation the operation type
     * @return this builder
     */
    public Builder operation(String operation) {
      this.operation = Objects.requireNonNull(operation, "operation cannot be null");
      return this;
    }

    /**
     * Builds the governed tool.
     *
     * @return a new GovernedTool
     */
    public GovernedTool build() {
      String ct = connectorTypeFn != null ? connectorTypeFn.apply(tool.name()) : tool.name();
      return new GovernedTool(tool, client, ct, operation);
    }
  }
}
