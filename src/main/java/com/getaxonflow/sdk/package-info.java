// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT

/**
 * AxonFlow Java SDK - AI Governance Platform for Enterprise LLM Applications.
 *
 * <p>This SDK provides a Java client for interacting with the AxonFlow API, enabling AI governance,
 * policy enforcement, and compliance tracking for LLM applications.
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Create a client
 * AxonFlow axonflow = AxonFlow.create(AxonFlowConfig.builder()
 *     .agentUrl("http://localhost:8080")
 *     .clientId("my-client")
 *     .clientSecret("my-secret")
 *     .build());
 *
 * // Gateway Mode - Pre-check before your LLM call
 * PolicyApprovalResult approval = axonflow.getPolicyApprovedContext(
 *     PolicyApprovalRequest.builder()
 *         .userToken("user-123")
 *         .query("What is the weather?")
 *         .build());
 *
 * if (approval.isApproved()) {
 *     // Make your LLM call directly
 *     // Then audit it
 *     axonflow.auditLLMCall(AuditOptions.builder()
 *         .contextId(approval.getContextId())
 *         .provider("openai")
 *         .model("gpt-4")
 *         .build());
 * }
 * }</pre>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.AxonFlow} - Main client class
 *   <li>{@link com.getaxonflow.sdk.AxonFlowConfig} - Configuration builder
 *   <li>{@link com.getaxonflow.sdk.types.PolicyApprovalRequest} - Gateway Mode pre-check request
 *   <li>{@link com.getaxonflow.sdk.types.ClientRequest} - Proxy Mode query request
 * </ul>
 *
 * @see com.getaxonflow.sdk.AxonFlow
 * @see com.getaxonflow.sdk.AxonFlowConfig
 */
package com.getaxonflow.sdk;
