// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

/**
 * Workflow Control Plane types for AxonFlow SDK.
 *
 * <p>The Workflow Control Plane provides governance gates for external orchestrators like
 * LangChain, LangGraph, and CrewAI. These types define the request/response structures for
 * registering workflows, checking step gates, and managing workflow lifecycle.
 *
 * <p>"LangChain runs the workflow. AxonFlow decides when it's allowed to move forward."
 *
 * <h2>Policy Enforcement Types (v2.3.0)</h2>
 *
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.types.workflow.PolicyEvaluationResult} - Result of policy
 *       evaluation during execution
 *   <li>{@link com.getaxonflow.sdk.types.workflow.PolicyMatch} - Information about a matched policy
 *   <li>{@link com.getaxonflow.sdk.types.workflow.PlanExecutionResponse} - Response from MAP plan
 *       execution with policy info
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create a workflow
 * CreateWorkflowResponse workflow = axonflow.createWorkflow(
 *     CreateWorkflowRequest.builder()
 *         .workflowName("code-review-pipeline")
 *         .source(WorkflowSource.LANGGRAPH)
 *         .build()
 * );
 *
 * // Check step gate with policy evaluation
 * StepGateResponse gate = axonflow.stepGate(
 *     workflow.getWorkflowId(),
 *     "step-1",
 *     StepGateRequest.builder()
 *         .stepName("Generate Code")
 *         .stepType(StepType.LLM_CALL)
 *         .model("gpt-4")
 *         .build()
 * );
 *
 * if (gate.isBlocked()) {
 *     // Check which policies blocked the step
 *     for (PolicyMatch match : gate.getPoliciesMatched()) {
 *         System.out.println("Blocked by: " + match.getPolicyName() + " - " + match.getReason());
 *     }
 *     throw new RuntimeException("Step blocked: " + gate.getReason());
 * }
 *
 * // Execute step and complete workflow
 * axonflow.completeWorkflow(workflow.getWorkflowId());
 * }</pre>
 *
 * @see com.getaxonflow.sdk.types.workflow.WorkflowTypes
 * @see com.getaxonflow.sdk.types.workflow.PolicyEvaluationResult
 * @see com.getaxonflow.sdk.types.workflow.PolicyMatch
 * @see com.getaxonflow.sdk.types.workflow.PlanExecutionResponse
 * @see com.getaxonflow.sdk.AxonFlow#createWorkflow
 * @see com.getaxonflow.sdk.AxonFlow#stepGate
 */
package com.getaxonflow.sdk.types.workflow;
