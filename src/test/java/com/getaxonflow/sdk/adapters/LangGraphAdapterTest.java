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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApprovalStatus;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.GateDecision;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.MarkStepCompletedRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepType;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowSource;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatus;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStepInfo;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LangGraphAdapter")
@ExtendWith(MockitoExtension.class)
class LangGraphAdapterTest {

  @Mock private AxonFlow client;

  private LangGraphAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = LangGraphAdapter.builder(client, "test-workflow").build();
  }

  // ========================================================================
  // Builder
  // ========================================================================

  @Nested
  @DisplayName("Builder")
  class BuilderTests {

    @Test
    @DisplayName("should use default source and autoBlock")
    void shouldUseDefaults() {
      LangGraphAdapter a = LangGraphAdapter.builder(client, "my-wf").build();
      assertThat(a.getWorkflowId()).isNull();
    }

    @Test
    @DisplayName("should accept custom source")
    void shouldAcceptCustomSource() {
      // Verify the adapter can be built with custom source without error
      LangGraphAdapter a =
          LangGraphAdapter.builder(client, "wf").source(WorkflowSource.LANGCHAIN).build();
      assertThat(a).isNotNull();
    }

    @Test
    @DisplayName("should reject null client")
    void shouldRejectNullClient() {
      assertThatThrownBy(() -> LangGraphAdapter.builder(null, "wf"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("client");
    }

    @Test
    @DisplayName("should reject null workflowName")
    void shouldRejectNullWorkflowName() {
      assertThatThrownBy(() -> LangGraphAdapter.builder(client, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("workflowName");
    }
  }

  // ========================================================================
  // startWorkflow
  // ========================================================================

  @Nested
  @DisplayName("startWorkflow")
  class StartWorkflowTests {

    @Test
    @DisplayName("should create workflow and store ID")
    void shouldCreateWorkflowAndStoreId() {
      mockCreateWorkflow("wf-123");

      String id = adapter.startWorkflow();

      assertThat(id).isEqualTo("wf-123");
      assertThat(adapter.getWorkflowId()).isEqualTo("wf-123");
    }

    @Test
    @DisplayName("should pass metadata and traceId")
    void shouldPassMetadataAndTraceId() {
      mockCreateWorkflow("wf-456");

      Map<String, Object> meta = Map.of("customer", "cust-1");
      adapter.startWorkflow(meta, "trace-abc");

      ArgumentCaptor<CreateWorkflowRequest> captor =
          ArgumentCaptor.forClass(CreateWorkflowRequest.class);
      verify(client).createWorkflow(captor.capture());

      CreateWorkflowRequest req = captor.getValue();
      assertThat(req.getWorkflowName()).isEqualTo("test-workflow");
      assertThat(req.getSource()).isEqualTo(WorkflowSource.LANGGRAPH);
      assertThat(req.getMetadata()).containsEntry("customer", "cust-1");
      assertThat(req.getTraceId()).isEqualTo("trace-abc");
    }
  }

  // ========================================================================
  // checkGate
  // ========================================================================

  @Nested
  @DisplayName("checkGate")
  class CheckGateTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-1");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should return true when allowed")
    void shouldReturnTrueWhenAllowed() {
      mockStepGate(GateDecision.ALLOW);

      boolean result = adapter.checkGate("generate", "llm_call");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should throw WorkflowBlockedError when blocked and autoBlock=true")
    void shouldThrowOnBlockWithAutoBlock() {
      mockStepGate(GateDecision.BLOCK, "step-x", "Policy violation", List.of("pol-1"));

      assertThatThrownBy(() -> adapter.checkGate("generate", "llm_call"))
          .isInstanceOf(WorkflowBlockedError.class)
          .hasMessageContaining("generate")
          .hasMessageContaining("blocked");
    }

    @Test
    @DisplayName("should return false when blocked and autoBlock=false")
    void shouldReturnFalseOnBlockWithoutAutoBlock() {
      LangGraphAdapter noBlock = LangGraphAdapter.builder(client, "wf").autoBlock(false).build();
      mockCreateWorkflow("wf-nb");
      noBlock.startWorkflow();

      mockStepGate(GateDecision.BLOCK);

      boolean result = noBlock.checkGate("generate", "llm_call");
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should throw WorkflowApprovalRequiredError on require_approval")
    void shouldThrowOnApprovalRequired() {
      StepGateResponse resp =
          new StepGateResponse(
              GateDecision.REQUIRE_APPROVAL,
              "step-approval",
              "Needs review",
              Collections.emptyList(),
              "https://approve.me",
              null,
              null,
              false,
              "fresh");
      when(client.stepGate(anyString(), anyString(), any(StepGateRequest.class))).thenReturn(resp);

      assertThatThrownBy(() -> adapter.checkGate("deploy", "human_task"))
          .isInstanceOf(WorkflowApprovalRequiredError.class)
          .satisfies(
              ex -> {
                WorkflowApprovalRequiredError err = (WorkflowApprovalRequiredError) ex;
                assertThat(err.getStepId()).isEqualTo("step-approval");
                assertThat(err.getApprovalUrl()).isEqualTo("https://approve.me");
                assertThat(err.getReason()).isEqualTo("Needs review");
              });
    }

    @Test
    @DisplayName("should throw IllegalStateException when workflow not started")
    void shouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();

      assertThatThrownBy(() -> fresh.checkGate("step", "llm_call"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not started");
    }

    @Test
    @DisplayName("should pass model and provider from options")
    void shouldPassModelAndProvider() {
      mockStepGate(GateDecision.ALLOW);

      CheckGateOptions opts =
          CheckGateOptions.builder()
              .model("gpt-4")
              .provider("openai")
              .stepInput(Map.of("prompt", "hello"))
              .build();

      adapter.checkGate("generate", "llm_call", opts);

      ArgumentCaptor<StepGateRequest> captor = ArgumentCaptor.forClass(StepGateRequest.class);
      verify(client).stepGate(eq("wf-1"), anyString(), captor.capture());

      StepGateRequest req = captor.getValue();
      assertThat(req.getModel()).isEqualTo("gpt-4");
      assertThat(req.getProvider()).isEqualTo("openai");
      assertThat(req.getStepInput()).containsEntry("prompt", "hello");
    }

    @Test
    @DisplayName("should use custom stepId from options")
    void shouldUseCustomStepId() {
      mockStepGate(GateDecision.ALLOW);

      CheckGateOptions opts = CheckGateOptions.builder().stepId("my-custom-step").build();

      adapter.checkGate("generate", "llm_call", opts);

      verify(client).stepGate(eq("wf-1"), eq("my-custom-step"), any(StepGateRequest.class));
    }

    @Test
    @DisplayName("WorkflowBlockedError should contain policy details")
    void blockedErrorShouldContainDetails() {
      mockStepGate(
          GateDecision.BLOCK,
          "step-blocked-1",
          "Cost limit exceeded",
          List.of("cost-policy", "budget-policy"));

      assertThatThrownBy(() -> adapter.checkGate("expensive", "llm_call"))
          .isInstanceOf(WorkflowBlockedError.class)
          .satisfies(
              ex -> {
                WorkflowBlockedError err = (WorkflowBlockedError) ex;
                assertThat(err.getStepId()).isEqualTo("step-blocked-1");
                assertThat(err.getReason()).isEqualTo("Cost limit exceeded");
                assertThat(err.getPolicyIds()).containsExactly("cost-policy", "budget-policy");
              });
    }
  }

  // ========================================================================
  // stepCompleted
  // ========================================================================

  @Nested
  @DisplayName("stepCompleted")
  class StepCompletedTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-sc");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should call markStepCompleted with matching step ID")
    void shouldCallMarkStepCompleted() {
      mockStepGate(GateDecision.ALLOW);
      adapter.checkGate("analyze", "llm_call");

      adapter.stepCompleted("analyze");

      // Step counter is 1 after first checkGate, so step ID should be step-1-analyze
      verify(client)
          .markStepCompleted(
              eq("wf-sc"), eq("step-1-analyze"), any(MarkStepCompletedRequest.class));
    }

    @Test
    @DisplayName("should pass output and metadata from options")
    void shouldPassOptions() {
      mockStepGate(GateDecision.ALLOW);
      adapter.checkGate("generate", "llm_call");

      StepCompletedOptions opts =
          StepCompletedOptions.builder()
              .output(Map.of("code", "result"))
              .metadata(Map.of("key", "val"))
              .tokensIn(100)
              .tokensOut(200)
              .costUsd(0.05)
              .build();

      adapter.stepCompleted("generate", opts);

      ArgumentCaptor<MarkStepCompletedRequest> captor =
          ArgumentCaptor.forClass(MarkStepCompletedRequest.class);
      verify(client).markStepCompleted(eq("wf-sc"), anyString(), captor.capture());

      MarkStepCompletedRequest req = captor.getValue();
      assertThat(req.getOutput()).containsEntry("code", "result");
      assertThat(req.getMetadata()).containsEntry("key", "val");
      assertThat(req.getTokensIn()).isEqualTo(100);
      assertThat(req.getTokensOut()).isEqualTo(200);
      assertThat(req.getCostUsd()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("should throw IllegalStateException when workflow not started")
    void shouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();

      assertThatThrownBy(() -> fresh.stepCompleted("step"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not started");
    }

    @Test
    @DisplayName("should use custom stepId from options")
    void shouldUseCustomStepId() {
      mockStepGate(GateDecision.ALLOW);
      adapter.checkGate("generate", "llm_call");

      StepCompletedOptions opts = StepCompletedOptions.builder().stepId("custom-id").build();

      adapter.stepCompleted("generate", opts);

      verify(client)
          .markStepCompleted(eq("wf-sc"), eq("custom-id"), any(MarkStepCompletedRequest.class));
    }
  }

  // ========================================================================
  // checkToolGate
  // ========================================================================

  @Nested
  @DisplayName("checkToolGate")
  class CheckToolGateTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-tg");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should use default step name tools/{toolName}")
    void shouldUseDefaultStepName() {
      mockStepGate(GateDecision.ALLOW);

      adapter.checkToolGate("web_search", "function");

      ArgumentCaptor<StepGateRequest> captor = ArgumentCaptor.forClass(StepGateRequest.class);
      verify(client).stepGate(eq("wf-tg"), anyString(), captor.capture());

      StepGateRequest req = captor.getValue();
      assertThat(req.getStepName()).isEqualTo("tools/web_search");
      assertThat(req.getStepType()).isEqualTo(StepType.TOOL_CALL);
      assertThat(req.getToolContext()).isNotNull();
      assertThat(req.getToolContext().getToolName()).isEqualTo("web_search");
      assertThat(req.getToolContext().getToolType()).isEqualTo("function");
    }

    @Test
    @DisplayName("should use custom step name from options")
    void shouldUseCustomStepName() {
      mockStepGate(GateDecision.ALLOW);

      CheckToolGateOptions opts =
          CheckToolGateOptions.builder()
              .stepName("custom-tools/search")
              .toolInput(Map.of("query", "test"))
              .build();

      adapter.checkToolGate("web_search", "function", opts);

      ArgumentCaptor<StepGateRequest> captor = ArgumentCaptor.forClass(StepGateRequest.class);
      verify(client).stepGate(eq("wf-tg"), anyString(), captor.capture());

      StepGateRequest req = captor.getValue();
      assertThat(req.getStepName()).isEqualTo("custom-tools/search");
      assertThat(req.getToolContext().getToolInput()).containsEntry("query", "test");
    }
  }

  // ========================================================================
  // toolCompleted
  // ========================================================================

  @Nested
  @DisplayName("toolCompleted")
  class ToolCompletedTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-tc");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should delegate to stepCompleted with default step name")
    void shouldDelegateWithDefaultName() {
      mockStepGate(GateDecision.ALLOW);
      adapter.checkToolGate("calculator", "function");

      adapter.toolCompleted("calculator");

      // Step ID should match the one generated in checkToolGate (tools/calculator ->
      // tools-calculator)
      verify(client)
          .markStepCompleted(
              eq("wf-tc"), eq("step-1-tools-calculator"), any(MarkStepCompletedRequest.class));
    }

    @Test
    @DisplayName("should pass options through to stepCompleted")
    void shouldPassOptions() {
      mockStepGate(GateDecision.ALLOW);
      adapter.checkToolGate("calc", "function");

      ToolCompletedOptions opts =
          ToolCompletedOptions.builder()
              .output(Map.of("result", 42))
              .tokensIn(10)
              .tokensOut(20)
              .costUsd(0.001)
              .build();

      adapter.toolCompleted("calc", opts);

      ArgumentCaptor<MarkStepCompletedRequest> captor =
          ArgumentCaptor.forClass(MarkStepCompletedRequest.class);
      verify(client).markStepCompleted(eq("wf-tc"), anyString(), captor.capture());

      MarkStepCompletedRequest req = captor.getValue();
      assertThat(req.getTokensIn()).isEqualTo(10);
      assertThat(req.getTokensOut()).isEqualTo(20);
      assertThat(req.getCostUsd()).isEqualTo(0.001);
    }
  }

  // ========================================================================
  // Workflow Lifecycle (complete/abort/fail)
  // ========================================================================

  @Nested
  @DisplayName("Workflow Lifecycle")
  class LifecycleTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-lc");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("completeWorkflow should delegate to client")
    void completeWorkflowShouldDelegate() {
      adapter.completeWorkflow();
      verify(client).completeWorkflow("wf-lc");
    }

    @Test
    @DisplayName("abortWorkflow should delegate with reason")
    void abortWorkflowShouldDelegateWithReason() {
      adapter.abortWorkflow("user cancelled");
      verify(client).abortWorkflow("wf-lc", "user cancelled");
    }

    @Test
    @DisplayName("failWorkflow should delegate with reason")
    void failWorkflowShouldDelegateWithReason() {
      adapter.failWorkflow("pipeline error");
      verify(client).failWorkflow("wf-lc", "pipeline error");
    }

    @Test
    @DisplayName("completeWorkflow should throw when not started")
    void completeShouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();
      assertThatThrownBy(fresh::completeWorkflow).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("abortWorkflow should throw when not started")
    void abortShouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();
      assertThatThrownBy(() -> fresh.abortWorkflow("reason"))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("failWorkflow should throw when not started")
    void failShouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();
      assertThatThrownBy(() -> fresh.failWorkflow("reason"))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ========================================================================
  // Step Counter
  // ========================================================================

  @Nested
  @DisplayName("Step Counter")
  class StepCounterTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-cnt");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should increment step counter on each checkGate")
    void shouldIncrementCounter() {
      mockStepGate(GateDecision.ALLOW);

      adapter.checkGate("step-a", "llm_call");
      assertThat(adapter.getStepCounter()).isEqualTo(1);

      adapter.checkGate("step-b", "tool_call");
      assertThat(adapter.getStepCounter()).isEqualTo(2);

      adapter.checkGate("step-c", "llm_call");
      assertThat(adapter.getStepCounter()).isEqualTo(3);
    }

    @Test
    @DisplayName("should generate step IDs with counter and safe name")
    void shouldGenerateStepIds() {
      mockStepGate(GateDecision.ALLOW);

      adapter.checkGate("My Step", "llm_call");
      verify(client).stepGate(eq("wf-cnt"), eq("step-1-my-step"), any(StepGateRequest.class));

      adapter.checkGate("tools/search", "tool_call");
      verify(client).stepGate(eq("wf-cnt"), eq("step-2-tools-search"), any(StepGateRequest.class));
    }
  }

  // ========================================================================
  // waitForApproval
  // ========================================================================

  @Nested
  @DisplayName("waitForApproval")
  class WaitForApprovalTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-wa");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should return true when step is approved")
    void shouldReturnTrueOnApproval() throws Exception {
      WorkflowStepInfo approvedStep =
          new WorkflowStepInfo(
              "step-1",
              1,
              "deploy",
              StepType.HUMAN_TASK,
              GateDecision.REQUIRE_APPROVAL,
              null,
              ApprovalStatus.APPROVED,
              "admin",
              Instant.now(),
              null);

      WorkflowStatusResponse status =
          new WorkflowStatusResponse(
              "wf-wa",
              "wf",
              WorkflowSource.LANGGRAPH,
              WorkflowStatus.IN_PROGRESS,
              1,
              null,
              Instant.now(),
              null,
              List.of(approvedStep));

      when(client.getWorkflow("wf-wa")).thenReturn(status);

      boolean result = adapter.waitForApproval("step-1", 50, 5000);
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false when step is rejected")
    void shouldReturnFalseOnRejection() throws Exception {
      WorkflowStepInfo rejectedStep =
          new WorkflowStepInfo(
              "step-1",
              1,
              "deploy",
              StepType.HUMAN_TASK,
              GateDecision.REQUIRE_APPROVAL,
              null,
              ApprovalStatus.REJECTED,
              null,
              Instant.now(),
              null);

      WorkflowStatusResponse status =
          new WorkflowStatusResponse(
              "wf-wa",
              "wf",
              WorkflowSource.LANGGRAPH,
              WorkflowStatus.IN_PROGRESS,
              1,
              null,
              Instant.now(),
              null,
              List.of(rejectedStep));

      when(client.getWorkflow("wf-wa")).thenReturn(status);

      boolean result = adapter.waitForApproval("step-1", 50, 5000);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should throw TimeoutException when approval not received")
    void shouldThrowOnTimeout() {
      WorkflowStepInfo pendingStep =
          new WorkflowStepInfo(
              "step-1",
              1,
              "deploy",
              StepType.HUMAN_TASK,
              GateDecision.REQUIRE_APPROVAL,
              null,
              ApprovalStatus.PENDING,
              null,
              Instant.now(),
              null);

      WorkflowStatusResponse status =
          new WorkflowStatusResponse(
              "wf-wa",
              "wf",
              WorkflowSource.LANGGRAPH,
              WorkflowStatus.IN_PROGRESS,
              1,
              null,
              Instant.now(),
              null,
              List.of(pendingStep));

      when(client.getWorkflow("wf-wa")).thenReturn(status);

      assertThatThrownBy(() -> adapter.waitForApproval("step-1", 50, 120))
          .isInstanceOf(TimeoutException.class)
          .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("should throw IllegalStateException when not started")
    void shouldThrowWhenNotStarted() {
      LangGraphAdapter fresh = LangGraphAdapter.builder(client, "wf").build();
      assertThatThrownBy(() -> fresh.waitForApproval("step-1", 50, 1000))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ========================================================================
  // close()
  // ========================================================================

  @Nested
  @DisplayName("close")
  class CloseTests {

    @Test
    @DisplayName("should abort workflow if not closed normally")
    void shouldAbortIfNotClosedNormally() {
      mockCreateWorkflow("wf-close");
      adapter.startWorkflow();

      adapter.close();

      verify(client)
          .abortWorkflow(eq("wf-close"), eq("Adapter closed without explicit completion"));
    }

    @Test
    @DisplayName("should not abort if completeWorkflow was called")
    void shouldNotAbortIfCompleted() {
      mockCreateWorkflow("wf-close");
      adapter.startWorkflow();
      adapter.completeWorkflow();

      adapter.close();

      verify(client, never())
          .abortWorkflow(eq("wf-close"), eq("Adapter closed without explicit completion"));
    }

    @Test
    @DisplayName("should not abort if abortWorkflow was called")
    void shouldNotAbortIfAborted() {
      mockCreateWorkflow("wf-close");
      adapter.startWorkflow();
      adapter.abortWorkflow("manual");

      adapter.close();

      // abortWorkflow was called once (manual), not twice
      verify(client, times(1)).abortWorkflow(anyString(), anyString());
    }

    @Test
    @DisplayName("should not abort if failWorkflow was called")
    void shouldNotAbortIfFailed() {
      mockCreateWorkflow("wf-close");
      adapter.startWorkflow();
      adapter.failWorkflow("error");

      adapter.close();

      verify(client, never())
          .abortWorkflow(eq("wf-close"), eq("Adapter closed without explicit completion"));
    }

    @Test
    @DisplayName("should do nothing if workflow was never started")
    void shouldDoNothingIfNotStarted() {
      adapter.close();
      verify(client, never()).abortWorkflow(anyString(), anyString());
    }

    @Test
    @DisplayName("should swallow exceptions during close abort")
    void shouldSwallowExceptionsDuringCloseAbort() {
      mockCreateWorkflow("wf-close-err");
      adapter.startWorkflow();

      doThrow(new RuntimeException("network error"))
          .when(client)
          .abortWorkflow(anyString(), anyString());

      // Should not throw
      adapter.close();
    }
  }

  // ========================================================================
  // MCPToolInterceptor
  // ========================================================================

  @Nested
  @DisplayName("MCPToolInterceptor")
  class MCPToolInterceptorTests {

    @BeforeEach
    void startWorkflow() {
      mockCreateWorkflow("wf-mcp");
      adapter.startWorkflow();
    }

    @Test
    @DisplayName("should pass through when input and output are allowed")
    void shouldPassThroughWhenAllowed() throws Exception {
      MCPCheckInputResponse inputOk = new MCPCheckInputResponse(true, null, 1, null);
      MCPCheckOutputResponse outputOk = new MCPCheckOutputResponse(true, null, null, 1, null, null);

      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(inputOk);
      when(client.mcpCheckOutput(anyString(), isNull(), any())).thenReturn(outputOk);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request = new MCPToolRequest("postgres", "query", Map.of("sql", "SELECT 1"));

      Object result = interceptor.intercept(request, req -> "result-data");

      assertThat(result).isEqualTo("result-data");
    }

    @Test
    @DisplayName("should throw PolicyViolationException when input is blocked")
    void shouldThrowOnBlockedInput() {
      MCPCheckInputResponse blocked = new MCPCheckInputResponse(false, "DROP not allowed", 1, null);
      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(blocked);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request =
          new MCPToolRequest("postgres", "execute", Map.of("sql", "DROP TABLE"));

      assertThatThrownBy(() -> interceptor.intercept(request, req -> "ignored"))
          .isInstanceOf(PolicyViolationException.class)
          .hasMessageContaining("DROP not allowed");
    }

    @Test
    @DisplayName("should throw PolicyViolationException when output is blocked")
    void shouldThrowOnBlockedOutput() throws Exception {
      MCPCheckInputResponse inputOk = new MCPCheckInputResponse(true, null, 1, null);
      MCPCheckOutputResponse outputBlocked =
          new MCPCheckOutputResponse(false, "PII detected", null, 1, null, null);

      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(inputOk);
      when(client.mcpCheckOutput(anyString(), isNull(), any())).thenReturn(outputBlocked);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request = new MCPToolRequest("postgres", "query", null);

      assertThatThrownBy(() -> interceptor.intercept(request, req -> "sensitive-data"))
          .isInstanceOf(PolicyViolationException.class)
          .hasMessageContaining("PII detected");
    }

    @Test
    @DisplayName("should return redacted data when available")
    void shouldReturnRedactedData() throws Exception {
      MCPCheckInputResponse inputOk = new MCPCheckInputResponse(true, null, 1, null);
      MCPCheckOutputResponse redacted =
          new MCPCheckOutputResponse(true, null, "***REDACTED***", 1, null, null);

      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(inputOk);
      when(client.mcpCheckOutput(anyString(), isNull(), any())).thenReturn(redacted);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request = new MCPToolRequest("db", "query", Map.of("q", "SELECT *"));

      Object result = interceptor.intercept(request, req -> "raw-data-with-pii");

      assertThat(result).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("should use default connector type serverName.toolName")
    void shouldUseDefaultConnectorType() throws Exception {
      MCPCheckInputResponse inputOk = new MCPCheckInputResponse(true, null, 1, null);
      MCPCheckOutputResponse outputOk = new MCPCheckOutputResponse(true, null, null, 1, null, null);

      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(inputOk);
      when(client.mcpCheckOutput(anyString(), isNull(), any())).thenReturn(outputOk);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request = new MCPToolRequest("myserver", "mytool", Collections.emptyMap());

      interceptor.intercept(request, req -> "ok");

      verify(client).mcpCheckInput(eq("myserver.mytool"), anyString(), any());
      verify(client).mcpCheckOutput(eq("myserver.mytool"), isNull(), any());
    }

    @Test
    @DisplayName("should use custom connector type function")
    void shouldUseCustomConnectorTypeFn() throws Exception {
      MCPCheckInputResponse inputOk = new MCPCheckInputResponse(true, null, 1, null);
      MCPCheckOutputResponse outputOk = new MCPCheckOutputResponse(true, null, null, 1, null, null);

      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(inputOk);
      when(client.mcpCheckOutput(anyString(), isNull(), any())).thenReturn(outputOk);

      MCPInterceptorOptions opts =
          MCPInterceptorOptions.builder()
              .connectorTypeFn(req -> "custom-" + req.getServerName())
              .operation("query")
              .build();

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor(opts);
      MCPToolRequest request = new MCPToolRequest("pg", "read", null);

      interceptor.intercept(request, req -> "data");

      ArgumentCaptor<Map<String, Object>> inputOptsCaptor = ArgumentCaptor.forClass(Map.class);
      verify(client).mcpCheckInput(eq("custom-pg"), anyString(), inputOptsCaptor.capture());
      assertThat(inputOptsCaptor.getValue()).containsEntry("operation", "query");
    }

    @Test
    @DisplayName("should not call handler when input is blocked")
    void shouldNotCallHandlerWhenInputBlocked() {
      MCPCheckInputResponse blocked = new MCPCheckInputResponse(false, "Blocked", 1, null);
      when(client.mcpCheckInput(anyString(), anyString(), any())).thenReturn(blocked);

      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();
      MCPToolRequest request = new MCPToolRequest("srv", "tool", null);

      MCPToolHandler handler =
          req -> {
            throw new AssertionError("Handler should not be called");
          };

      assertThatThrownBy(() -> interceptor.intercept(request, handler))
          .isInstanceOf(PolicyViolationException.class);
    }
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private void mockCreateWorkflow(String workflowId) {
    CreateWorkflowResponse resp =
        new CreateWorkflowResponse(
            workflowId,
            "test-workflow",
            WorkflowSource.LANGGRAPH,
            WorkflowStatus.IN_PROGRESS,
            Instant.now());
    when(client.createWorkflow(any(CreateWorkflowRequest.class))).thenReturn(resp);
  }

  private void mockStepGate(GateDecision decision) {
    mockStepGate(decision, null, null, Collections.emptyList());
  }

  private void mockStepGate(
      GateDecision decision, String stepId, String reason, List<String> policyIds) {
    StepGateResponse resp =
        new StepGateResponse(decision, stepId, reason, policyIds, null, null, null, false, "fresh");
    when(client.stepGate(anyString(), anyString(), any(StepGateRequest.class))).thenReturn(resp);
  }
}
