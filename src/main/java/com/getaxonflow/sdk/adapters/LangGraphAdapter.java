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

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApprovalStatus;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.GateDecision;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.MarkStepCompletedRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepType;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.ToolContext;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowSource;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStepInfo;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Wraps LangGraph workflows with AxonFlow governance gates.
 *
 * <p>This adapter provides a simple interface for integrating AxonFlow's
 * Workflow Control Plane with LangGraph workflows. It handles workflow
 * registration, step gate checks, per-tool governance, MCP tool interception,
 * and workflow lifecycle management.
 *
 * <p><strong>Thread safety:</strong> This class is not thread-safe. Each workflow
 * execution should use its own adapter instance from a single thread.
 *
 * <p>"LangGraph runs the workflow. AxonFlow decides when it's allowed to move forward."
 *
 * <p>Example usage:
 * <pre>{@code
 * try (LangGraphAdapter adapter = LangGraphAdapter.builder(client, "code-review")
 *         .autoBlock(true)
 *         .build()) {
 *     adapter.startWorkflow();
 *
 *     if (adapter.checkGate("analyze", "llm_call")) {
 *         analyzeCode();
 *         adapter.stepCompleted("analyze");
 *     }
 *
 *     adapter.completeWorkflow();
 * }
 * }</pre>
 *
 * @see CheckGateOptions
 * @see MCPToolInterceptor
 */
public final class LangGraphAdapter implements AutoCloseable {

    private final AxonFlow client;
    private final String workflowName;
    private final WorkflowSource source;
    private final boolean autoBlock;
    private String workflowId;
    private int stepCounter;
    private boolean closedNormally;

    private LangGraphAdapter(Builder builder) {
        this.client = builder.client;
        this.workflowName = builder.workflowName;
        this.source = builder.source;
        this.autoBlock = builder.autoBlock;
    }

    /**
     * Creates a new builder for a LangGraphAdapter.
     *
     * @param client       the AxonFlow client instance
     * @param workflowName human-readable name for the workflow
     * @return a new builder
     */
    public static Builder builder(AxonFlow client, String workflowName) {
        return new Builder(client, workflowName);
    }

    // ========================================================================
    // Workflow Lifecycle
    // ========================================================================

    /**
     * Registers the workflow with AxonFlow.
     *
     * <p>Call this at the start of your LangGraph workflow execution.
     *
     * @param metadata additional workflow metadata (may be null)
     * @param traceId  external trace ID for correlation (may be null)
     * @return the assigned workflow ID
     */
    public String startWorkflow(Map<String, Object> metadata, String traceId) {
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                workflowName,
                source,
                metadata != null ? metadata : Collections.emptyMap(),
                traceId
        );

