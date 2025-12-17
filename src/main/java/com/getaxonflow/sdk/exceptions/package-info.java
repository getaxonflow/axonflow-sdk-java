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
 * Exception types for the AxonFlow SDK.
 *
 * <p>All exceptions extend {@link com.getaxonflow.sdk.exceptions.AxonFlowException},
 * allowing callers to catch all SDK errors with a single catch block.
 *
 * <h2>Exception Hierarchy</h2>
 * <pre>
 * AxonFlowException (base)
 * ├── AuthenticationException   - Authentication/authorization failures
 * ├── PolicyViolationException  - Request blocked by policy
 * ├── RateLimitException        - Rate limit exceeded
 * ├── TimeoutException          - Request timeout
 * ├── ConnectionException       - Network/connection errors
 * ├── ConfigurationException    - Invalid configuration
 * ├── ConnectorException        - MCP connector errors
 * └── PlanExecutionException    - Plan generation/execution errors
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try {
 *     axonflow.executeQuery(request);
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
