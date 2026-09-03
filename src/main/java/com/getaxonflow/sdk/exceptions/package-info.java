// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT

/**
 * Exception types for the AxonFlow SDK.
 *
 * <p>All exceptions extend {@link com.getaxonflow.sdk.exceptions.AxonFlowException}, allowing
 * callers to catch all SDK errors with a single catch block.
 *
 * <h2>Exception Hierarchy</h2>
 *
 * <pre>
 * AxonFlowException (base)
 * ├── AuthenticationException   - Authentication/authorization failures
 * ├── PolicyViolationException  - Request blocked by policy
 * ├── RateLimitException        - Rate limit exceeded
 * ├── TimeoutException          - Request timeout
 * ├── ConnectionException       - Network/connection errors
 * ├── ConfigurationException    - Invalid configuration
 * ├── ConnectorException        - MCP connector errors
 * ├── PlanExecutionException    - Plan generation/execution errors
 * └── VersionConflictException  - Optimistic concurrency version conflict
 * </pre>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * try {
 *     axonflow.proxyLLMCall(request);
 * } catch (PolicyViolationException e) {
 *     System.out.println("Blocked by: " + e.getPolicyName());
 * } catch (RateLimitException e) {
 *     System.out.println("Retry after: " + e.getRetryAfter());
 * } catch (AxonFlowException e) {
 *     System.out.println("Error: " + e.getMessage());
 * }
 * }</pre>
 */
package com.getaxonflow.sdk.exceptions;
