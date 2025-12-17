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
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.exceptions.*;
import com.getaxonflow.sdk.types.*;
import com.getaxonflow.sdk.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Main client for interacting with the AxonFlow API.
 *
 * <p>The AxonFlow client provides methods for:
 * <ul>
 *   <li><strong>Gateway Mode:</strong> Pre-check and audit for your own LLM calls</li>
 *   <li><strong>Proxy Mode:</strong> Let AxonFlow handle policy and LLM routing</li>
 *   <li><strong>Planning:</strong> Multi-agent planning (MAP) operations</li>
 *   <li><strong>Connectors:</strong> MCP connector discovery and queries</li>
 * </ul>
 *
 * <h2>Gateway Mode Example</h2>
 * <pre>{@code
 * AxonFlow axonflow = AxonFlow.builder()
 *     .agentUrl("http://localhost:8080")
 *     .clientId("my-client")
 *     .clientSecret("my-secret")
 *     .build();
 *
 * // Step 1: Pre-check
 * PolicyApprovalResult approval = axonflow.getPolicyApprovedContext(
 *     PolicyApprovalRequest.builder()
 *         .userToken("user-123")
 *         .query("What is the weather?")
 *         .build());
 *
 * if (approval.isApproved()) {
 *     // Step 2: Make your LLM call
 *     // ... call OpenAI/Anthropic directly ...
 *
 *     // Step 3: Audit
 *     axonflow.auditLLMCall(AuditOptions.builder()
 *         .contextId(approval.getContextId())
 *         .provider("openai")
 *         .model("gpt-4")
 *         .tokenUsage(TokenUsage.of(100, 150))
 *         .latencyMs(1234)
 *         .build());
 * }
 * }</pre>
 *
 * <h2>Proxy Mode Example</h2>
 * <pre>{@code
 * ClientResponse response = axonflow.executeQuery(
 *     ClientRequest.builder()
 *         .query("What is the weather?")
 *         .userToken("user-123")
 *         .llmProvider("openai")
 *         .model("gpt-4")
 *         .build());
 *
 * if (response.isSuccess() && !response.isBlocked()) {
 *     System.out.println(response.getData());
 * }
 * }</pre>
 *
 * @see AxonFlowConfig
 * @see PolicyApprovalRequest
 * @see ClientRequest
 */
