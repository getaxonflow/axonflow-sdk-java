/*
 * Copyright 2025 AxonFlow
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

/**
 * AxonFlow Java SDK - AI Governance Platform for Enterprise LLM Applications.
 *
 * <p>This SDK provides a Java client for interacting with the AxonFlow API,
 * enabling AI governance, policy enforcement, and compliance tracking for
 * LLM applications.
 *
 * <h2>Quick Start</h2>
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
 * <ul>
 *   <li>{@link com.axonflow.sdk.AxonFlow} - Main client class</li>
 *   <li>{@link com.axonflow.sdk.AxonFlowConfig} - Configuration builder</li>
 *   <li>{@link com.axonflow.sdk.types.PolicyApprovalRequest} - Gateway Mode pre-check request</li>
 *   <li>{@link com.axonflow.sdk.types.ClientRequest} - Proxy Mode query request</li>
 * </ul>
 *
 * @see com.axonflow.sdk.AxonFlow
 * @see com.axonflow.sdk.AxonFlowConfig
 */
package com.axonflow.sdk;