        CreateWorkflowResponse response = client.createWorkflow(request);
        this.workflowId = response.getWorkflowId();
        return this.workflowId;
    }

    /**
     * Registers the workflow with AxonFlow using default metadata.
     *
     * @return the assigned workflow ID
     */
    public String startWorkflow() {
        return startWorkflow(null, null);
    }

    /**
     * Marks the workflow as completed successfully.
     *
     * <p>Call this when your LangGraph workflow finishes all steps.
     *
     * @throws IllegalStateException if workflow not started
     */
    public void completeWorkflow() {
        requireStarted();
        client.completeWorkflow(workflowId);
        closedNormally = true;
    }

    /**
     * Aborts the workflow.
     *
     * @param reason the reason for aborting (may be null)
     * @throws IllegalStateException if workflow not started
     */
    public void abortWorkflow(String reason) {
        requireStarted();
        client.abortWorkflow(workflowId, reason);
        closedNormally = true;
    }

    /**
     * Fails the workflow.
     *
     * @param reason the reason for failing (may be null)
     * @throws IllegalStateException if workflow not started
     */
    public void failWorkflow(String reason) {
        requireStarted();
        client.failWorkflow(workflowId, reason);
        closedNormally = true;
    }

    // ========================================================================
    // Step Governance
    // ========================================================================

    /**
     * Checks if a step is allowed to proceed.
     *
     * <p>Call this before executing each LangGraph node.
     *
     * @param stepName human-readable step name
     * @param stepType type of step (llm_call, tool_call, connector_call, human_task)
     * @return true if allowed, false if blocked (when autoBlock is false)
     * @throws WorkflowBlockedError          if blocked and autoBlock is true
     * @throws WorkflowApprovalRequiredError  if approval is required
     * @throws IllegalStateException          if workflow not started
     */
    public boolean checkGate(String stepName, String stepType) {
        return checkGate(stepName, stepType, null);
    }

    /**
     * Checks if a step is allowed to proceed, with options.
     *
     * @param stepName human-readable step name
     * @param stepType type of step (llm_call, tool_call, connector_call, human_task)
     * @param options  additional options (may be null)
     * @return true if allowed, false if blocked (when autoBlock is false)
     * @throws WorkflowBlockedError          if blocked and autoBlock is true
     * @throws WorkflowApprovalRequiredError  if approval is required
     * @throws IllegalStateException          if workflow not started
     */
    public boolean checkGate(String stepName, String stepType, CheckGateOptions options) {
        requireStarted();

        StepType resolvedType = StepType.fromValue(stepType);

        // Generate or use provided step ID
        String stepId;
        if (options != null && options.getStepId() != null) {
            stepId = options.getStepId();
        } else {
            stepCounter++;
            String safeName = stepName.toLowerCase().replace(" ", "-").replace("/", "-");
            stepId = "step-" + stepCounter + "-" + safeName;
        }

        StepGateRequest request = new StepGateRequest(
                stepName,
                resolvedType,
                options != null && options.getStepInput() != null ? options.getStepInput() : Collections.emptyMap(),
                options != null ? options.getModel() : null,
                options != null ? options.getProvider() : null,
                options != null ? options.getToolContext() : null
        );

        StepGateResponse response = client.stepGate(workflowId, stepId, request);

        if (response.getDecision() == GateDecision.BLOCK) {
            if (autoBlock) {
                throw new WorkflowBlockedError(
                        "Step '" + stepName + "' blocked: " + response.getReason(),
                        response.getStepId(),
                        response.getReason(),
                        response.getPolicyIds()
                );
            }
            return false;
        }

        if (response.getDecision() == GateDecision.REQUIRE_APPROVAL) {
            throw new WorkflowApprovalRequiredError(
                    "Step '" + stepName + "' requires approval",
                    response.getStepId(),
                    response.getApprovalUrl(),
                    response.getReason()
            );
        }

        return true;
    }

    /**
     * Marks a step as completed.
     *
     * <p>Call this after successfully executing a LangGraph node.
     *
     * @param stepName the step name (used to derive step ID if not provided)
     * @throws IllegalStateException if workflow not started
     */
    public void stepCompleted(String stepName) {
        stepCompleted(stepName, null);
    }

    /**
     * Marks a step as completed, with options.
     *
     * @param stepName the step name
     * @param options  additional options (may be null)
     * @throws IllegalStateException if workflow not started
     */
    public void stepCompleted(String stepName, StepCompletedOptions options) {
        requireStarted();

        // Generate step ID matching the current counter (same as last checkGate)
        String stepId;
        if (options != null && options.getStepId() != null) {
            stepId = options.getStepId();
        } else {
            String safeName = stepName.toLowerCase().replace(" ", "-").replace("/", "-");
            stepId = "step-" + stepCounter + "-" + safeName;
        }

        MarkStepCompletedRequest request = new MarkStepCompletedRequest(
                options != null ? options.getOutput() : null,
                options != null ? options.getMetadata() : null,
                options != null ? options.getTokensIn() : null,
                options != null ? options.getTokensOut() : null,
                options != null ? options.getCostUsd() : null
        );

        client.markStepCompleted(workflowId, stepId, request);
    }

    // ========================================================================
    // Per-Tool Governance
    // ========================================================================

    /**
     * Checks if a specific tool invocation is allowed.
     *
     * <p>Convenience wrapper around {@link #checkGate} that creates a
     * {@link ToolContext} and uses step type {@code tool_call}.
     *
     * @param toolName the tool name
     * @param toolType the tool type (function, mcp, api)
     * @return true if allowed, false if blocked (when autoBlock is false)
     * @throws WorkflowBlockedError          if blocked and autoBlock is true
     * @throws WorkflowApprovalRequiredError  if approval is required
     * @throws IllegalStateException          if workflow not started
     */
    public boolean checkToolGate(String toolName, String toolType) {
        return checkToolGate(toolName, toolType, null);
    }

    /**
     * Checks if a specific tool invocation is allowed, with options.
     *
     * @param toolName the tool name
     * @param toolType the tool type (function, mcp, api)
     * @param options  additional options (may be null)
     * @return true if allowed, false if blocked (when autoBlock is false)
     */
    public boolean checkToolGate(String toolName, String toolType, CheckToolGateOptions options) {
        String stepName = (options != null && options.getStepName() != null)
                ? options.getStepName()
                : "tools/" + toolName;

        ToolContext toolContext = ToolContext.builder(toolName)
                .toolType(toolType)
                .toolInput(options != null ? options.getToolInput() : null)
                .build();

        CheckGateOptions gateOptions = CheckGateOptions.builder()
                .stepId(options != null ? options.getStepId() : null)
                .model(options != null ? options.getModel() : null)
                .provider(options != null ? options.getProvider() : null)
                .toolContext(toolContext)
                .build();

        return checkGate(stepName, StepType.TOOL_CALL.getValue(), gateOptions);
    }

    /**
     * Marks a tool invocation as completed.
     *
     * @param toolName the tool name
     * @throws IllegalStateException if workflow not started
     */
    public void toolCompleted(String toolName) {
        toolCompleted(toolName, null);
    }

    /**
     * Marks a tool invocation as completed, with options.
     *
     * @param toolName the tool name
     * @param options  additional options (may be null)
     * @throws IllegalStateException if workflow not started
     */
    public void toolCompleted(String toolName, ToolCompletedOptions options) {
        String stepName = (options != null && options.getStepName() != null)
                ? options.getStepName()
                : "tools/" + toolName;

        StepCompletedOptions stepOptions = null;
        if (options != null) {
            stepOptions = StepCompletedOptions.builder()
                    .stepId(options.getStepId())
                    .output(options.getOutput())
                    .tokensIn(options.getTokensIn())
                    .tokensOut(options.getTokensOut())
                    .costUsd(options.getCostUsd())
                    .build();
        }

        stepCompleted(stepName, stepOptions);
    }

    // ========================================================================
    // Approval
    // ========================================================================

    /**
     * Waits for a step to be approved by polling the workflow status.
     *
     * @param stepId         the step ID to wait for
     * @param pollIntervalMs milliseconds between polls
     * @param timeoutMs      maximum milliseconds to wait
     * @return true if approved, false if rejected
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws TimeoutException     if approval not received within timeout
     * @throws IllegalStateException if workflow not started
     */
    public boolean waitForApproval(String stepId, long pollIntervalMs, long timeoutMs)
            throws InterruptedException, TimeoutException {
        requireStarted();

        long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadlineNanos) {
            WorkflowStatusResponse status = client.getWorkflow(workflowId);

            for (WorkflowStepInfo step : status.getSteps()) {
                if (stepId.equals(step.getStepId())) {
                    if (step.getApprovalStatus() != null) {
                        if (step.getApprovalStatus() == ApprovalStatus.APPROVED) {
                            return true;
                        }
                        if (step.getApprovalStatus() == ApprovalStatus.REJECTED) {
                            return false;
                        }
                    }
                    break;
                }
            }

            Thread.sleep(pollIntervalMs);
        }

        throw new TimeoutException("Approval timeout after " + timeoutMs + "ms for step " + stepId);
    }

    // ========================================================================
    // MCP Interceptor
    // ========================================================================

    /**
     * Creates an MCP tool interceptor with default options.
     *
     * <p>The interceptor enforces AxonFlow input and output policies around
     * every MCP tool call.
     *
     * @return a new MCP tool interceptor
     */
    public MCPToolInterceptor mcpToolInterceptor() {
        return mcpToolInterceptor(null);
    }

    /**
     * Creates an MCP tool interceptor with custom options.
     *
     * @param options interceptor options (may be null for defaults)
     * @return a new MCP tool interceptor
     */
    public MCPToolInterceptor mcpToolInterceptor(MCPInterceptorOptions options) {
        Function<MCPToolRequest, String> connectorTypeFn;
        String operation;

        if (options != null) {
            connectorTypeFn = options.getConnectorTypeFn() != null
                    ? options.getConnectorTypeFn()
                    : req -> req.getServerName() + "." + req.getName();
            operation = options.getOperation();
        } else {
            connectorTypeFn = req -> req.getServerName() + "." + req.getName();
            operation = "execute";
        }

        return new MCPToolInterceptor(client, connectorTypeFn, operation);
    }

    // ========================================================================
    // Accessors
    // ========================================================================

    /**
     * Returns the workflow ID assigned after {@link #startWorkflow()}.
     *
     * @return the workflow ID, or null if not yet started
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * Returns the current step counter value.
     *
     * @return the step counter
     */
    int getStepCounter() {
        return stepCounter;
    }

    // ========================================================================
    // AutoCloseable
    // ========================================================================

    /**
     * Closes the adapter. If the workflow was started but not explicitly
     * completed, aborted, or failed, it will be aborted automatically.
     */
    @Override
    public void close() {
        if (workflowId != null && !closedNormally) {
            try {
                abortWorkflow("Adapter closed without explicit completion");
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }

    // ========================================================================
    // Internals
    // ========================================================================

    private void requireStarted() {
        if (workflowId == null) {
            throw new IllegalStateException("Workflow not started. Call startWorkflow() first.");
        }
    }

    // ========================================================================
    // Builder
    // ========================================================================

    /**
     * Builder for {@link LangGraphAdapter}.
     */
    public static final class Builder {

        private final AxonFlow client;
        private final String workflowName;
        private WorkflowSource source = WorkflowSource.LANGGRAPH;
        private boolean autoBlock = true;

        private Builder(AxonFlow client, String workflowName) {
            this.client = Objects.requireNonNull(client, "client cannot be null");
            this.workflowName = Objects.requireNonNull(workflowName, "workflowName cannot be null");
        }

        /**
         * Sets the workflow source. Defaults to {@link WorkflowSource#LANGGRAPH}.
         *
         * @param source the workflow source
         * @return this builder
         */
        public Builder source(WorkflowSource source) {
            this.source = Objects.requireNonNull(source, "source cannot be null");
            return this;
        }

        /**
         * Sets whether to automatically throw on blocked steps.
         * Defaults to {@code true}.
         *
         * <p>When {@code true}, {@link LangGraphAdapter#checkGate} throws
         * {@link WorkflowBlockedError} on block decisions.
         * When {@code false}, it returns {@code false}.
         *
         * @param autoBlock whether to auto-block
         * @return this builder
         */
        public Builder autoBlock(boolean autoBlock) {
            this.autoBlock = autoBlock;
            return this;
        }

        /**
         * Builds the adapter.
         *
         * @return a new LangGraphAdapter
         */
        public LangGraphAdapter build() {
            return new LangGraphAdapter(this);
        }
    }
}
