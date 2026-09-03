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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernedToolTest {

  @Mock private AxonFlow client;

  private MCPCheckInputResponse allowedInput;
  private MCPCheckInputResponse blockedInput;
  private MCPCheckOutputResponse allowedOutput;
  private MCPCheckOutputResponse blockedOutput;
  private MCPCheckOutputResponse redactedOutput;

  @BeforeEach
  void setUp() {
    allowedInput = new MCPCheckInputResponse(true, null, 3, null);
    blockedInput = new MCPCheckInputResponse(false, "Dangerous SQL detected", 3, null);
    allowedOutput = new MCPCheckOutputResponse(true, null, null, 2, null, null);
    blockedOutput =
        new MCPCheckOutputResponse(false, "PII detected in output", null, 2, null, null);
    redactedOutput = new MCPCheckOutputResponse(true, null, "[REDACTED:ssn] data", 2, null, null);
  }

  /** Creates a simple mock tool that returns the given result. */
  private Tool mockTool(String name, Object result) {
    return new Tool() {
      boolean invoked = false;

      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "Mock " + name + " tool";
      }

      @Override
      public Object invoke(Object input) {
        invoked = true;
        return result;
      }
    };
  }

  /** Creates a mock tool that tracks whether it was invoked. */
  private TrackableTool trackableTool(String name, Object result) {
    return new TrackableTool(name, result);
  }

  @Test
  void cleanCallAllowed() throws Exception {
    when(client.mcpCheckInput(eq("web_search"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("web_search"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("web_search", "search results");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    Object result = governed.invoke("latest AI research");

    assertThat(result).isEqualTo("search results");
    verify(client).mcpCheckInput(eq("web_search"), eq("latest AI research"));
    verify(client).mcpCheckOutput(eq("web_search"), isNull(), any());
  }

  @Test
  void inputBlocked() {
    when(client.mcpCheckInput(eq("db_query"), any())).thenReturn(blockedInput);

    TrackableTool tool = trackableTool("db_query", "should not see this");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThatThrownBy(() -> governed.invoke("DROP TABLE users"))
        .isInstanceOf(PolicyViolationException.class)
        .hasMessageContaining("Dangerous SQL detected");

    assertThat(tool.wasInvoked()).isFalse();
    verify(client, never()).mcpCheckOutput(any(), any(), any());
  }

  @Test
  void outputBlocked() {
    when(client.mcpCheckInput(eq("db_query"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("db_query"), isNull(), any())).thenReturn(blockedOutput);

    TrackableTool tool = trackableTool("db_query", "John Doe, SSN: 123-45-6789");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThatThrownBy(() -> governed.invoke("SELECT * FROM users"))
        .isInstanceOf(PolicyViolationException.class)
        .hasMessageContaining("PII detected in output");

    assertThat(tool.wasInvoked()).isTrue();
  }

  @Test
  void outputRedacted() throws Exception {
    when(client.mcpCheckInput(eq("db_query"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("db_query"), isNull(), any())).thenReturn(redactedOutput);

    Tool tool = mockTool("db_query", "John Doe, SSN: 123-45-6789");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    Object result = governed.invoke("SELECT * FROM users");

    assertThat(result).isEqualTo("[REDACTED:ssn] data");
  }

  @Test
  void customConnectorTypeFn() throws Exception {
    when(client.mcpCheckInput(eq("custom.web_search"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("custom.web_search"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("web_search", "results");
    GovernedTool governed =
        GovernedTool.builder(tool, client).connectorTypeFn(name -> "custom." + name).build();

    Object result = governed.invoke("query");

    assertThat(result).isEqualTo("results");
    verify(client).mcpCheckInput(eq("custom.web_search"), any());
    verify(client).mcpCheckOutput(eq("custom.web_search"), isNull(), any());
  }

  @Test
  void customOperation() throws Exception {
    when(client.mcpCheckInput(eq("db_query"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("db_query"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("db_query", "rows");
    GovernedTool governed = GovernedTool.builder(tool, client).operation("query").build();

    Object result = governed.invoke("SELECT 1");

    assertThat(result).isEqualTo("rows");
  }

  @Test
  void governToolsBatch() throws Exception {
    when(client.mcpCheckInput(any(), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(any(), isNull(), any())).thenReturn(allowedOutput);

    Tool tool1 = mockTool("search", "result1");
    Tool tool2 = mockTool("calculator", "result2");
    Tool tool3 = mockTool("email", "result3");

    List<GovernedTool> governed =
        GovernedTool.governTools(Arrays.asList(tool1, tool2, tool3), client);

    assertThat(governed).hasSize(3);
    assertThat(governed.get(0).name()).isEqualTo("search");
    assertThat(governed.get(1).name()).isEqualTo("calculator");
    assertThat(governed.get(2).name()).isEqualTo("email");

    // Invoke each to verify they work
    assertThat(governed.get(0).invoke("q")).isEqualTo("result1");
    assertThat(governed.get(1).invoke("1+1")).isEqualTo("result2");
    assertThat(governed.get(2).invoke("send")).isEqualTo("result3");
  }

  @Test
  void governToolsBatchWithCustomOptions() throws Exception {
    when(client.mcpCheckInput(any(), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(any(), isNull(), any())).thenReturn(allowedOutput);

    Tool tool1 = mockTool("search", "result1");
    Tool tool2 = mockTool("calculator", "result2");

    List<GovernedTool> governed =
        GovernedTool.governTools(
            Arrays.asList(tool1, tool2), client, name -> "ns." + name, "query");

    assertThat(governed).hasSize(2);

    governed.get(0).invoke("q");
    verify(client).mcpCheckInput(eq("ns.search"), any());

    governed.get(1).invoke("1+1");
    verify(client).mcpCheckInput(eq("ns.calculator"), any());
  }

  @Test
  void stringInputPassthrough() throws Exception {
    when(client.mcpCheckInput(eq("tool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("tool"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("tool", "ok");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    governed.invoke("plain string input");

    // String should be passed through directly, not JSON-encoded
    verify(client).mcpCheckInput(eq("tool"), eq("plain string input"));
  }

  @Test
  void objectInputSerialized() throws Exception {
    when(client.mcpCheckInput(eq("tool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("tool"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("tool", "ok");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    Map<String, Object> input = Map.of("query", "test", "limit", 10);
    governed.invoke(input);

    // Object should be JSON-serialized
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(client).mcpCheckInput(eq("tool"), captor.capture());
    String serialized = captor.getValue();
    assertThat(serialized).contains("\"query\"");
    assertThat(serialized).contains("\"test\"");
    assertThat(serialized).contains("\"limit\"");
    assertThat(serialized).contains("10");
  }

  @Test
  void builderPattern() throws Exception {
    when(client.mcpCheckInput(eq("custom.myTool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("custom.myTool"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("myTool", "built result");
    GovernedTool governed =
        GovernedTool.builder(tool, client)
            .connectorTypeFn(name -> "custom." + name)
            .operation("query")
            .build();

    Object result = governed.invoke("input");

    assertThat(result).isEqualTo("built result");
    assertThat(governed.name()).isEqualTo("myTool");
    assertThat(governed.description()).isEqualTo("Mock myTool tool");
  }

  @Test
  void wrapFactory() throws Exception {
    when(client.mcpCheckInput(eq("simpleTool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("simpleTool"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("simpleTool", "wrapped result");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThat(governed.name()).isEqualTo("simpleTool");
    assertThat(governed.description()).isEqualTo("Mock simpleTool tool");

    Object result = governed.invoke("test");
    assertThat(result).isEqualTo("wrapped result");
  }

  @Test
  void toStringFormat() {
    Tool tool = mockTool("myTool", null);
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThat(governed.toString()).isEqualTo("GovernedTool(name=myTool, connectorType=myTool)");
  }

  @Test
  void toStringFormatWithCustomConnector() {
    Tool tool = mockTool("myTool", null);
    GovernedTool governed =
        GovernedTool.builder(tool, client).connectorTypeFn(name -> "ns." + name).build();

    assertThat(governed.toString()).isEqualTo("GovernedTool(name=myTool, connectorType=ns.myTool)");
  }

  @Test
  void outputCheckReceivesMessageInOptions() throws Exception {
    when(client.mcpCheckInput(eq("tool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("tool"), isNull(), any())).thenReturn(allowedOutput);

    Tool tool = mockTool("tool", "tool output data");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    governed.invoke("input");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client).mcpCheckOutput(eq("tool"), isNull(), optionsCaptor.capture());

    Map<String, Object> capturedOptions = optionsCaptor.getValue();
    assertThat(capturedOptions).containsKey("message");
    assertThat(capturedOptions.get("message")).isEqualTo("tool output data");
  }

  @Test
  void inputBlockedWithNullReason() {
    MCPCheckInputResponse blockedNoReason = new MCPCheckInputResponse(false, null, 1, null);
    when(client.mcpCheckInput(eq("tool"), any())).thenReturn(blockedNoReason);

    Tool tool = mockTool("tool", "should not run");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThatThrownBy(() -> governed.invoke("input"))
        .isInstanceOf(PolicyViolationException.class)
        .hasMessageContaining("Tool call blocked by input policy");
  }

  @Test
  void outputBlockedWithNullReason() {
    MCPCheckOutputResponse blockedNoReason =
        new MCPCheckOutputResponse(false, null, null, 1, null, null);
    when(client.mcpCheckInput(eq("tool"), any())).thenReturn(allowedInput);
    when(client.mcpCheckOutput(eq("tool"), isNull(), any())).thenReturn(blockedNoReason);

    Tool tool = mockTool("tool", "result");
    GovernedTool governed = GovernedTool.wrap(tool, client);

    assertThatThrownBy(() -> governed.invoke("input"))
        .isInstanceOf(PolicyViolationException.class)
        .hasMessageContaining("Tool output blocked by policy");
  }

  @Test
  void nullToolThrows() {
    assertThatThrownBy(() -> GovernedTool.wrap(null, client))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tool cannot be null");
  }

  @Test
  void nullClientThrows() {
    Tool tool = mockTool("tool", null);
    assertThatThrownBy(() -> GovernedTool.wrap(tool, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("client cannot be null");
  }

  /** Helper class that tracks invocation state. */
  private static class TrackableTool implements Tool {
    private final String toolName;
    private final Object result;
    private boolean invoked;

    TrackableTool(String name, Object result) {
      this.toolName = name;
      this.result = result;
    }

    @Override
    public String name() {
      return toolName;
    }

    @Override
    public String description() {
      return "Trackable " + toolName + " tool";
    }

    @Override
    public Object invoke(Object input) {
      invoked = true;
      return result;
    }

    boolean wasInvoked() {
      return invoked;
    }
  }
}