public final class AxonFlow implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(AxonFlow.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AxonFlowConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryExecutor retryExecutor;
    private final ResponseCache cache;
    private final Executor asyncExecutor;

    private AxonFlow(AxonFlowConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.httpClient = HttpClientFactory.create(config);
        this.objectMapper = createObjectMapper();
        this.retryExecutor = new RetryExecutor(config.getRetryConfig());
        this.cache = new ResponseCache(config.getCacheConfig());
        this.asyncExecutor = ForkJoinPool.commonPool();

        logger.info("AxonFlow client initialized for {}", config.getAgentUrl());
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        return mapper;
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Creates a new builder for AxonFlow configuration.
     *
     * @return a new builder
     */
    public static AxonFlowConfig.Builder builder() {
        return AxonFlowConfig.builder();
    }

    /**
     * Creates an AxonFlow client with the given configuration.
     *
     * @param config the configuration
     * @return a new AxonFlow client
     */
    public static AxonFlow create(AxonFlowConfig config) {
        return new AxonFlow(config);
    }

    /**
     * Creates an AxonFlow client from environment variables.
     *
     * @return a new AxonFlow client
     * @see AxonFlowConfig#fromEnvironment()
     */
    public static AxonFlow fromEnvironment() {
        return new AxonFlow(AxonFlowConfig.fromEnvironment());
    }

    /**
     * Creates an AxonFlow client in sandbox mode.
     *
     * @param agentUrl the Agent URL
     * @return a new AxonFlow client in sandbox mode
     */
    public static AxonFlow sandbox(String agentUrl) {
        return new AxonFlow(AxonFlowConfig.builder()
            .agentUrl(agentUrl)
            .mode(Mode.SANDBOX)
            .build());
    }

    // ========================================================================
    // Health Check
    // ========================================================================

    /**
     * Checks if the AxonFlow Agent is healthy.
     *
     * @return the health status
     * @throws ConnectionException if the Agent cannot be reached
     */
    public HealthStatus healthCheck() {
        return retryExecutor.execute(() -> {
            Request request = buildRequest("GET", "/health", null);
            try (Response response = httpClient.newCall(request).execute()) {
                return parseResponse(response, HealthStatus.class);
            }
        }, "healthCheck");
    }

    /**
     * Asynchronously checks if the AxonFlow Agent is healthy.
     *
     * @return a future containing the health status
     */
    public CompletableFuture<HealthStatus> healthCheckAsync() {
        return CompletableFuture.supplyAsync(this::healthCheck, asyncExecutor);
    }

    // ========================================================================
    // Gateway Mode - Policy Pre-check and Audit
    // ========================================================================

    /**
     * Pre-checks a request against policies (Gateway Mode - Step 1).
     *
     * <p>This is the first step in Gateway Mode. If approved, make your LLM call
     * directly, then call {@link #auditLLMCall(AuditOptions)} to complete the flow.
     *
     * @param request the policy approval request
     * @return the approval result with context ID for auditing
     * @throws PolicyViolationException if the request is blocked by policy
     * @throws AuthenticationException  if authentication fails
     */
    public PolicyApprovalResult getPolicyApprovedContext(PolicyApprovalRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/gateway/pre-check", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                PolicyApprovalResult result = parseResponse(response, PolicyApprovalResult.class);

                if (!result.isApproved()) {
                    throw new PolicyViolationException(
                        result.getBlockReason(),
                        result.getBlockingPolicyName(),
                        result.getPolicies()
                    );
                }

                return result;
            }
        }, "getPolicyApprovedContext");
    }

    /**
     * Alias for {@link #getPolicyApprovedContext(PolicyApprovalRequest)}.
     *
     * @param request the policy approval request
     * @return the approval result
     */
    public PolicyApprovalResult preCheck(PolicyApprovalRequest request) {
        return getPolicyApprovedContext(request);
    }

    /**
     * Asynchronously pre-checks a request against policies.
     *
     * @param request the policy approval request
     * @return a future containing the approval result
     */
    public CompletableFuture<PolicyApprovalResult> getPolicyApprovedContextAsync(PolicyApprovalRequest request) {
        return CompletableFuture.supplyAsync(() -> getPolicyApprovedContext(request), asyncExecutor);
    }

    /**
     * Audits an LLM call for compliance tracking (Gateway Mode - Step 3).
     *
     * <p>Call this after making your direct LLM call to record it for
     * compliance and observability.
     *
     * @param options the audit options including context ID from pre-check
     * @return the audit result
     * @throws AxonFlowException if the audit fails
     */
    public AuditResult auditLLMCall(AuditOptions options) {
        Objects.requireNonNull(options, "options cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/gateway/audit", options);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, AuditResult.class);
            }
        }, "auditLLMCall");
    }

    /**
     * Asynchronously audits an LLM call.
     *
     * @param options the audit options
     * @return a future containing the audit result
     */
    public CompletableFuture<AuditResult> auditLLMCallAsync(AuditOptions options) {
        return CompletableFuture.supplyAsync(() -> auditLLMCall(options), asyncExecutor);
    }

    // ========================================================================
    // Proxy Mode - Query Execution
    // ========================================================================

    /**
     * Executes a query through AxonFlow (Proxy Mode).
     *
     * <p>In Proxy Mode, AxonFlow handles both policy enforcement and LLM routing.
     * This is the simplest integration pattern but adds latency.
     *
     * @param request the client request
     * @return the response from AxonFlow
     * @throws PolicyViolationException if the request is blocked by policy
     * @throws AuthenticationException  if authentication fails
     */
    public ClientResponse executeQuery(ClientRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        // Check cache first
        String cacheKey = ResponseCache.generateKey(
            request.getRequestType(),
            request.getQuery(),
            request.getUserToken()
        );

        return cache.get(cacheKey, ClientResponse.class).orElseGet(() -> {
            ClientResponse response = retryExecutor.execute(() -> {
                Request httpRequest = buildRequest("POST", "/api/request", request);
                try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
                    ClientResponse result = parseResponse(httpResponse, ClientResponse.class);

                    if (result.isBlocked()) {
                        throw new PolicyViolationException(
                            result.getBlockReason(),
                            result.getBlockingPolicyName(),
                            result.getPolicyInfo() != null
                                ? result.getPolicyInfo().getPoliciesEvaluated()
                                : null
                        );
                    }

                    return result;
                }
            }, "executeQuery");

            // Cache successful responses
            if (response.isSuccess() && !response.isBlocked()) {
                cache.put(cacheKey, response);
            }

            return response;
        });
    }

    /**
     * Asynchronously executes a query through AxonFlow.
     *
     * @param request the client request
     * @return a future containing the response
     */
    public CompletableFuture<ClientResponse> executeQueryAsync(ClientRequest request) {
        return CompletableFuture.supplyAsync(() -> executeQuery(request), asyncExecutor);
    }

    // ========================================================================
    // Multi-Agent Planning (MAP)
    // ========================================================================

    /**
     * Generates a multi-agent plan for a complex task.
     *
     * @param request the plan request
     * @return the generated plan
     * @throws PlanExecutionException if plan generation fails
     */
    public PlanResponse generatePlan(PlanRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/orchestrator/plan", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PlanResponse.class);
            }
        }, "generatePlan");
    }

    /**
     * Asynchronously generates a multi-agent plan.
     *
     * @param request the plan request
     * @return a future containing the generated plan
     */
    public CompletableFuture<PlanResponse> generatePlanAsync(PlanRequest request) {
        return CompletableFuture.supplyAsync(() -> generatePlan(request), asyncExecutor);
    }

    /**
     * Executes a previously generated plan.
     *
     * @param planId the ID of the plan to execute
     * @return the execution result
     * @throws PlanExecutionException if execution fails
     */
    public PlanResponse executePlan(String planId) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST",
                "/api/v1/orchestrator/plan/" + planId + "/execute", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PlanResponse.class);
            }
        }, "executePlan");
    }

    /**
     * Gets the status of a plan.
     *
     * @param planId the plan ID
     * @return the plan status
     */
    public PlanResponse getPlanStatus(String planId) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET",
                "/api/v1/orchestrator/plan/" + planId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PlanResponse.class);
            }
        }, "getPlanStatus");
    }

    // ========================================================================
    // MCP Connectors
    // ========================================================================

    /**
     * Lists available MCP connectors.
     *
     * @return list of available connectors
     */
    public List<ConnectorInfo> listConnectors() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/connectors", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<List<ConnectorInfo>>() {});
            }
        }, "listConnectors");
    }

    /**
     * Asynchronously lists available MCP connectors.
     *
     * @return a future containing the list of connectors
     */
    public CompletableFuture<List<ConnectorInfo>> listConnectorsAsync() {
        return CompletableFuture.supplyAsync(this::listConnectors, asyncExecutor);
    }

    /**
     * Installs an MCP connector.
     *
     * @param connectorId the connector ID to install
     * @param config      the connector configuration
     * @return the installed connector info
     */
    public ConnectorInfo installConnector(String connectorId, Map<String, Object> config) {
        Objects.requireNonNull(connectorId, "connectorId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = Map.of(
                "connector_id", connectorId,
                "config", config != null ? config : Map.of()
            );
            Request httpRequest = buildRequest("POST", "/api/v1/connectors/install", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ConnectorInfo.class);
            }
        }, "installConnector");
    }

    /**
     * Queries an MCP connector.
     *
     * @param query the connector query
     * @return the query response
     * @throws ConnectorException if the query fails
     */
    public ConnectorResponse queryConnector(ConnectorQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/connectors/query", query);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                ConnectorResponse result = parseResponse(response, ConnectorResponse.class);

                if (!result.isSuccess()) {
                    throw new ConnectorException(
                        result.getError(),
                        query.getConnectorId(),
                        query.getOperation()
                    );
                }

                return result;
            }
        }, "queryConnector");
    }

    /**
     * Asynchronously queries an MCP connector.
     *
     * @param query the connector query
     * @return a future containing the response
     */
    public CompletableFuture<ConnectorResponse> queryConnectorAsync(ConnectorQuery query) {
        return CompletableFuture.supplyAsync(() -> queryConnector(query), asyncExecutor);
    }

    // ========================================================================
    // Configuration Access
    // ========================================================================

    /**
     * Returns the current configuration.
     *
     * @return the configuration
     */
    public AxonFlowConfig getConfig() {
        return config;
    }

    /**
     * Returns cache statistics.
     *
     * @return cache stats string
     */
    public String getCacheStats() {
        return cache.getStats();
    }

    /**
     * Clears the response cache.
     */
    public void clearCache() {
        cache.clear();
    }

    // ========================================================================
    // Internal Methods
    // ========================================================================

    private Request buildRequest(String method, String path, Object body) {
        HttpUrl url = HttpUrl.parse(config.getAgentUrl() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getAgentUrl() + path);
        }

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("Accept", "application/json");

        // Add authentication headers
        addAuthHeaders(builder);

        // Add mode header
        if (config.getMode() != null) {
            builder.header("X-AxonFlow-Mode", config.getMode().getValue());
        }

        // Set method and body
        RequestBody requestBody = null;
        if (body != null) {
            try {
                String json = objectMapper.writeValueAsString(body);
                requestBody = RequestBody.create(json, JSON);
            } catch (JsonProcessingException e) {
                throw new AxonFlowException("Failed to serialize request body", e);
            }
        }

        switch (method.toUpperCase()) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            case "PUT":
                builder.put(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            case "DELETE":
                builder.delete(requestBody);
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return builder.build();
    }

    private void addAuthHeaders(Request.Builder builder) {
        // Skip auth for localhost in self-hosted mode
        if (config.isLocalhost()) {
            logger.debug("Skipping authentication for localhost");
            return;
        }

        // Prefer license key
        if (config.getLicenseKey() != null && !config.getLicenseKey().isEmpty()) {
            builder.header("X-License-Key", config.getLicenseKey());
            return;
        }

        // Fall back to client credentials
        if (config.getClientId() != null && config.getClientSecret() != null) {
            builder.header("X-Client-ID", config.getClientId());
            builder.header("X-Client-Secret", config.getClientSecret());
        }
    }

    private <T> T parseResponse(Response response, Class<T> type) throws IOException {
        handleErrorResponse(response);

        ResponseBody body = response.body();
        if (body == null) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        String json = body.string();
        if (json.isEmpty()) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AxonFlowException("Failed to parse response: " + e.getMessage(), response.code(), null, e);
        }
    }

    private <T> T parseResponse(Response response, TypeReference<T> typeRef) throws IOException {
        handleErrorResponse(response);

        ResponseBody body = response.body();
        if (body == null) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        String json = body.string();
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new AxonFlowException("Failed to parse response: " + e.getMessage(), response.code(), null, e);
        }
    }

    private void handleErrorResponse(Response response) throws IOException {
        if (response.isSuccessful()) {
            return;
        }

        int code = response.code();
        String message = response.message();
        String body = response.body() != null ? response.body().string() : "";

        // Try to extract error message from JSON body
        String errorMessage = extractErrorMessage(body, message);

        switch (code) {
            case 401:
                throw new AuthenticationException(errorMessage);
            case 403:
                // Check if this is a policy violation
                if (body.contains("policy") || body.contains("blocked")) {
                    throw new PolicyViolationException(errorMessage);
                }
                throw new AuthenticationException(errorMessage, 403);
            case 429:
                throw new RateLimitException(errorMessage);
            case 408:
            case 504:
                throw new TimeoutException(errorMessage);
            default:
                throw new AxonFlowException(errorMessage, code, null);
        }
    }

    private String extractErrorMessage(String body, String defaultMessage) {
        if (body == null || body.isEmpty()) {
            return defaultMessage;
        }

        try {
            Map<String, Object> errorResponse = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() {});

            if (errorResponse.containsKey("error")) {
                return String.valueOf(errorResponse.get("error"));
            }
            if (errorResponse.containsKey("message")) {
                return String.valueOf(errorResponse.get("message"));
            }
            if (errorResponse.containsKey("block_reason")) {
                return String.valueOf(errorResponse.get("block_reason"));
            }
        } catch (JsonProcessingException e) {
            // Body is not JSON, return as-is if short enough
            if (body.length() < 200) {
                return body;
            }
        }

        return defaultMessage;
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        cache.clear();
        logger.info("AxonFlow client closed");
    }
}
