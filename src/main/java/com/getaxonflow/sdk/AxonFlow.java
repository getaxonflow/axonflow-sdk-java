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
import com.getaxonflow.sdk.telemetry.TelemetryReporter;
import com.getaxonflow.sdk.types.*;
import com.getaxonflow.sdk.types.codegovernance.*;
import com.getaxonflow.sdk.types.costcontrols.CostControlTypes.*;
import com.getaxonflow.sdk.types.executionreplay.ExecutionReplayTypes.*;
import com.getaxonflow.sdk.types.hitl.HITLTypes.*;
import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import com.getaxonflow.sdk.types.webhook.WebhookTypes.*;
import com.getaxonflow.sdk.simulation.*;
import com.getaxonflow.sdk.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

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
 * ClientResponse response = axonflow.proxyLLMCall(
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
    private volatile String sessionCookie; // Session cookie for Customer Portal authentication
    private final MASFEATNamespace masfeatNamespace;

    private AxonFlow(AxonFlowConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.httpClient = HttpClientFactory.create(config);
        this.objectMapper = createObjectMapper();
        this.retryExecutor = new RetryExecutor(config.getRetryConfig());
        this.cache = new ResponseCache(config.getCacheConfig());
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.masfeatNamespace = new MASFEATNamespace();

        logger.info("AxonFlow client initialized for {}", config.getEndpoint());

        // Send telemetry ping (fire-and-forget).
        boolean hasCredentials = config.getClientId() != null && !config.getClientId().isEmpty()
                && config.getClientSecret() != null && !config.getClientSecret().isEmpty();
        TelemetryReporter.sendPing(
            config.getMode() != null ? config.getMode().getValue() : "production",
            config.getEndpoint(),
            config.getTelemetry(),
            config.isDebug(),
            hasCredentials
        );
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        return mapper;
    }

    /**
     * Compares two semantic version strings numerically (major.minor.patch).
     * Returns negative if a < b, zero if equal, positive if a > b.
     */
    private static int compareSemver(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int numA = 0;
            int numB = 0;
            if (i < partsA.length) {
                try {
                    String cleanA = partsA[i].contains("-") ? partsA[i].substring(0, partsA[i].indexOf("-")) : partsA[i];
                    numA = Integer.parseInt(cleanA);
                } catch (NumberFormatException ignored) {
                    // default to 0
                }
            }
            if (i < partsB.length) {
                try {
                    String cleanB = partsB[i].contains("-") ? partsB[i].substring(0, partsB[i].indexOf("-")) : partsB[i];
                    numB = Integer.parseInt(cleanB);
                } catch (NumberFormatException ignored) {
                    // default to 0
                }
            }
            if (numA != numB) {
                return Integer.compare(numA, numB);
            }
        }
        return 0;
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
        HealthStatus status = retryExecutor.execute(() -> {
            Request request = buildRequest("GET", "/health", null);
            try (Response response = httpClient.newCall(request).execute()) {
                return parseResponse(response, HealthStatus.class);
            }
        }, "healthCheck");

        if (status.getSdkCompatibility() != null
                && status.getSdkCompatibility().getMinSdkVersion() != null
                && !"unknown".equals(AxonFlowConfig.SDK_VERSION)
                && compareSemver(AxonFlowConfig.SDK_VERSION, status.getSdkCompatibility().getMinSdkVersion()) < 0) {
            logger.warn("SDK version {} is below minimum supported version {}. Please upgrade.",
                    AxonFlowConfig.SDK_VERSION, status.getSdkCompatibility().getMinSdkVersion());
        }

        return status;
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
    // MAS FEAT Namespace Accessor
    // ========================================================================

    /**
     * Returns the MAS FEAT (Monetary Authority of Singapore - Fairness, Ethics,
     * Accountability, Transparency) compliance namespace.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * <p>Example usage:
     * <pre>{@code
     * AISystemRegistry system = client.masfeat().registerSystem(
     *     RegisterSystemRequest.builder()
     *         .systemId("credit-scoring-ai")
     *         .systemName("Credit Scoring AI")
     *         .useCase(AISystemUseCase.CREDIT_SCORING)
     *         .ownerTeam("Risk Management")
     *         .customerImpact(4)
     *         .modelComplexity(3)
     *         .humanReliance(5)
     *         .build()
     * );
     * }</pre>
     *
     * @return the MAS FEAT compliance namespace
     */
    public MASFEATNamespace masfeat() {
        return masfeatNamespace;
    }

    /**
     * Checks if the AxonFlow Orchestrator is healthy.
     *
     * @return the health status
     * @throws ConnectionException if the Orchestrator cannot be reached
     */
    public HealthStatus orchestratorHealthCheck() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/health", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    return new HealthStatus("unhealthy", null, null, null, null, null);
                }
                return parseResponse(response, HealthStatus.class);
            }
        }, "orchestratorHealthCheck");
    }

    /**
     * Asynchronously checks if the AxonFlow Orchestrator is healthy.
     *
     * @return a future containing the health status
     */
    public CompletableFuture<HealthStatus> orchestratorHealthCheckAsync() {
        return CompletableFuture.supplyAsync(this::orchestratorHealthCheck, asyncExecutor);
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

        // Use smart default for clientId - enables zero-config community mode
        String effectiveClientId = (request.getClientId() != null && !request.getClientId().isEmpty())
            ? request.getClientId()
            : getEffectiveClientId();

        Map<String, Object> ctx = request.getContext();
        PolicyApprovalRequest effectiveRequest = PolicyApprovalRequest.builder()
            .userToken(request.getUserToken())
            .query(request.getQuery())
            .dataSources(request.getDataSources())
            .context(ctx == null || ctx.isEmpty() ? null : ctx)
            .clientId(effectiveClientId)
            .build();

        final PolicyApprovalRequest finalRequest = effectiveRequest;
        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/policy/pre-check", finalRequest);
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

        // Use smart default for clientId - enables zero-config community mode
        String effectiveClientId = (options.getClientId() != null && !options.getClientId().isEmpty())
            ? options.getClientId()
            : getEffectiveClientId();

        // Create effective options with the smart default clientId
        AuditOptions.Builder builder = AuditOptions.builder()
            .contextId(options.getContextId())
            .clientId(effectiveClientId)
            .responseSummary(options.getResponseSummary())
            .provider(options.getProvider())
            .model(options.getModel())
            .tokenUsage(options.getTokenUsage())
            .metadata(options.getMetadata())
            .success(options.getSuccess())
            .errorMessage(options.getErrorMessage());

        // Handle null latencyMs (builder takes primitive long)
        if (options.getLatencyMs() != null) {
            builder.latencyMs(options.getLatencyMs());
        }

        AuditOptions effectiveOptions = builder.build();

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/audit/llm-call", effectiveOptions);
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
    // Audit Log Read Methods
    // ========================================================================

    /**
     * Searches audit logs with flexible filtering options.
     *
     * <p>Example usage:
     * <pre>{@code
     * AuditSearchResponse response = axonflow.searchAuditLogs(
     *     AuditSearchRequest.builder()
     *         .userEmail("analyst@company.com")
     *         .startTime(Instant.now().minus(Duration.ofDays(7)))
     *         .requestType("llm_chat")
     *         .limit(100)
     *         .build());
     *
     * for (AuditLogEntry entry : response.getEntries()) {
     *     System.out.println(entry.getId() + ": " + entry.getQuerySummary());
     * }
     * }</pre>
     *
     * @param request the search request with optional filters
     * @return the search response containing matching audit log entries
     * @throws AxonFlowException if the search fails
     */
    public AuditSearchResponse searchAuditLogs(AuditSearchRequest request) {
        return retryExecutor.execute(() -> {
            AuditSearchRequest req = request != null ? request : AuditSearchRequest.builder().build();
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/audit/search", req);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);

                // Handle both array and wrapped response formats
                if (node.isArray()) {
                    List<AuditLogEntry> entries = objectMapper.convertValue(
                        node, new TypeReference<List<AuditLogEntry>>() {});
                    return AuditSearchResponse.fromArray(entries,
                        req.getLimit() != null ? req.getLimit() : 100,
                        req.getOffset() != null ? req.getOffset() : 0);
                }

                return objectMapper.treeToValue(node, AuditSearchResponse.class);
            }
        }, "searchAuditLogs");
    }

    /**
     * Searches audit logs with default options (last 100 entries).
     *
     * @return the search response
     */
    public AuditSearchResponse searchAuditLogs() {
        return searchAuditLogs(null);
    }

    /**
     * Asynchronously searches audit logs.
     *
     * @param request the search request
     * @return a future containing the search response
     */
    public CompletableFuture<AuditSearchResponse> searchAuditLogsAsync(AuditSearchRequest request) {
        return CompletableFuture.supplyAsync(() -> searchAuditLogs(request), asyncExecutor);
    }

    /**
     * Gets audit logs for a specific tenant.
     *
     * <p>Example usage:
     * <pre>{@code
     * AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc",
     *     AuditQueryOptions.builder()
     *         .limit(100)
     *         .offset(50)
     *         .build());
     *
     * System.out.println("Total entries: " + response.getTotal());
     * System.out.println("Has more: " + response.hasMore());
     * }</pre>
     *
     * @param tenantId the tenant ID to query
     * @param options optional pagination options
     * @return the search response containing audit log entries for the tenant
     * @throws IllegalArgumentException if tenantId is null or empty
     * @throws AxonFlowException if the query fails
     */
    public AuditSearchResponse getAuditLogsByTenant(String tenantId, AuditQueryOptions options) {
        if (tenantId == null || tenantId.isEmpty()) {
            throw new IllegalArgumentException("tenantId is required");
        }

        return retryExecutor.execute(() -> {
            AuditQueryOptions opts = options != null ? options : AuditQueryOptions.defaults();
            String encodedTenantId = java.net.URLEncoder.encode(tenantId, "UTF-8");
            String path = "/api/v1/audit/tenant/" + encodedTenantId +
                "?limit=" + opts.getLimit() + "&offset=" + opts.getOffset();

            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);

                // Handle both array and wrapped response formats
                if (node.isArray()) {
                    List<AuditLogEntry> entries = objectMapper.convertValue(
                        node, new TypeReference<List<AuditLogEntry>>() {});
                    return AuditSearchResponse.fromArray(entries, opts.getLimit(), opts.getOffset());
                }

                return objectMapper.treeToValue(node, AuditSearchResponse.class);
            }
        }, "getAuditLogsByTenant");
    }

    /**
     * Gets audit logs for a specific tenant with default options.
     *
     * @param tenantId the tenant ID to query
     * @return the search response
     */
    public AuditSearchResponse getAuditLogsByTenant(String tenantId) {
        return getAuditLogsByTenant(tenantId, null);
    }

    /**
     * Asynchronously gets audit logs for a specific tenant.
     *
     * @param tenantId the tenant ID to query
     * @param options optional pagination options
     * @return a future containing the search response
     */
    public CompletableFuture<AuditSearchResponse> getAuditLogsByTenantAsync(String tenantId, AuditQueryOptions options) {
        return CompletableFuture.supplyAsync(() -> getAuditLogsByTenant(tenantId, options), asyncExecutor);
    }

    // ========================================================================
    // Audit Tool Call
    // ========================================================================

    /**
     * Audits a non-LLM tool call for compliance and observability.
     *
     * <p>Records tool invocations such as function calls, MCP operations,
     * or API calls to the audit log.
     *
     * <p>Example usage:
     * <pre>{@code
     * AuditToolCallResponse response = axonflow.auditToolCall(
     *     AuditToolCallRequest.builder()
     *         .toolName("web_search")
     *         .toolType("function")
     *         .input(Map.of("query", "latest news"))
     *         .output(Map.of("results", 5))
     *         .workflowId("wf_123")
     *         .durationMs(450L)
     *         .success(true)
     *         .build());
     * }</pre>
     *
     * @param request the audit tool call request
     * @return the audit tool call response with audit ID
     * @throws NullPointerException if request is null
     * @throws IllegalArgumentException if tool_name is null or empty
     * @throws AxonFlowException if the audit fails
     */
    public AuditToolCallResponse auditToolCall(AuditToolCallRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/audit/tool-call", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, AuditToolCallResponse.class);
            }
        }, "auditToolCall");
    }

    /**
     * Asynchronously audits a non-LLM tool call.
     *
     * @param request the audit tool call request
     * @return a future containing the audit tool call response
     */
    public CompletableFuture<AuditToolCallResponse> auditToolCallAsync(AuditToolCallRequest request) {
        return CompletableFuture.supplyAsync(() -> auditToolCall(request), asyncExecutor);
    }

    // ========================================================================
    // Circuit Breaker Observability
    // ========================================================================

    /**
     * Gets the current circuit breaker status, including all active (tripped) circuits.
     *
     * <p>Example usage:
     * <pre>{@code
     * CircuitBreakerStatusResponse status = axonflow.getCircuitBreakerStatus();
     * System.out.println("Active circuits: " + status.getCount());
     * System.out.println("Emergency stop: " + status.isEmergencyStopActive());
     * }</pre>
     *
     * @return the circuit breaker status
     * @throws AxonFlowException if the request fails
     */
    public CircuitBreakerStatusResponse getCircuitBreakerStatus() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/circuit-breaker/status", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), CircuitBreakerStatusResponse.class);
                }
                return objectMapper.treeToValue(node, CircuitBreakerStatusResponse.class);
            }
        }, "getCircuitBreakerStatus");
    }

    /**
     * Asynchronously gets the current circuit breaker status.
     *
     * @return a future containing the circuit breaker status
     */
    public CompletableFuture<CircuitBreakerStatusResponse> getCircuitBreakerStatusAsync() {
        return CompletableFuture.supplyAsync(this::getCircuitBreakerStatus, asyncExecutor);
    }

    /**
     * Gets the circuit breaker history, including past trips and resets.
     *
     * <p>Example usage:
     * <pre>{@code
     * CircuitBreakerHistoryResponse history = axonflow.getCircuitBreakerHistory(50);
     * for (CircuitBreakerHistoryEntry entry : history.getHistory()) {
     *     System.out.println(entry.getScope() + "/" + entry.getScopeId() + " - " + entry.getState());
     * }
     * }</pre>
     *
     * @param limit the maximum number of history entries to return
     * @return the circuit breaker history
     * @throws IllegalArgumentException if limit is less than 1
     * @throws AxonFlowException if the request fails
     */
    public CircuitBreakerHistoryResponse getCircuitBreakerHistory(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        return retryExecutor.execute(() -> {
            String path = "/api/v1/circuit-breaker/history?limit=" + limit;
            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), CircuitBreakerHistoryResponse.class);
                }
                return objectMapper.treeToValue(node, CircuitBreakerHistoryResponse.class);
            }
        }, "getCircuitBreakerHistory");
    }

    /**
     * Asynchronously gets the circuit breaker history.
     *
     * @param limit the maximum number of history entries to return
     * @return a future containing the circuit breaker history
     */
    public CompletableFuture<CircuitBreakerHistoryResponse> getCircuitBreakerHistoryAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> getCircuitBreakerHistory(limit), asyncExecutor);
    }

    /**
     * Gets the circuit breaker configuration for a specific tenant.
     *
     * <p>Example usage:
     * <pre>{@code
     * CircuitBreakerConfig config = axonflow.getCircuitBreakerConfig("tenant_123");
     * System.out.println("Error threshold: " + config.getErrorThreshold());
     * System.out.println("Auto recovery: " + config.isEnableAutoRecovery());
     * }</pre>
     *
     * @param tenantId the tenant ID to get configuration for
     * @return the circuit breaker configuration
     * @throws NullPointerException if tenantId is null
     * @throws IllegalArgumentException if tenantId is empty
     * @throws AxonFlowException if the request fails
     */
    public CircuitBreakerConfig getCircuitBreakerConfig(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (tenantId.isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be empty");
        }

        return retryExecutor.execute(() -> {
            String path = "/api/v1/circuit-breaker/config?tenant_id=" + java.net.URLEncoder.encode(tenantId, java.nio.charset.StandardCharsets.UTF_8);
            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), CircuitBreakerConfig.class);
                }
                return objectMapper.treeToValue(node, CircuitBreakerConfig.class);
            }
        }, "getCircuitBreakerConfig");
    }

    /**
     * Asynchronously gets the circuit breaker configuration for a specific tenant.
     *
     * @param tenantId the tenant ID to get configuration for
     * @return a future containing the circuit breaker configuration
     */
    public CompletableFuture<CircuitBreakerConfig> getCircuitBreakerConfigAsync(String tenantId) {
        return CompletableFuture.supplyAsync(() -> getCircuitBreakerConfig(tenantId), asyncExecutor);
    }

    /**
     * Updates the circuit breaker configuration for a tenant.
     *
     * <p>Example usage:
     * <pre>{@code
     * CircuitBreakerConfig updated = axonflow.updateCircuitBreakerConfig(
     *     CircuitBreakerConfigUpdate.builder()
     *         .tenantId("tenant_123")
     *         .errorThreshold(10)
     *         .violationThreshold(5)
     *         .enableAutoRecovery(true)
     *         .build());
     * }</pre>
     *
     * @param config the configuration update
     * @return confirmation with tenant_id and message
     * @throws NullPointerException if config is null
     * @throws AxonFlowException if the request fails
     */
    public CircuitBreakerConfigUpdateResponse updateCircuitBreakerConfig(CircuitBreakerConfigUpdate config) {
        Objects.requireNonNull(config, "config cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("PUT", "/api/v1/circuit-breaker/config", config);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), CircuitBreakerConfigUpdateResponse.class);
                }
                return objectMapper.treeToValue(node, CircuitBreakerConfigUpdateResponse.class);
            }
        }, "updateCircuitBreakerConfig");
    }

    /**
     * Asynchronously updates the circuit breaker configuration for a tenant.
     *
     * @param config the configuration update
     * @return a future containing the update confirmation
     */
    public CompletableFuture<CircuitBreakerConfigUpdateResponse> updateCircuitBreakerConfigAsync(CircuitBreakerConfigUpdate config) {
        return CompletableFuture.supplyAsync(() -> updateCircuitBreakerConfig(config), asyncExecutor);
    }

    // ========================================================================
    // Policy Simulation
    // ========================================================================

    /**
     * Simulates policy evaluation against a query without actually enforcing policies.
     *
     * <p>This is a dry-run mode that shows which policies would match and what actions
     * would be taken, without blocking the request.
     *
     * <p>Example usage:
     * <pre>{@code
     * SimulatePoliciesResponse result = axonflow.simulatePolicies(
     *     SimulatePoliciesRequest.builder()
     *         .query("Transfer $50,000 to external account")
     *         .requestType("execute")
     *         .build());
     * System.out.println("Allowed: " + result.isAllowed());
     * System.out.println("Applied policies: " + result.getAppliedPolicies());
     * System.out.println("Risk score: " + result.getRiskScore());
     * }</pre>
     *
     * <p><b>Evaluation+ Feature:</b> Requires AxonFlow Evaluation tier or higher.
     *
     * @param request the simulation request
     * @return the simulation result
     * @throws NullPointerException if request is null
     * @throws AxonFlowException if the request fails
     */
    public SimulatePoliciesResponse simulatePolicies(SimulatePoliciesRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/policies/simulate", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), SimulatePoliciesResponse.class);
                }
                return objectMapper.treeToValue(node, SimulatePoliciesResponse.class);
            }
        }, "simulatePolicies");
    }

    /**
     * Asynchronously simulates policy evaluation against a query.
     *
     * @param request the simulation request
     * @return a future containing the simulation result
     */
    public CompletableFuture<SimulatePoliciesResponse> simulatePoliciesAsync(SimulatePoliciesRequest request) {
        return CompletableFuture.supplyAsync(() -> simulatePolicies(request), asyncExecutor);
    }

    /**
     * Generates a policy impact report by testing a set of inputs against a specific policy.
     *
     * <p>This helps you understand how a policy would affect real traffic before deploying it.
     *
     * <p>Example usage:
     * <pre>{@code
     * ImpactReportResponse report = axonflow.getPolicyImpactReport(
     *     ImpactReportRequest.builder()
     *         .policyId("policy_block_pii")
     *         .inputs(List.of(
     *             ImpactReportInput.builder().query("My SSN is 123-45-6789").build(),
     *             ImpactReportInput.builder().query("What is the weather?").build()))
     *         .build());
     * System.out.println("Match rate: " + report.getMatchRate());
     * System.out.println("Block rate: " + report.getBlockRate());
     * }</pre>
     *
     * <p><b>Evaluation+ Feature:</b> Requires AxonFlow Evaluation tier or higher.
     *
     * @param request the impact report request
     * @return the impact report
     * @throws NullPointerException if request is null
     * @throws AxonFlowException if the request fails
     */
    public ImpactReportResponse getPolicyImpactReport(ImpactReportRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/policies/impact-report", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), ImpactReportResponse.class);
                }
                return objectMapper.treeToValue(node, ImpactReportResponse.class);
            }
        }, "getPolicyImpactReport");
    }

    /**
     * Asynchronously generates a policy impact report.
     *
     * @param request the impact report request
     * @return a future containing the impact report
     */
    public CompletableFuture<ImpactReportResponse> getPolicyImpactReportAsync(ImpactReportRequest request) {
        return CompletableFuture.supplyAsync(() -> getPolicyImpactReport(request), asyncExecutor);
    }

    /**
     * Scans all active policies for conflicts.
     *
     * <p>Example usage:
     * <pre>{@code
     * PolicyConflictResponse conflicts = axonflow.detectPolicyConflicts();
     * System.out.println("Conflicts found: " + conflicts.getConflictCount());
     * for (PolicyConflict conflict : conflicts.getConflicts()) {
     *     System.out.println(conflict.getConflictType() + ": " + conflict.getDescription());
     * }
     * }</pre>
     *
     * <p><b>Evaluation+ Feature:</b> Requires AxonFlow Evaluation tier or higher.
     *
     * @return the conflict detection result
     * @throws AxonFlowException if the request fails
     */
    public PolicyConflictResponse detectPolicyConflicts() {
        return detectPolicyConflicts(null);
    }

    /**
     * Detects conflicts between a specific policy and other active policies,
     * or scans all policies if policyId is null.
     *
     * <p>Example usage:
     * <pre>{@code
     * PolicyConflictResponse conflicts = axonflow.detectPolicyConflicts("policy_block_pii");
     * System.out.println("Conflicts found: " + conflicts.getConflictCount());
     * for (PolicyConflict conflict : conflicts.getConflicts()) {
     *     System.out.println(conflict.getConflictType() + ": " + conflict.getDescription());
     * }
     * }</pre>
     *
     * <p><b>Evaluation+ Feature:</b> Requires AxonFlow Evaluation tier or higher.
     *
     * @param policyId the policy ID to check for conflicts, or null to scan all policies
     * @return the conflict detection result
     * @throws IllegalArgumentException if policyId is non-null and empty
     * @throws AxonFlowException if the request fails
     */
    public PolicyConflictResponse detectPolicyConflicts(String policyId) {
        if (policyId != null && policyId.isEmpty()) {
            throw new IllegalArgumentException("policyId cannot be empty");
        }

        return retryExecutor.execute(() -> {
            Object body;
            if (policyId != null) {
                body = java.util.Map.of("policy_id", policyId);
            } else {
                body = java.util.Map.of();
            }
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/policies/conflicts", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), PolicyConflictResponse.class);
                }
                return objectMapper.treeToValue(node, PolicyConflictResponse.class);
            }
        }, "detectPolicyConflicts");
    }

    /**
     * Asynchronously scans all active policies for conflicts.
     *
     * @return a future containing the conflict detection result
     */
    public CompletableFuture<PolicyConflictResponse> detectPolicyConflictsAsync() {
        return CompletableFuture.supplyAsync(() -> detectPolicyConflicts(), asyncExecutor);
    }

    /**
     * Asynchronously detects conflicts between a specific policy and other active policies.
     *
     * @param policyId the policy ID to check for conflicts, or null to scan all policies
     * @return a future containing the conflict detection result
     */
    public CompletableFuture<PolicyConflictResponse> detectPolicyConflictsAsync(String policyId) {
        return CompletableFuture.supplyAsync(() -> detectPolicyConflicts(policyId), asyncExecutor);
    }

    // ========================================================================
    // Proxy Mode - Query Execution
    // ========================================================================

    /**
     * Sends a query through AxonFlow with full policy enforcement (Proxy Mode).
     *
     * <p>This is Proxy Mode - AxonFlow acts as an intermediary, making the LLM call on your behalf.
     *
     * <p>Use this when you want AxonFlow to:
     * <ul>
     *   <li>Evaluate policies before the LLM call</li>
     *   <li>Make the LLM call to the configured provider</li>
     *   <li>Filter/redact sensitive data from responses</li>
     *   <li>Automatically track costs and audit the interaction</li>
     * </ul>
     *
     * <p>For Gateway Mode (lower latency, you make the LLM call), use:
     * <ul>
     *   <li>{@link #getPolicyApprovedContext} before your LLM call</li>
     *   <li>{@link #auditLLMCall} after your LLM call</li>
     * </ul>
     *
     * @param request the client request
     * @return the response from AxonFlow
     * @throws PolicyViolationException if the request is blocked by policy
     * @throws AuthenticationException  if authentication fails
     */
    public ClientResponse proxyLLMCall(ClientRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        // Auto-populate clientId from config if not set in request (matches Go/Python/TypeScript SDK behavior)
        ClientRequest effectiveRequest = request;
        if ((request.getClientId() == null || request.getClientId().isEmpty())
                && config.getClientId() != null && !config.getClientId().isEmpty()) {
            effectiveRequest = ClientRequest.builder()
                .query(request.getQuery())
                .userToken(request.getUserToken())
                .clientId(config.getClientId())
                .requestType(request.getRequestType() != null
                    ? RequestType.fromValue(request.getRequestType())
                    : RequestType.CHAT)
                .context(request.getContext())
                .llmProvider(request.getLlmProvider())
                .model(request.getModel())
                .media(request.getMedia())
                .build();
        }

        final ClientRequest finalRequest = effectiveRequest;

        // Media requests must not be cached — binary content makes cache keys unreliable
        boolean hasMedia = finalRequest.getMedia() != null && !finalRequest.getMedia().isEmpty();

        // Check cache first (skip for media requests)
        String cacheKey = ResponseCache.generateKey(
            finalRequest.getRequestType(),
            finalRequest.getQuery(),
            finalRequest.getUserToken()
        );

        if (!hasMedia) {
            java.util.Optional<ClientResponse> cached = cache.get(cacheKey, ClientResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        ClientResponse response = retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/request", finalRequest);
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
        }, "proxyLLMCall");

        // Cache successful responses (skip for media requests)
        if (!hasMedia && response.isSuccess() && !response.isBlocked()) {
            cache.put(cacheKey, response);
        }

        return response;
    }

    /**
     * Asynchronously sends a query through AxonFlow with full policy enforcement (Proxy Mode).
     *
     * @param request the client request
     * @return a future containing the response
     * @see #proxyLLMCall(ClientRequest)
     */
    public CompletableFuture<ClientResponse> proxyLLMCallAsync(ClientRequest request) {
        return CompletableFuture.supplyAsync(() -> proxyLLMCall(request), asyncExecutor);
    }

    // ========================================================================
    // Multi-Agent Planning (MAP)
    // ========================================================================

    /**
     * Generates a multi-agent plan for a complex task.
     *
     * <p>This method uses the Agent API with request_type "multi-agent-plan"
     * to generate and execute plans through the governance layer.
     *
     * @param request the plan request
     * @return the generated plan
     * @throws PlanExecutionException if plan generation fails
     */
    public PlanResponse generatePlan(PlanRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            // Build agent request format - use HashMap to allow null-safe values
            String userToken = request.getUserToken();
            if (userToken == null) {
                userToken = config.getClientId() != null ? config.getClientId() : "default";
            }
            String clientId = config.getClientId() != null ? config.getClientId() : "default";
            String domain = request.getDomain() != null ? request.getDomain() : "generic";

            Map<String, Object> agentRequest = new java.util.HashMap<>();
            agentRequest.put("query", request.getObjective());
            agentRequest.put("user_token", userToken);
            agentRequest.put("client_id", clientId);
            agentRequest.put("request_type", "multi-agent-plan");
            agentRequest.put("context", Map.of("domain", domain));

            Request httpRequest = buildRequest("POST", "/api/request", agentRequest);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parsePlanResponse(response, request.getDomain());
            }
        }, "generatePlan");
    }

    /**
     * Parses the Agent API response format into PlanResponse.
     * The Agent API returns: {success, plan_id, data: {steps, domain, ...}, metadata, result}
     */
    @SuppressWarnings("unchecked")
    private PlanResponse parsePlanResponse(Response response, String requestDomain) throws IOException {
        handleErrorResponse(response);

        ResponseBody body = response.body();
        if (body == null) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        String json = body.string();
        Map<String, Object> agentResponse = objectMapper.readValue(json,
            new TypeReference<Map<String, Object>>() {});

        // Check for errors
        Boolean success = (Boolean) agentResponse.get("success");
        if (success == null || !success) {
            String error = (String) agentResponse.get("error");
            throw new PlanExecutionException(error != null ? error : "Plan generation failed");
        }

        // Extract fields from Agent API response format
        String planId = (String) agentResponse.get("plan_id");
        Map<String, Object> data = (Map<String, Object>) agentResponse.get("data");
        Map<String, Object> metadata = (Map<String, Object>) agentResponse.get("metadata");
        String result = (String) agentResponse.get("result");

        // Extract nested fields from data
        List<PlanStep> steps = Collections.emptyList();
        String domain = requestDomain != null ? requestDomain : "generic";
        Integer complexity = null;
        Boolean parallel = null;
        String estimatedDuration = null;

        if (data != null) {
            // Parse steps if present
            List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) data.get("steps");
            if (rawSteps != null) {
                steps = rawSteps.stream()
                    .map(stepMap -> objectMapper.convertValue(stepMap, PlanStep.class))
                    .collect(java.util.stream.Collectors.toList());
            }
            domain = data.get("domain") != null ? (String) data.get("domain") : domain;
            complexity = data.get("complexity") != null ? ((Number) data.get("complexity")).intValue() : null;
            parallel = (Boolean) data.get("parallel");
            estimatedDuration = (String) data.get("estimated_duration");
        }

        return new PlanResponse(planId, steps, domain, complexity, parallel,
            estimatedDuration, metadata, null, result);
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
        return executePlan(planId, null);
    }

    /**
     * Executes a previously generated plan with an explicit user token.
     *
     * @param planId the ID of the plan to execute
     * @param userToken the user token (JWT) for authentication; if null, defaults to clientId
     * @return the execution result
     * @throws PlanExecutionException if execution fails
     */
    public PlanResponse executePlan(String planId, String userToken) {
        Objects.requireNonNull(planId, "planId cannot be null");

        // executePlan is a mutation — do NOT retry (retrying causes 409 "Plan has already been executed")
        try {
            // Build agent request format - like generatePlan but with request_type "execute-plan"
            String token = userToken != null ? userToken : (config.getClientId() != null ? config.getClientId() : "default");
            String clientId = config.getClientId() != null ? config.getClientId() : "default";

            Map<String, Object> agentRequest = new java.util.HashMap<>();
            agentRequest.put("query", "");
            agentRequest.put("user_token", token);
            agentRequest.put("client_id", clientId);
            agentRequest.put("request_type", "execute-plan");
            agentRequest.put("context", Map.of("plan_id", planId));

            Request httpRequest = buildRequest("POST", "/api/request", agentRequest);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseExecutePlanResponse(response, planId);
            }
        } catch (AxonFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new PlanExecutionException("executePlan failed: " + e.getMessage(), planId, null, e);
        }
    }

    /**
     * Parses the execute plan response.
     */
    @SuppressWarnings("unchecked")
    private PlanResponse parseExecutePlanResponse(Response response, String planId) throws IOException {
        handleErrorResponse(response);

        ResponseBody body = response.body();
        if (body == null) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        String json = body.string();
        Map<String, Object> agentResponse = objectMapper.readValue(json,
            new TypeReference<Map<String, Object>>() {});

        // Check for errors (outer response)
        Boolean success = (Boolean) agentResponse.get("success");

        // Detect nested data.success=false (agent wraps orchestrator errors)
        Object dataObj = agentResponse.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            Boolean dataSuccess = (Boolean) dataMap.get("success");
            if (dataSuccess != null && !dataSuccess) {
                success = false;
                String dataError = (String) dataMap.get("error");
                if (dataError != null) {
                    throw new PlanExecutionException(dataError);
                }
            }
        }

        if (success == null || !success) {
            String error = (String) agentResponse.get("error");
            throw new PlanExecutionException(error != null ? error : "Plan execution failed");
        }

        // Extract result - this is the completed plan output
        String result = (String) agentResponse.get("result");

        // Read status from response data (e.g., "awaiting_approval" for confirm mode)
        // Precedence: data.status > metadata.status > top-level status > "completed"
        String status = "completed";
        Object dataObj2 = agentResponse.get("data");
        if (dataObj2 instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dm = (Map<String, Object>) dataObj2;
            Object dataStatus = dm.get("status");
            if (dataStatus instanceof String && !((String) dataStatus).isEmpty()) {
                status = (String) dataStatus;
            }
        }
        if ("completed".equals(status)) {
            Object metaObj = agentResponse.get("metadata");
            if (metaObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metaMap = (Map<String, Object>) metaObj;
                Object metaStatus = metaMap.get("status");
                if (metaStatus instanceof String && !((String) metaStatus).isEmpty()) {
                    status = (String) metaStatus;
                }
            }
        }
        if ("completed".equals(status)) {
            Object topStatus = agentResponse.get("status");
            if (topStatus instanceof String && !((String) topStatus).isEmpty()) {
                status = (String) topStatus;
            }
        }

        // Build response with execution status
        return new PlanResponse(planId, Collections.emptyList(), null, null, null,
            null, null, status, result);
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
                "/api/v1/plan/" + planId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PlanResponse.class);
            }
        }, "getPlanStatus");
    }

    /**
     * Generates a multi-agent plan with additional options.
     *
     * <p>This overload allows specifying execution mode and other generation
     * options beyond what is in the base {@link PlanRequest}.
     *
     * @param request the plan request
     * @param options additional generation options
     * @return the generated plan
     * @throws PlanExecutionException if plan generation fails
     */
    public PlanResponse generatePlan(PlanRequest request, GeneratePlanOptions options) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        return retryExecutor.execute(() -> {
            // Build agent request format - use HashMap to allow null-safe values
            String userToken = request.getUserToken();
            if (userToken == null) {
                userToken = config.getClientId() != null ? config.getClientId() : "default";
            }
            String clientId = config.getClientId() != null ? config.getClientId() : "default";
            String domain = request.getDomain() != null ? request.getDomain() : "generic";

            Map<String, Object> context = new java.util.HashMap<>();
            context.put("domain", domain);
            if (options.getExecutionMode() != null) {
                context.put("execution_mode", options.getExecutionMode().getValue());
            }

            Map<String, Object> agentRequest = new java.util.HashMap<>();
            agentRequest.put("query", request.getObjective());
            agentRequest.put("user_token", userToken);
            agentRequest.put("client_id", clientId);
            agentRequest.put("request_type", "multi-agent-plan");
            agentRequest.put("context", context);

            Request httpRequest = buildRequest("POST", "/api/request", agentRequest);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parsePlanResponse(response, request.getDomain());
            }
        }, "generatePlan");
    }

    /**
     * Cancels a running or pending plan.
     *
     * @param planId the ID of the plan to cancel
     * @param reason an optional reason for the cancellation
     * @return the cancellation result
     */
    public CancelPlanResponse cancelPlan(String planId, String reason) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = new java.util.HashMap<>();
            if (reason != null) {
                body.put("reason", reason);
            }

            Request httpRequest = buildRequest("POST",
                "/api/v1/plan/" + planId + "/cancel", body.isEmpty() ? null : body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, CancelPlanResponse.class);
            }
        }, "cancelPlan");
    }

    /**
     * Cancels a running or pending plan without specifying a reason.
     *
     * @param planId the ID of the plan to cancel
     * @return the cancellation result
     */
    public CancelPlanResponse cancelPlan(String planId) {
        return cancelPlan(planId, null);
    }

    /**
     * Updates a plan with optimistic concurrency control.
     *
     * <p>The request must include the expected version number. If the version
     * does not match the current server version, a {@link VersionConflictException}
     * is thrown.
     *
     * @param planId  the ID of the plan to update
     * @param request the update request with version and changes
     * @return the update result
     * @throws VersionConflictException if the plan version has changed
     */
    public UpdatePlanResponse updatePlan(String planId, UpdatePlanRequest request) {
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        try {
            return retryExecutor.execute(() -> {
                Request httpRequest = buildRequest("PUT",
                    "/api/v1/plan/" + planId, request);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseResponse(response, UpdatePlanResponse.class);
                }
            }, "updatePlan");
        } catch (AxonFlowException e) {
            if (e.getStatusCode() == 409) {
                throw new VersionConflictException(
                    e.getMessage(), planId, request.getVersion(), null);
            }
            throw e;
        }
    }

    /**
     * Gets the version history of a plan.
     *
     * @param planId the plan ID
     * @return the version history
     */
    public PlanVersionsResponse getPlanVersions(String planId) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET",
                "/api/v1/plan/" + planId + "/versions", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PlanVersionsResponse.class);
            }
        }, "getPlanVersions");
    }

    /**
     * Resumes a paused plan, optionally approving or rejecting it.
     *
     * @param planId   the ID of the plan to resume
     * @param approved whether to approve the plan to continue (true) or reject it (false)
     * @return the resume result
     */
    public ResumePlanResponse resumePlan(String planId, Boolean approved) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("approved", approved != null ? approved : true);

            Request httpRequest = buildRequest("POST",
                "/api/v1/plan/" + planId + "/resume", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ResumePlanResponse.class);
            }
        }, "resumePlan");
    }

    /**
     * Resumes a paused plan with approval (default).
     *
     * <p>This is equivalent to calling {@code resumePlan(planId, true)}.
     *
     * @param planId the ID of the plan to resume
     * @return the resume result
     */
    public ResumePlanResponse resumePlan(String planId) {
        return resumePlan(planId, true);
    }

    /**
     * Rolls back a plan to a previous version.
     *
     * @param planId        the ID of the plan to roll back
     * @param targetVersion the version number to roll back to
     * @return the rollback result
     * @throws AxonFlowException if the rollback fails
     */
    public RollbackPlanResponse rollbackPlan(String planId, int targetVersion) {
        Objects.requireNonNull(planId, "planId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST",
                "/api/v1/plan/" + planId + "/rollback/" + targetVersion, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, RollbackPlanResponse.class);
            }
        }, "rollbackPlan");
    }

    /**
     * Asynchronously rolls back a plan to a previous version.
     *
     * @param planId        the ID of the plan to roll back
     * @param targetVersion the version number to roll back to
     * @return a future containing the rollback result
     */
    public CompletableFuture<RollbackPlanResponse> rollbackPlanAsync(String planId, int targetVersion) {
        return CompletableFuture.supplyAsync(() -> rollbackPlan(planId, targetVersion), asyncExecutor);
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
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/connectors", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Response is wrapped: {"connectors": [...], "total": N}
                JsonNode node = parseResponseNode(response);
                if (node.has("connectors")) {
                    return objectMapper.convertValue(
                        node.get("connectors"),
                        new TypeReference<List<ConnectorInfo>>() {}
                    );
                }
                return objectMapper.convertValue(node, new TypeReference<List<ConnectorInfo>>() {});
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
                "config", config != null ? config : Map.of()
            );
            String path = "/api/v1/connectors/" + connectorId + "/install";
            Request httpRequest = buildOrchestratorRequest("POST", path, body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ConnectorInfo.class);
            }
        }, "installConnector");
    }

    /**
     * Uninstalls an MCP connector.
     *
     * @param connectorName the name of the connector to uninstall
     */
    public void uninstallConnector(String connectorName) {
        Objects.requireNonNull(connectorName, "connectorName cannot be null");

        retryExecutor.execute(() -> {
            String path = "/api/v1/connectors/" + connectorName;
            Request httpRequest = buildOrchestratorRequest("DELETE", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "uninstallConnector");
    }

    /**
     * Gets details for a specific connector by ID.
     *
     * @param connectorId the connector ID
     * @return the connector info
     */
    public ConnectorInfo getConnector(String connectorId) {
        Objects.requireNonNull(connectorId, "connectorId cannot be null");

        return retryExecutor.execute(() -> {
            String path = "/api/v1/connectors/" + connectorId;
            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ConnectorInfo.class);
            }
        }, "getConnector");
    }

    /**
     * Asynchronously gets details for a specific connector by ID.
     *
     * @param connectorId the connector ID
     * @return a future containing the connector info
     */
    public CompletableFuture<ConnectorInfo> getConnectorAsync(String connectorId) {
        return CompletableFuture.supplyAsync(() -> getConnector(connectorId), asyncExecutor);
    }

    /**
     * Gets the health status of an installed connector.
     *
     * @param connectorId the connector ID
     * @return the health status
     */
    public ConnectorHealthStatus getConnectorHealth(String connectorId) {
        Objects.requireNonNull(connectorId, "connectorId cannot be null");

        return retryExecutor.execute(() -> {
            String path = "/api/v1/connectors/" + connectorId + "/health";
            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ConnectorHealthStatus.class);
            }
        }, "getConnectorHealth");
    }

    /**
     * Asynchronously gets the health status of an installed connector.
     *
     * @param connectorId the connector ID
     * @return a future containing the health status
     */
    public CompletableFuture<ConnectorHealthStatus> getConnectorHealthAsync(String connectorId) {
        return CompletableFuture.supplyAsync(() -> getConnectorHealth(connectorId), asyncExecutor);
    }

    /**
     * Queries an MCP connector.
     *
     * <p>This method sends the query to the AxonFlow Agent using the standard
     * request format with request_type: "mcp-query", which is routed to the
     * configured MCP connector.
     *
     * @param query the connector query
     * @return the query response
     * @throws ConnectorException if the query fails
     */
    public ConnectorResponse queryConnector(ConnectorQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return retryExecutor.execute(() -> {
            // Build a ClientRequest with MCP_QUERY request type
            // This follows the same pattern as Go and TypeScript SDKs
            Map<String, Object> context = new HashMap<>();
            context.put("connector", query.getConnectorId());
            if (query.getParameters() != null && !query.getParameters().isEmpty()) {
                context.put("params", query.getParameters());
            }

            String clientId = config.getClientId();

            ClientRequest clientRequest = ClientRequest.builder()
                .query(query.getOperation())
                .userToken(query.getUserToken() != null ? query.getUserToken() : clientId)
                .clientId(clientId)
                .requestType(RequestType.MCP_QUERY)
                .context(context)
                .build();

            Request httpRequest = buildRequest("POST", "/api/request", clientRequest);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                ClientResponse clientResponse = parseResponse(response, ClientResponse.class);

                // Convert ClientResponse to ConnectorResponse
                ConnectorResponse result = new ConnectorResponse(
                    clientResponse.isSuccess(),
                    clientResponse.getData(),
                    clientResponse.getError(),
                    query.getConnectorId(),
                    query.getOperation(),
                    null,  // processingTime not available from ClientResponse
                    false, // redacted - not available from this endpoint
                    null,  // redactedFields - not available from this endpoint
                    null   // policyInfo - not available from this endpoint
                );

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

    /**
     * Executes a query directly against the MCP connector endpoint.
     *
     * <p>This method calls the agent's /mcp/resources/query endpoint which provides:
     * <ul>
     *   <li>Request-phase policy evaluation (SQLi blocking, PII blocking)</li>
     *   <li>Response-phase policy evaluation (PII redaction)</li>
     *   <li>PolicyInfo metadata in responses</li>
     * </ul>
     *
     * <p>Example usage:
     * <pre>
     * ConnectorResponse response = axonflow.mcpQuery("postgres", "SELECT * FROM customers LIMIT 10");
     * if (response.isRedacted()) {
     *     System.out.println("Fields redacted: " + response.getRedactedFields());
     * }
     * System.out.println("Policies evaluated: " + response.getPolicyInfo().getPoliciesEvaluated());
     * </pre>
     *
     * @param connector name of the MCP connector (e.g., "postgres")
     * @param statement SQL statement or query to execute
     * @return ConnectorResponse with data, redaction info, and policy_info
     * @throws ConnectorException if the request is blocked by policy or fails
     */
    public ConnectorResponse mcpQuery(String connector, String statement) {
        return mcpQuery(connector, statement, null);
    }

    /**
     * Executes a query directly against the MCP connector endpoint with options.
     *
     * @param connector name of the MCP connector (e.g., "postgres")
     * @param statement SQL statement or query to execute
     * @param options optional additional options for the query
     * @return ConnectorResponse with data, redaction info, and policy_info
     * @throws ConnectorException if the request is blocked by policy or fails
     */
    public ConnectorResponse mcpQuery(String connector, String statement, Map<String, Object> options) {
        Objects.requireNonNull(connector, "connector cannot be null");
        Objects.requireNonNull(statement, "statement cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("connector", connector);
            body.put("statement", statement);
            if (options != null && !options.isEmpty()) {
                body.put("options", options);
            }

            Request httpRequest = buildRequest("POST", "/mcp/resources/query", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Parse the response body
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new ConnectorException("Empty response from MCP query", connector, "mcpQuery");
                }
                String responseJson = responseBody.string();

                // Handle policy blocks (403 responses)
                if (!response.isSuccessful()) {
                    try {
                        Map<String, Object> errorData = objectMapper.readValue(responseJson,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        String errorMsg = errorData.get("error") != null ?
                            errorData.get("error").toString() :
                            "MCP query failed: " + response.code();
                        throw new ConnectorException(errorMsg, connector, "mcpQuery");
                    } catch (JsonProcessingException e) {
                        throw new ConnectorException("MCP query failed: " + response.code(), connector, "mcpQuery");
                    }
                }

                return objectMapper.readValue(responseJson, ConnectorResponse.class);
            }
        }, "mcpQuery");
    }

    /**
     * Asynchronously executes a query against the MCP connector endpoint.
     *
     * @param connector name of the MCP connector
     * @param statement SQL statement to execute
     * @return a future containing the response
     */
    public CompletableFuture<ConnectorResponse> mcpQueryAsync(String connector, String statement) {
        return CompletableFuture.supplyAsync(() -> mcpQuery(connector, statement), asyncExecutor);
    }

    /**
     * Asynchronously executes a query against the MCP connector endpoint with options.
     *
     * @param connector name of the MCP connector
     * @param statement SQL statement to execute
     * @param options optional additional options
     * @return a future containing the response
     */
    public CompletableFuture<ConnectorResponse> mcpQueryAsync(String connector, String statement, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> mcpQuery(connector, statement, options), asyncExecutor);
    }

    /**
     * Executes a statement against an MCP connector (alias for mcpQuery).
     *
     * @param connector name of the MCP connector
     * @param statement SQL statement to execute
     * @return ConnectorResponse with data, redaction info, and policy_info
     */
    public ConnectorResponse mcpExecute(String connector, String statement) {
        return mcpQuery(connector, statement);
    }

    // ========================================================================
    // MCP Policy Check (Standalone)
    // ========================================================================

    /**
     * Validates an MCP input statement against configured policies without executing it.
     *
     * <p>This method calls the agent's {@code /api/v1/mcp/check-input} endpoint to pre-validate
     * a statement before sending it to the connector. Useful for checking SQL injection
     * patterns, blocked operations, and input policy violations.</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * MCPCheckInputResponse result = axonflow.mcpCheckInput("postgres", "SELECT * FROM users");
     * if (!result.isAllowed()) {
     *     System.out.println("Blocked: " + result.getBlockReason());
     * }
     * }</pre>
     *
     * @param connectorType name of the MCP connector type (e.g., "postgres")
     * @param statement     the statement to validate
     * @return MCPCheckInputResponse with allowed status, block reason, and policy info
     * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
     */
    public MCPCheckInputResponse mcpCheckInput(String connectorType, String statement) {
        return mcpCheckInput(connectorType, statement, null);
    }

    /**
     * Validates an MCP input statement against configured policies with options.
     *
     * @param connectorType name of the MCP connector type (e.g., "postgres")
     * @param statement     the statement to validate
     * @param options       optional parameters: "operation" (String), "parameters" (Map)
     * @return MCPCheckInputResponse with allowed status, block reason, and policy info
     * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
     */
    public MCPCheckInputResponse mcpCheckInput(String connectorType, String statement, Map<String, Object> options) {
        Objects.requireNonNull(connectorType, "connectorType cannot be null");
        Objects.requireNonNull(statement, "statement cannot be null");

        return retryExecutor.execute(() -> {
            MCPCheckInputRequest request;
            if (options != null) {
                String operation = (String) options.getOrDefault("operation", "execute");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) options.get("parameters");
                request = new MCPCheckInputRequest(connectorType, statement, parameters, operation);
            } else {
                request = new MCPCheckInputRequest(connectorType, statement);
            }

            Request httpRequest = buildRequest("POST", "/api/v1/mcp/check-input", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new ConnectorException("Empty response from MCP check-input", connectorType, "mcpCheckInput");
                }
                String responseJson = responseBody.string();

                // 403 means policy blocked — the body is still a valid response
                if (!response.isSuccessful() && response.code() != 403) {
                    try {
                        Map<String, Object> errorData = objectMapper.readValue(responseJson,
                            new TypeReference<Map<String, Object>>() {});
                        String errorMsg = errorData.get("error") != null ?
                            errorData.get("error").toString() :
                            "MCP check-input failed: " + response.code();
                        throw new ConnectorException(errorMsg, connectorType, "mcpCheckInput");
                    } catch (JsonProcessingException e) {
                        throw new ConnectorException("MCP check-input failed: " + response.code(), connectorType, "mcpCheckInput");
                    }
                }

                return objectMapper.readValue(responseJson, MCPCheckInputResponse.class);
            }
        }, "mcpCheckInput");
    }

    /**
     * Asynchronously validates an MCP input statement against configured policies.
     *
     * @param connectorType name of the MCP connector type
     * @param statement     the statement to validate
     * @return a future containing the check result
     */
    public CompletableFuture<MCPCheckInputResponse> mcpCheckInputAsync(String connectorType, String statement) {
        return CompletableFuture.supplyAsync(() -> mcpCheckInput(connectorType, statement), asyncExecutor);
    }

    /**
     * Asynchronously validates an MCP input statement against configured policies with options.
     *
     * @param connectorType name of the MCP connector type
     * @param statement     the statement to validate
     * @param options       optional parameters
     * @return a future containing the check result
     */
    public CompletableFuture<MCPCheckInputResponse> mcpCheckInputAsync(String connectorType, String statement, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> mcpCheckInput(connectorType, statement, options), asyncExecutor);
    }

    /**
     * Validates MCP response data against configured policies.
     *
     * <p>This method calls the agent's {@code /api/v1/mcp/check-output} endpoint to check
     * response data for PII content, exfiltration limit violations, and other output
     * policy violations. If PII redaction is active, {@code redactedData} contains the
     * sanitized version.</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * List<Map<String, Object>> rows = List.of(
     *     Map.of("name", "John", "ssn", "123-45-6789")
     * );
     * MCPCheckOutputResponse result = axonflow.mcpCheckOutput("postgres", rows);
     * if (!result.isAllowed()) {
     *     System.out.println("Blocked: " + result.getBlockReason());
     * }
     * if (result.getRedactedData() != null) {
     *     System.out.println("Redacted: " + result.getRedactedData());
     * }
     * }</pre>
     *
     * @param connectorType name of the MCP connector type (e.g., "postgres")
     * @param responseData  the response data rows to validate
     * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
     * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
     */
    public MCPCheckOutputResponse mcpCheckOutput(String connectorType, List<Map<String, Object>> responseData) {
        return mcpCheckOutput(connectorType, responseData, null);
    }

    /**
     * Validates MCP response data against configured policies with options.
     *
     * @param connectorType name of the MCP connector type (e.g., "postgres")
     * @param responseData  the response data rows to validate
     * @param options       optional parameters: "message" (String), "metadata" (Map), "row_count" (int)
     * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
     * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
     */
    public MCPCheckOutputResponse mcpCheckOutput(String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
        Objects.requireNonNull(connectorType, "connectorType cannot be null");
        // responseData can be null for execute-style requests that use message instead

        return retryExecutor.execute(() -> {
            String message = options != null ? (String) options.get("message") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = options != null ? (Map<String, Object>) options.get("metadata") : null;
            int rowCount = options != null ? (int) options.getOrDefault("row_count", 0) : 0;

            MCPCheckOutputRequest request = new MCPCheckOutputRequest(connectorType, responseData, message, metadata, rowCount);

            Request httpRequest = buildRequest("POST", "/api/v1/mcp/check-output", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new ConnectorException("Empty response from MCP check-output", connectorType, "mcpCheckOutput");
                }
                String responseJson = responseBody.string();

                // 403 means policy blocked — the body is still a valid response
                if (!response.isSuccessful() && response.code() != 403) {
                    try {
                        Map<String, Object> errorData = objectMapper.readValue(responseJson,
                            new TypeReference<Map<String, Object>>() {});
                        String errorMsg = errorData.get("error") != null ?
                            errorData.get("error").toString() :
                            "MCP check-output failed: " + response.code();
                        throw new ConnectorException(errorMsg, connectorType, "mcpCheckOutput");
                    } catch (JsonProcessingException e) {
                        throw new ConnectorException("MCP check-output failed: " + response.code(), connectorType, "mcpCheckOutput");
                    }
                }

                return objectMapper.readValue(responseJson, MCPCheckOutputResponse.class);
            }
        }, "mcpCheckOutput");
    }

    /**
     * Asynchronously validates MCP response data against configured policies.
     *
     * @param connectorType name of the MCP connector type
     * @param responseData  the response data rows to validate
     * @return a future containing the check result
     */
    public CompletableFuture<MCPCheckOutputResponse> mcpCheckOutputAsync(String connectorType, List<Map<String, Object>> responseData) {
        return CompletableFuture.supplyAsync(() -> mcpCheckOutput(connectorType, responseData), asyncExecutor);
    }

    /**
     * Asynchronously validates MCP response data against configured policies with options.
     *
     * @param connectorType name of the MCP connector type
     * @param responseData  the response data rows to validate
     * @param options       optional parameters
     * @return a future containing the check result
     */
    public CompletableFuture<MCPCheckOutputResponse> mcpCheckOutputAsync(String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> mcpCheckOutput(connectorType, responseData, options), asyncExecutor);
    }

    // ========================================================================
    // Policy CRUD - Static Policies
    // ========================================================================

    /**
     * Lists static policies with optional filtering.
     *
     * @return list of static policies
     */
    public List<StaticPolicy> listStaticPolicies() {
        return listStaticPolicies((ListStaticPoliciesOptions) null);
    }

    /**
     * Lists static policies with filtering options.
     *
     * @param options filtering options
     * @return list of static policies
     */
    public List<StaticPolicy> listStaticPolicies(ListStaticPoliciesOptions options) {
        return retryExecutor.execute(() -> {
            String path = buildPolicyQueryString("/api/v1/static-policies", options);
            Request httpRequest = buildRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                StaticPoliciesResponse wrapper = parseResponse(response, StaticPoliciesResponse.class);
                // Handle null wrapper or null policies list (Issue #40)
                if (wrapper == null || wrapper.getPolicies() == null) {
                    return java.util.Collections.emptyList();
                }
                return wrapper.getPolicies();
            }
        }, "listStaticPolicies");
    }

    /**
     * Lists static policies filtered by tier and organization ID (Enterprise).
     *
     * @param tier           the policy tier
     * @param organizationId the organization ID
     * @return list of static policies
     */
    public List<StaticPolicy> listStaticPolicies(PolicyTier tier, String organizationId) {
        return listStaticPolicies(ListStaticPoliciesOptions.builder()
                .tier(tier)
                .organizationId(organizationId)
                .build());
    }

    /**
     * Lists static policies filtered by tier and category.
     *
     * @param tier     the policy tier
     * @param category the policy category
     * @return list of static policies
     */
    public List<StaticPolicy> listStaticPolicies(PolicyTier tier, PolicyCategory category) {
        return listStaticPolicies(ListStaticPoliciesOptions.builder()
                .tier(tier)
                .category(category)
                .build());
    }

    /**
     * Lists static policies filtered by category.
     *
     * @param category the policy category
     * @return list of static policies
     */
    public List<StaticPolicy> listStaticPolicies(PolicyCategory category) {
        return listStaticPolicies(ListStaticPoliciesOptions.builder()
                .category(category)
                .build());
    }

    /**
     * Gets a specific static policy by ID.
     *
     * @param policyId the policy ID
     * @return the static policy
     */
    public StaticPolicy getStaticPolicy(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/static-policies/" + policyId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, StaticPolicy.class);
            }
        }, "getStaticPolicy");
    }

    /**
     * Creates a new static policy.
     *
     * @param request the create request
     * @return the created policy
     */
    public StaticPolicy createStaticPolicy(CreateStaticPolicyRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/static-policies", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, StaticPolicy.class);
            }
        }, "createStaticPolicy");
    }

    /**
     * Updates an existing static policy.
     *
     * @param policyId the policy ID
     * @param request  the update request
     * @return the updated policy
     */
    public StaticPolicy updateStaticPolicy(String policyId, UpdateStaticPolicyRequest request) {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("PUT", "/api/v1/static-policies/" + policyId, request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, StaticPolicy.class);
            }
        }, "updateStaticPolicy");
    }

    /**
     * Deletes a static policy.
     *
     * @param policyId the policy ID
     */
    public void deleteStaticPolicy(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("DELETE", "/api/v1/static-policies/" + policyId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deleteStaticPolicy");
    }

    /**
     * Toggles a static policy's enabled status.
     *
     * @param policyId the policy ID
     * @param enabled  the new enabled status
     * @return the updated policy
     */
    public StaticPolicy toggleStaticPolicy(String policyId, boolean enabled) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = Map.of("enabled", enabled);
            Request httpRequest = buildPatchRequest("/api/v1/static-policies/" + policyId, body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, StaticPolicy.class);
            }
        }, "toggleStaticPolicy");
    }

    /**
     * Gets effective static policies after inheritance and overrides.
     *
     * @return list of effective policies
     */
    public List<StaticPolicy> getEffectiveStaticPolicies() {
        return getEffectiveStaticPolicies((EffectivePoliciesOptions) null);
    }

    /**
     * Gets effective static policies filtered by category.
     *
     * @param category the policy category
     * @return list of effective policies
     */
    public List<StaticPolicy> getEffectiveStaticPolicies(PolicyCategory category) {
        return getEffectiveStaticPolicies(EffectivePoliciesOptions.builder()
                .category(category)
                .build());
    }

    /**
     * Gets effective static policies with options.
     *
     * @param options filtering options
     * @return list of effective policies
     */
    public List<StaticPolicy> getEffectiveStaticPolicies(EffectivePoliciesOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/static-policies/effective");
            if (options != null) {
                String query = buildEffectivePoliciesQuery(options);
                if (!query.isEmpty()) {
                    path.append("?").append(query);
                }
            }
            Request httpRequest = buildRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                EffectivePoliciesResponse wrapper = parseResponse(response, EffectivePoliciesResponse.class);
                // Handle null wrapper or null policies list (Issue #40)
                if (wrapper == null || wrapper.getStaticPolicies() == null) {
                    return java.util.Collections.emptyList();
                }
                return wrapper.getStaticPolicies();
            }
        }, "getEffectiveStaticPolicies");
    }

    /**
     * Tests a regex pattern against sample inputs.
     *
     * @param pattern    the regex pattern
     * @param testInputs sample inputs to test
     * @return the test result
     */
    public TestPatternResult testPattern(String pattern, List<String> testInputs) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(testInputs, "testInputs cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = Map.of(
                "pattern", pattern,
                "inputs", testInputs
            );
            Request httpRequest = buildRequest("POST", "/api/v1/static-policies/test", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, TestPatternResult.class);
            }
        }, "testPattern");
    }

    /**
     * Gets version history for a static policy.
     *
     * @param policyId the policy ID
     * @return list of policy versions
     */
    public List<PolicyVersion> getStaticPolicyVersions(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/static-policies/" + policyId + "/versions", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                Map<String, Object> wrapper = parseResponse(response, new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> versionsRaw = (List<Map<String, Object>>) wrapper.get("versions");
                if (versionsRaw == null) {
                    return new ArrayList<>();
                }
                List<PolicyVersion> versions = new ArrayList<>();
                for (Map<String, Object> v : versionsRaw) {
                    PolicyVersion version = objectMapper.convertValue(v, PolicyVersion.class);
                    versions.add(version);
                }
                return versions;
            }
        }, "getStaticPolicyVersions");
    }

    // ========================================================================
    // Policy CRUD - Overrides (Enterprise)
    // ========================================================================

    /**
     * Creates a policy override.
     *
     * @param policyId the policy ID
     * @param request  the override request
     * @return the created override
     */
    public PolicyOverride createPolicyOverride(String policyId, CreatePolicyOverrideRequest request) {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/v1/static-policies/" + policyId + "/override", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, PolicyOverride.class);
            }
        }, "createPolicyOverride");
    }

    /**
     * Deletes a policy override.
     *
     * @param policyId the policy ID
     */
    public void deletePolicyOverride(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("DELETE", "/api/v1/static-policies/" + policyId + "/override", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deletePolicyOverride");
    }

    /**
     * Lists all active policy overrides (Enterprise).
     *
     * @return list of policy overrides
     */
    public List<PolicyOverride> listPolicyOverrides() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/static-policies/overrides", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Backend returns wrapped response: {"overrides": [...], "count": N}
                Map<String, Object> wrapper = parseResponse(response, new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> overridesRaw = (List<Map<String, Object>>) wrapper.get("overrides");
                if (overridesRaw == null) {
                    return java.util.Collections.emptyList();
                }
                return overridesRaw.stream()
                    .map(raw -> objectMapper.convertValue(raw, PolicyOverride.class))
                    .collect(java.util.stream.Collectors.toList());
            }
        }, "listPolicyOverrides");
    }

    // ========================================================================
    // Policy CRUD - Dynamic Policies
    // ========================================================================

    /**
     * Lists dynamic policies.
     *
     * @return list of dynamic policies
     */
    public List<DynamicPolicy> listDynamicPolicies() {
        return listDynamicPolicies(null);
    }

    /**
     * Lists dynamic policies with filtering options.
     *
     * @param options filtering options
     * @return list of dynamic policies
     */
    public List<DynamicPolicy> listDynamicPolicies(ListDynamicPoliciesOptions options) {
        return retryExecutor.execute(() -> {
            String path = buildDynamicPolicyQueryString("/api/v1/dynamic-policies", options);
            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policies": [...]} wrapper
                DynamicPoliciesResponse wrapper = parseResponse(response, DynamicPoliciesResponse.class);
                // Handle null wrapper or null policies list (Issue #40)
                if (wrapper == null || wrapper.getPolicies() == null) {
                    return java.util.Collections.emptyList();
                }
                return wrapper.getPolicies();
            }
        }, "listDynamicPolicies");
    }

    /**
     * Gets a specific dynamic policy by ID.
     *
     * @param policyId the policy ID
     * @return the dynamic policy
     */
    public DynamicPolicy getDynamicPolicy(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/dynamic-policies/" + policyId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
                DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
                return wrapper != null ? wrapper.getPolicy() : null;
            }
        }, "getDynamicPolicy");
    }

    /**
     * Creates a new dynamic policy.
     *
     * @param request the create request
     * @return the created policy
     */
    public DynamicPolicy createDynamicPolicy(CreateDynamicPolicyRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/dynamic-policies", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
                DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
                return wrapper != null ? wrapper.getPolicy() : null;
            }
        }, "createDynamicPolicy");
    }

    /**
     * Updates an existing dynamic policy.
     *
     * @param policyId the policy ID
     * @param request  the update request
     * @return the updated policy
     */
    public DynamicPolicy updateDynamicPolicy(String policyId, UpdateDynamicPolicyRequest request) {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("PUT", "/api/v1/dynamic-policies/" + policyId, request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
                DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
                return wrapper != null ? wrapper.getPolicy() : null;
            }
        }, "updateDynamicPolicy");
    }

    /**
     * Deletes a dynamic policy.
     *
     * @param policyId the policy ID
     */
    public void deleteDynamicPolicy(String policyId) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("DELETE", "/api/v1/dynamic-policies/" + policyId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deleteDynamicPolicy");
    }

    /**
     * Toggles a dynamic policy's enabled status.
     *
     * @param policyId the policy ID
     * @param enabled  the new enabled status
     * @return the updated policy
     */
    public DynamicPolicy toggleDynamicPolicy(String policyId, boolean enabled) {
        Objects.requireNonNull(policyId, "policyId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = Map.of("enabled", enabled);
            Request httpRequest = buildOrchestratorRequest("PUT", "/api/v1/dynamic-policies/" + policyId, body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
                DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
                return wrapper != null ? wrapper.getPolicy() : null;
            }
        }, "toggleDynamicPolicy");
    }

    /**
     * Gets effective dynamic policies after inheritance.
     *
     * @return list of effective policies
     */
    public List<DynamicPolicy> getEffectiveDynamicPolicies() {
        return getEffectiveDynamicPolicies(null);
    }

    /**
     * Gets effective dynamic policies with options.
     *
     * @param options filtering options
     * @return list of effective policies
     */
    public List<DynamicPolicy> getEffectiveDynamicPolicies(EffectivePoliciesOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/dynamic-policies/effective");
            if (options != null) {
                String query = buildEffectivePoliciesQuery(options);
                if (!query.isEmpty()) {
                    path.append("?").append(query);
                }
            }
            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // Agent proxy (Issue #886) returns {"policies": [...]} wrapper
                DynamicPoliciesResponse wrapper = parseResponse(response, DynamicPoliciesResponse.class);
                // Handle null wrapper or null policies list (Issue #40)
                if (wrapper == null || wrapper.getPolicies() == null) {
                    return java.util.Collections.emptyList();
                }
                return wrapper.getPolicies();
            }
        }, "getEffectiveDynamicPolicies");
    }

    // ========================================================================
    // Unified Execution Tracking (Issue #1075 - EPIC #1074)
    // ========================================================================

    /**
     * Gets the unified execution status for a given execution ID.
     *
     * <p>This method works for both MAP plans and WCP workflows, returning
     * a consistent status format regardless of execution type.
     *
     * @param executionId the execution ID (plan ID or workflow ID)
     * @return the unified execution status
     */
    public com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus getExecutionStatus(String executionId) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/unified/executions/" + executionId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus.class);
            }
        }, "getExecutionStatus");
    }

    /**
     * Lists unified executions with optional filtering.
     *
     * @param request filter options
     * @return paginated list of executions
     */
    public com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsResponse listUnifiedExecutions(
            com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsRequest request) {

        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/unified/executions");
            if (request != null) {
                StringBuilder params = new StringBuilder();
                if (request.getExecutionType() != null) {
                    params.append("execution_type=").append(request.getExecutionType().getValue());
                }
                if (request.getStatus() != null) {
                    if (params.length() > 0) params.append("&");
                    params.append("status=").append(request.getStatus().getValue());
                }
                if (request.getTenantId() != null) {
                    if (params.length() > 0) params.append("&");
                    params.append("tenant_id=").append(request.getTenantId());
                }
                if (request.getOrgId() != null) {
                    if (params.length() > 0) params.append("&");
                    params.append("org_id=").append(request.getOrgId());
                }
                if (request.getLimit() > 0) {
                    if (params.length() > 0) params.append("&");
                    params.append("limit=").append(request.getLimit());
                }
                if (request.getOffset() > 0) {
                    if (params.length() > 0) params.append("&");
                    params.append("offset=").append(request.getOffset());
                }
                if (params.length() > 0) {
                    path.append("?").append(params);
                }
            }
            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsResponse.class);
            }
        }, "listUnifiedExecutions");
    }

    /**
     * Cancels a unified execution (MAP plan or WCP workflow).
     *
     * <p>This method cancels an execution via the unified execution API,
     * automatically propagating to the correct subsystem (MAP or WCP).
     *
     * @param executionId the execution ID (plan ID or workflow ID)
     * @param reason optional reason for cancellation
     */
    public void cancelExecution(String executionId, String reason) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        retryExecutor.execute(() -> {
            Map<String, String> body = reason != null ?
                Collections.singletonMap("reason", reason) : Collections.emptyMap();
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/unified/executions/" + executionId + "/cancel", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "cancelExecution");
    }

    /**
     * Cancels a unified execution without a reason.
     *
     * @param executionId the execution ID
     */
    public void cancelExecution(String executionId) {
        cancelExecution(executionId, null);
    }

    /**
     * Streams real-time execution status updates via Server-Sent Events (SSE).
     *
     * <p>Connects to the SSE streaming endpoint and invokes the callback with each
     * {@link com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus} update
     * as it arrives. The stream automatically closes when the execution reaches a
     * terminal state (completed, failed, cancelled, aborted, or expired).
     *
     * <p>Example usage:
     * <pre>{@code
     * axonflow.streamExecutionStatus("exec_123", status -> {
     *     System.out.printf("Progress: %.0f%% - Status: %s%n",
     *         status.getProgressPercent(), status.getStatus().getValue());
     *     if (status.getCurrentStep() != null) {
     *         System.out.println("  Current step: " + status.getCurrentStep().getStepName());
     *     }
     * });
     * }</pre>
     *
     * @param executionId the execution ID (plan ID or workflow ID)
     * @param callback consumer invoked with each ExecutionStatus update
     * @throws AxonFlowException if the connection fails or an I/O error occurs
     * @throws AuthenticationException if authentication fails (401/403)
     */
    public void streamExecutionStatus(
            String executionId,
            Consumer<com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus> callback) {
        Objects.requireNonNull(executionId, "executionId cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        logger.debug("Streaming execution status for {}", executionId);

        HttpUrl url = HttpUrl.parse(config.getEndpoint() + "/api/v1/unified/executions/" + executionId + "/stream");
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getEndpoint()
                + "/api/v1/unified/executions/" + executionId + "/stream");
        }

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("Accept", "text/event-stream")
            .get();

        addAuthHeaders(builder);

        Request httpRequest = builder.build();

        try {
            Response response = httpClient.newCall(httpRequest).execute();
            try {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new AxonFlowException("SSE response has no body", 0, null);
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    StringBuilder eventBuffer = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            // Empty line = end of SSE event
                            String event = eventBuffer.toString().trim();
                            eventBuffer.setLength(0);

                            if (event.isEmpty()) {
                                continue;
                            }

                            // Parse SSE data lines
                            for (String eventLine : event.split("\n")) {
                                if (eventLine.startsWith("data: ")) {
                                    String jsonStr = eventLine.substring(6);
                                    if (jsonStr.isEmpty() || "[DONE]".equals(jsonStr)) {
                                        continue;
                                    }
                                    try {
                                        com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus status =
                                            objectMapper.readValue(jsonStr,
                                                com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus.class);
                                        callback.accept(status);

                                        // Check for terminal status
                                        if (status.getStatus() != null && status.getStatus().isTerminal()) {
                                            return;
                                        }
                                    } catch (JsonProcessingException e) {
                                        logger.warn("Failed to parse SSE data: {}", jsonStr, e);
                                    }
                                }
                            }
                        } else {
                            eventBuffer.append(line).append("\n");
                        }
                    }
                }
            } finally {
                response.close();
            }
        } catch (IOException e) {
            throw new AxonFlowException("SSE stream failed: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Media Governance Config
    // ========================================================================

    /**
     * Gets the media governance configuration for the current tenant.
     *
     * <p>Returns per-tenant settings controlling whether media analysis is
     * enabled and which analyzers are allowed.
     *
     * @return the media governance configuration
     * @throws AxonFlowException if the request fails
     */
    public MediaGovernanceConfig getMediaGovernanceConfig() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/media-governance/config", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, MediaGovernanceConfig.class);
            }
        }, "getMediaGovernanceConfig");
    }

    /**
     * Asynchronously gets the media governance configuration for the current tenant.
     *
     * @return a future containing the media governance configuration
     */
    public CompletableFuture<MediaGovernanceConfig> getMediaGovernanceConfigAsync() {
        return CompletableFuture.supplyAsync(this::getMediaGovernanceConfig, asyncExecutor);
    }

    /**
     * Updates the media governance configuration for the current tenant.
     *
     * <p>Allows enabling/disabling media analysis and controlling which
     * analyzers are permitted.
     *
     * @param request the update request
     * @return the updated media governance configuration
     * @throws AxonFlowException if the request fails
     */
    public MediaGovernanceConfig updateMediaGovernanceConfig(UpdateMediaGovernanceConfigRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("PUT", "/api/v1/media-governance/config", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, MediaGovernanceConfig.class);
            }
        }, "updateMediaGovernanceConfig");
    }

    /**
     * Asynchronously updates the media governance configuration for the current tenant.
     *
     * @param request the update request
     * @return a future containing the updated media governance configuration
     */
    public CompletableFuture<MediaGovernanceConfig> updateMediaGovernanceConfigAsync(UpdateMediaGovernanceConfigRequest request) {
        return CompletableFuture.supplyAsync(() -> updateMediaGovernanceConfig(request), asyncExecutor);
    }

    /**
     * Gets the platform-level media governance status.
     *
     * <p>Returns whether media governance is available, default enablement,
     * and the required license tier.
     *
     * @return the media governance status
     * @throws AxonFlowException if the request fails
     */
    public MediaGovernanceStatus getMediaGovernanceStatus() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("GET", "/api/v1/media-governance/status", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, MediaGovernanceStatus.class);
            }
        }, "getMediaGovernanceStatus");
    }

    /**
     * Asynchronously gets the platform-level media governance status.
     *
     * @return a future containing the media governance status
     */
    public CompletableFuture<MediaGovernanceStatus> getMediaGovernanceStatusAsync() {
        return CompletableFuture.supplyAsync(this::getMediaGovernanceStatus, asyncExecutor);
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
        HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
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

    private Request buildPatchRequest(String path, Object body) {
        HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
        }

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("Accept", "application/json");

        addAuthHeaders(builder);

        if (config.getMode() != null) {
            builder.header("X-AxonFlow-Mode", config.getMode().getValue());
        }

        RequestBody requestBody = null;
        if (body != null) {
            try {
                String json = objectMapper.writeValueAsString(body);
                requestBody = RequestBody.create(json, JSON);
            } catch (JsonProcessingException e) {
                throw new AxonFlowException("Failed to serialize request body", e);
            }
        }

        builder.patch(requestBody != null ? requestBody : RequestBody.create("", JSON));
        return builder.build();
    }

    private String buildPolicyQueryString(String basePath, ListStaticPoliciesOptions options) {
        if (options == null) {
            return basePath;
        }

        StringBuilder path = new StringBuilder(basePath);
        StringBuilder query = new StringBuilder();

        if (options.getCategory() != null) {
            appendQueryParam(query, "category", options.getCategory().getValue());
        }
        if (options.getTier() != null) {
            appendQueryParam(query, "tier", options.getTier().getValue());
        }
        if (options.getOrganizationId() != null) {
            appendQueryParam(query, "organization_id", options.getOrganizationId());
        }
        if (options.getEnabled() != null) {
            appendQueryParam(query, "enabled", options.getEnabled().toString());
        }
        if (options.getLimit() != null) {
            appendQueryParam(query, "limit", options.getLimit().toString());
        }
        if (options.getOffset() != null) {
            appendQueryParam(query, "offset", options.getOffset().toString());
        }
        if (options.getSortBy() != null) {
            appendQueryParam(query, "sort_by", options.getSortBy());
        }
        if (options.getSortOrder() != null) {
            appendQueryParam(query, "sort_order", options.getSortOrder());
        }
        if (options.getSearch() != null) {
            appendQueryParam(query, "search", options.getSearch());
        }

        if (query.length() > 0) {
            path.append("?").append(query);
        }
        return path.toString();
    }

    private String buildDynamicPolicyQueryString(String basePath, ListDynamicPoliciesOptions options) {
        if (options == null) {
            return basePath;
        }

        StringBuilder path = new StringBuilder(basePath);
        StringBuilder query = new StringBuilder();

        if (options.getType() != null) {
            appendQueryParam(query, "type", options.getType());
        }
        if (options.getTier() != null) {
            appendQueryParam(query, "tier", options.getTier().getValue());
        }
        if (options.getOrganizationId() != null) {
            appendQueryParam(query, "organization_id", options.getOrganizationId());
        }
        if (options.getEnabled() != null) {
            appendQueryParam(query, "enabled", options.getEnabled().toString());
        }
        if (options.getLimit() != null) {
            appendQueryParam(query, "limit", options.getLimit().toString());
        }
        if (options.getOffset() != null) {
            appendQueryParam(query, "offset", options.getOffset().toString());
        }
        if (options.getSortBy() != null) {
            appendQueryParam(query, "sort_by", options.getSortBy());
        }
        if (options.getSortOrder() != null) {
            appendQueryParam(query, "sort_order", options.getSortOrder());
        }
        if (options.getSearch() != null) {
            appendQueryParam(query, "search", options.getSearch());
        }

        if (query.length() > 0) {
            path.append("?").append(query);
        }
        return path.toString();
    }

    private String buildEffectivePoliciesQuery(EffectivePoliciesOptions options) {
        StringBuilder query = new StringBuilder();

        if (options.getCategory() != null) {
            appendQueryParam(query, "category", options.getCategory().getValue());
        }
        if (options.isIncludeDisabled()) {
            appendQueryParam(query, "include_disabled", "true");
        }
        if (options.isIncludeOverridden()) {
            appendQueryParam(query, "include_overridden", "true");
        }

        return query.toString();
    }

    private void appendQueryParam(StringBuilder query, String name, String value) {
        if (query.length() > 0) {
            query.append("&");
        }
        query.append(name).append("=").append(value);
    }

    private void addAuthHeaders(Request.Builder builder) {
        // Always send Basic auth with the effective clientId — server derives tenant from it.
        // clientSecret defaults to empty string for community/no-secret mode.
        String effectiveClientId = getEffectiveClientId();
        String secret = config.getClientSecret() != null ? config.getClientSecret() : "";
        String credentials = effectiveClientId + ":" + secret;
        String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8)
        );
        builder.header("Authorization", "Basic " + encoded);
    }

    /**
     * Requires credentials for enterprise features.
     * Get the effective clientId, using smart default for community mode.
     *
     * <p>Returns the configured clientId if set, otherwise returns "community"
     * as a smart default. This enables zero-config usage for community/self-hosted
     * deployments while still supporting enterprise deployments with explicit credentials.
     *
     * @return the clientId to use in requests
     */
    private String getEffectiveClientId() {
        String clientId = config.getClientId();
        return (clientId != null && !clientId.isEmpty()) ? clientId : "community";
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

    private JsonNode parseResponseNode(Response response) throws IOException {
        handleErrorResponse(response);

        ResponseBody body = response.body();
        if (body == null) {
            throw new AxonFlowException("Empty response body", response.code(), null);
        }

        String json = body.string();
        if (json.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(json);
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
            case 402:
                // Budget exceeded - treat similarly to 403 policy violation
                throw new PolicyViolationException(errorMessage);
            case 403:
                // Check if this is a policy violation
                if (body.contains("policy") || body.contains("blocked")) {
                    throw new PolicyViolationException(errorMessage);
                }
                throw new AuthenticationException(errorMessage, 403);
            case 409:
                throw new AxonFlowException(errorMessage, 409, "VERSION_CONFLICT");
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

    // ========================================================================
    // Portal Authentication (Enterprise)
    // ========================================================================

    /**
     * Login to Customer Portal and store session cookie.
     * Required before using Code Governance methods.
     *
     * @param orgId the organization ID
     * @param password the organization password
     * @return login response with session info
     * @throws IOException if the request fails
     *
     * @example
     * <pre>{@code
     * PortalLoginResponse login = axonflow.loginToPortal("test-org-001", "test123");
     * System.out.println("Logged in as: " + login.getName());
     *
     * // Now you can use Code Governance methods
     * ListGitProvidersResponse providers = axonflow.listGitProviders();
     * }</pre>
     */
    public PortalLoginResponse loginToPortal(String orgId, String password) throws IOException {
        logger.debug("Logging in to portal: {}", orgId);

        String json = objectMapper.writeValueAsString(
            java.util.Map.of("org_id", orgId, "password", password)
        );
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/auth/login")
                .post(body)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new AuthenticationException("Login failed: " + response.body().string());
            }

            PortalLoginResponse loginResponse = parseResponse(response, PortalLoginResponse.class);

            // Extract session cookie from response
            String cookies = response.header("Set-Cookie");
            if (cookies != null && cookies.contains("axonflow_session=")) {
                int start = cookies.indexOf("axonflow_session=") + 17;
                int end = cookies.indexOf(";", start);
                if (end > start) {
                    this.sessionCookie = cookies.substring(start, end);
                }
            }

            // Fallback to session_id in response body
            if (this.sessionCookie == null && loginResponse.getSessionId() != null) {
                this.sessionCookie = loginResponse.getSessionId();
            }

            logger.info("Portal login successful for {}", orgId);
            return loginResponse;
        }
    }

    /**
     * Logout from Customer Portal and clear session cookie.
     */
    public void logoutFromPortal() {
        if (sessionCookie == null) {
            return;
        }

        try {
            Request request = new Request.Builder()
                    .url(config.getEndpoint() + "/api/v1/auth/logout")
                    .post(RequestBody.create("", JSON))
                    .header("Cookie", "axonflow_session=" + sessionCookie)
                    .build();

            httpClient.newCall(request).execute().close();
        } catch (Exception e) {
            // Ignore logout errors
        }

        sessionCookie = null;
        logger.info("Portal logout successful");
    }

    /**
     * Check if logged in to Customer Portal.
     *
     * @return true if logged in
     */
    public boolean isLoggedIn() {
        return sessionCookie != null;
    }

    // ========================================================================
    // Code Governance - Git Provider APIs (Enterprise)
    // ========================================================================

    /**
     * Validates Git provider credentials without saving them.
     * Requires prior authentication via loginToPortal().
     *
     * @param request the validation request with provider type and credentials
     * @return validation result
     * @throws IOException if the request fails
     */
    public ValidateGitProviderResponse validateGitProvider(ValidateGitProviderRequest request) throws IOException {
        requirePortalLogin();
        logger.debug("Validating Git provider: {}", request.getType());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/git-providers/validate")
                .post(body);

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, ValidateGitProviderResponse.class);
        }
    }

    /**
     * Configures a Git provider for code governance.
     *
     * @param request the configuration request with provider type and credentials
     * @return configuration result
     * @throws IOException if the request fails
     */
    public ConfigureGitProviderResponse configureGitProvider(ConfigureGitProviderRequest request) throws IOException {
        requirePortalLogin();
        logger.debug("Configuring Git provider: {}", request.getType());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/git-providers")
                .post(body);

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, ConfigureGitProviderResponse.class);
        }
    }

    /**
     * Lists configured Git providers.
     *
     * @return list of configured providers
     * @throws IOException if the request fails
     */
    public ListGitProvidersResponse listGitProviders() throws IOException {
        requirePortalLogin();
        logger.debug("Listing Git providers");

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/git-providers")
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, ListGitProvidersResponse.class);
        }
    }

    /**
     * Deletes a configured Git provider.
     *
     * @param providerType the provider type to delete
     * @throws IOException if the request fails
     */
    public void deleteGitProvider(GitProviderType providerType) throws IOException {
        requirePortalLogin();
        logger.debug("Deleting Git provider: {}", providerType);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/git-providers/" + providerType.getValue())
                .delete();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            handleErrorResponse(response);
        }
    }

    /**
     * Creates a Pull Request from LLM-generated code.
     *
     * @param request the PR creation request with repository info and files
     * @return the created PR details
     * @throws IOException if the request fails
     */
    public CreatePRResponse createPR(CreatePRRequest request) throws IOException {
        requirePortalLogin();
        logger.debug("Creating PR: {} in {}/{}", request.getTitle(), request.getOwner(), request.getRepo());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/prs")
                .post(body);

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, CreatePRResponse.class);
        }
    }

    /**
     * Lists PRs with optional filtering.
     *
     * @param options filtering options (limit, offset, state)
     * @return list of PRs
     * @throws IOException if the request fails
     */
    public ListPRsResponse listPRs(ListPRsOptions options) throws IOException {
        requirePortalLogin();
        logger.debug("Listing PRs");

        StringBuilder url = new StringBuilder(config.getEndpoint() + "/api/v1/code-governance/prs");
        StringBuilder query = new StringBuilder();

        if (options != null) {
            if (options.getLimit() != null) {
                appendQueryParam(query, "limit", String.valueOf(options.getLimit()));
            }
            if (options.getOffset() != null) {
                appendQueryParam(query, "offset", String.valueOf(options.getOffset()));
            }
            if (options.getState() != null) {
                appendQueryParam(query, "state", options.getState());
            }
        }

        if (query.length() > 0) {
            url.append("?").append(query);
        }

        Request.Builder builder = new Request.Builder()
                .url(url.toString())
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, ListPRsResponse.class);
        }
    }

    /**
     * Lists PRs with default options.
     *
     * @return list of PRs
     * @throws IOException if the request fails
     */
    public ListPRsResponse listPRs() throws IOException {
        return listPRs(null);
    }

    /**
     * Gets a specific PR by ID.
     *
     * @param prId the PR record ID
     * @return the PR record
     * @throws IOException if the request fails
     */
    public PRRecord getPR(String prId) throws IOException {
        requirePortalLogin();
        logger.debug("Getting PR: {}", prId);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/prs/" + prId)
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, PRRecord.class);
        }
    }

    /**
     * Syncs PR status from the Git provider.
     *
     * @param prId the PR record ID to sync
     * @return the updated PR record
     * @throws IOException if the request fails
     */
    public PRRecord syncPRStatus(String prId) throws IOException {
        requirePortalLogin();
        logger.debug("Syncing PR status: {}", prId);

        RequestBody body = RequestBody.create("{}", JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/prs/" + prId + "/sync")
                .post(body);

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, PRRecord.class);
        }
    }

    /**
     * Closes a PR without merging and optionally deletes the branch.
     * This is an enterprise feature for cleaning up test/demo PRs.
     * Supports all Git providers: GitHub, GitLab, Bitbucket.
     *
     * @param prId the PR record ID to close
     * @param deleteBranch whether to also delete the source branch
     * @return the closed PR record
     * @throws IOException if the request fails
     */
    public PRRecord closePR(String prId, boolean deleteBranch) throws IOException {
        requirePortalLogin();
        logger.debug("Closing PR: {} (deleteBranch={})", prId, deleteBranch);

        String url = config.getEndpoint() + "/api/v1/code-governance/prs/" + prId;
        if (deleteBranch) {
            url += "?delete_branch=true";
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .delete();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, PRRecord.class);
        }
    }

    /**
     * Gets aggregated code governance metrics for the tenant.
     *
     * @return aggregated metrics including PR counts, file counts, and security findings
     * @throws IOException if the request fails
     */
    public CodeGovernanceMetrics getCodeGovernanceMetrics() throws IOException {
        requirePortalLogin();
        logger.debug("Getting code governance metrics");

        Request.Builder builder = new Request.Builder()
                .url(config.getEndpoint() + "/api/v1/code-governance/metrics")
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, CodeGovernanceMetrics.class);
        }
    }

    /**
     * Exports code governance data in JSON format.
     *
     * @param options export options (format, date range, state filter)
     * @return export response with PR records
     * @throws IOException if the request fails
     */
    public ExportResponse exportCodeGovernanceData(ExportOptions options) throws IOException {
        requirePortalLogin();
        logger.debug("Exporting code governance data");

        StringBuilder url = new StringBuilder(config.getEndpoint() + "/api/v1/code-governance/export");
        StringBuilder query = new StringBuilder();

        if (options != null) {
            appendQueryParam(query, "format", options.getFormat() != null ? options.getFormat() : "json");
            if (options.getStartDate() != null) {
                appendQueryParam(query, "start_date", options.getStartDate().toString());
            }
            if (options.getEndDate() != null) {
                appendQueryParam(query, "end_date", options.getEndDate().toString());
            }
            if (options.getState() != null) {
                appendQueryParam(query, "state", options.getState());
            }
        } else {
            appendQueryParam(query, "format", "json");
        }

        if (query.length() > 0) {
            url.append("?").append(query);
        }

        Request.Builder builder = new Request.Builder()
                .url(url.toString())
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response, ExportResponse.class);
        }
    }

    /**
     * Exports code governance data in CSV format.
     *
     * @param options export options (date range, state filter)
     * @return CSV data as a string
     * @throws IOException if the request fails
     */
    public String exportCodeGovernanceDataCSV(ExportOptions options) throws IOException {
        requirePortalLogin();
        logger.debug("Exporting code governance data as CSV");

        StringBuilder url = new StringBuilder(config.getEndpoint() + "/api/v1/code-governance/export");
        StringBuilder query = new StringBuilder();

        appendQueryParam(query, "format", "csv");
        if (options != null) {
            if (options.getStartDate() != null) {
                appendQueryParam(query, "start_date", options.getStartDate().toString());
            }
            if (options.getEndDate() != null) {
                appendQueryParam(query, "end_date", options.getEndDate().toString());
            }
            if (options.getState() != null) {
                appendQueryParam(query, "state", options.getState());
            }
        }

        url.append("?").append(query);

        Request.Builder builder = new Request.Builder()
                .url(url.toString())
                .get();

        addPortalSessionCookie(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            handleErrorResponse(response);
            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }
            return body.string();
        }
    }

    // ========================================================================
    // Execution Replay API
    // ========================================================================

    /**
     * Builds a request for the orchestrator API.
     */
    private Request buildOrchestratorRequest(String method, String path, Object body) {
        HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
        }

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("Accept", "application/json");

        addAuthHeaders(builder);

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
            case "PATCH":
                builder.patch(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            case "DELETE":
                builder.delete(requestBody);
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return builder.build();
    }

    /**
     * Requires portal login before making code governance requests.
     */
    private void requirePortalLogin() {
        if (sessionCookie == null) {
            throw new AuthenticationException("Not logged in to Customer Portal. Call loginToPortal() first.");
        }
    }

    /**
     * Adds the session cookie header for portal authentication.
     */
    private void addPortalSessionCookie(Request.Builder builder) {
        if (sessionCookie != null) {
            builder.header("Cookie", "axonflow_session=" + sessionCookie);
        }
    }

    /**
     * Builds a request for the Customer Portal API (enterprise features).
     * Requires prior authentication via loginToPortal().
     */
    private Request buildPortalRequest(String method, String path, Object body) {
        requirePortalLogin();

        HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
        }

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("Accept", "application/json");

        addPortalSessionCookie(builder);

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
            case "PATCH":
                builder.patch(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            case "DELETE":
                builder.delete(requestBody);
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return builder.build();
    }

    /**
     * Lists workflow executions with optional filtering and pagination.
     *
     * @param options filtering and pagination options
     * @return paginated list of execution summaries
     *
     * @example
     * <pre>{@code
     * ListExecutionsResponse response = axonflow.listExecutions(
     *     ListExecutionsOptions.builder()
     *         .setStatus("completed")
     *         .setLimit(10)
     * );
     * for (ExecutionSummary exec : response.getExecutions()) {
     *     System.out.println(exec.getRequestId() + ": " + exec.getStatus());
     * }
     * }</pre>
     */
    public ListExecutionsResponse listExecutions(ListExecutionsOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/executions");
            StringBuilder query = new StringBuilder();

            if (options != null) {
                if (options.getLimit() != null) {
                    appendQueryParam(query, "limit", options.getLimit().toString());
                }
                if (options.getOffset() != null) {
                    appendQueryParam(query, "offset", options.getOffset().toString());
                }
                if (options.getStatus() != null) {
                    appendQueryParam(query, "status", options.getStatus());
                }
                if (options.getWorkflowId() != null) {
                    appendQueryParam(query, "workflow_id", options.getWorkflowId());
                }
                if (options.getStartTime() != null) {
                    appendQueryParam(query, "start_time", options.getStartTime());
                }
                if (options.getEndTime() != null) {
                    appendQueryParam(query, "end_time", options.getEndTime());
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ListExecutionsResponse.class);
            }
        }, "listExecutions");
    }

    /**
     * Lists workflow executions with default options.
     *
     * @return list of execution summaries
     */
    public ListExecutionsResponse listExecutions() {
        return listExecutions(null);
    }

    /**
     * Gets a complete execution record including summary and all steps.
     *
     * @param executionId the execution ID (request_id)
     * @return full execution details with all step snapshots
     *
     * @example
     * <pre>{@code
     * ExecutionDetail detail = axonflow.getExecution("exec-abc123");
     * System.out.println("Status: " + detail.getSummary().getStatus());
     * for (ExecutionSnapshot step : detail.getSteps()) {
     *     System.out.println("Step " + step.getStepIndex() + ": " + step.getStepName());
     * }
     * }</pre>
     */
    public ExecutionDetail getExecution(String executionId) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET",
                "/api/v1/executions/" + executionId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ExecutionDetail.class);
            }
        }, "getExecution");
    }

    /**
     * Gets all step snapshots for an execution.
     *
     * @param executionId the execution ID (request_id)
     * @return list of step snapshots
     */
    public List<ExecutionSnapshot> getExecutionSteps(String executionId) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET",
                "/api/v1/executions/" + executionId + "/steps", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<List<ExecutionSnapshot>>() {});
            }
        }, "getExecutionSteps");
    }

    /**
     * Gets a timeline view of execution events for visualization.
     *
     * @param executionId the execution ID (request_id)
     * @return list of timeline entries
     */
    public List<TimelineEntry> getExecutionTimeline(String executionId) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET",
                "/api/v1/executions/" + executionId + "/timeline", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<List<TimelineEntry>>() {});
            }
        }, "getExecutionTimeline");
    }

    /**
     * Exports a complete execution record for compliance or archival.
     *
     * @param executionId the execution ID (request_id)
     * @param options export options
     * @return execution data as a map
     *
     * @example
     * <pre>{@code
     * Map<String, Object> export = axonflow.exportExecution("exec-abc123",
     *     ExecutionExportOptions.builder()
     *         .setIncludeInput(true)
     *         .setIncludeOutput(true));
     * // Save to file for audit
     * }</pre>
     */
    public Map<String, Object> exportExecution(String executionId, ExecutionExportOptions options) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/executions/" + executionId + "/export");
            StringBuilder query = new StringBuilder();

            if (options != null) {
                if (options.getFormat() != null) {
                    appendQueryParam(query, "format", options.getFormat());
                }
                if (options.isIncludeInput()) {
                    appendQueryParam(query, "include_input", "true");
                }
                if (options.isIncludeOutput()) {
                    appendQueryParam(query, "include_output", "true");
                }
                if (options.isIncludePolicies()) {
                    appendQueryParam(query, "include_policies", "true");
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<Map<String, Object>>() {});
            }
        }, "exportExecution");
    }

    /**
     * Exports a complete execution record with default options.
     *
     * @param executionId the execution ID (request_id)
     * @return execution data as a map
     */
    public Map<String, Object> exportExecution(String executionId) {
        return exportExecution(executionId, null);
    }

    /**
     * Deletes an execution and all associated step snapshots.
     *
     * @param executionId the execution ID (request_id)
     */
    public void deleteExecution(String executionId) {
        Objects.requireNonNull(executionId, "executionId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("DELETE",
                "/api/v1/executions/" + executionId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deleteExecution");
    }

    /**
     * Asynchronously lists workflow executions.
     *
     * @param options filtering and pagination options
     * @return a future containing the list of executions
     */
    public CompletableFuture<ListExecutionsResponse> listExecutionsAsync(ListExecutionsOptions options) {
        return CompletableFuture.supplyAsync(() -> listExecutions(options), asyncExecutor);
    }

    /**
     * Asynchronously gets execution details.
     *
     * @param executionId the execution ID
     * @return a future containing the execution details
     */
    public CompletableFuture<ExecutionDetail> getExecutionAsync(String executionId) {
        return CompletableFuture.supplyAsync(() -> getExecution(executionId), asyncExecutor);
    }

    // ========================================
    // COST CONTROLS - BUDGETS
    // ========================================

    /**
     * Creates a new budget.
     *
     * @param request the budget creation request
     * @return the created budget
     */
    public Budget createBudget(CreateBudgetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/budgets", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, Budget.class);
            }
        }, "createBudget");
    }

    /**
     * Gets a budget by ID.
     *
     * @param budgetId the budget ID
     * @return the budget
     */
    public Budget getBudget(String budgetId) {
        Objects.requireNonNull(budgetId, "budgetId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, Budget.class);
            }
        }, "getBudget");
    }

    /**
     * Lists all budgets.
     *
     * @param options filtering and pagination options
     * @return list of budgets
     */
    public BudgetsResponse listBudgets(ListBudgetsOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/budgets");
            StringBuilder query = new StringBuilder();

            if (options != null) {
                if (options.getScope() != null) {
                    appendQueryParam(query, "scope", options.getScope().getValue());
                }
                if (options.getLimit() != null) {
                    appendQueryParam(query, "limit", options.getLimit().toString());
                }
                if (options.getOffset() != null) {
                    appendQueryParam(query, "offset", options.getOffset().toString());
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, BudgetsResponse.class);
            }
        }, "listBudgets");
    }

    /**
     * Lists all budgets with default options.
     *
     * @return list of budgets
     */
    public BudgetsResponse listBudgets() {
        return listBudgets(null);
    }

    /**
     * Updates an existing budget.
     *
     * @param budgetId the budget ID
     * @param request the update request
     * @return the updated budget
     */
    public Budget updateBudget(String budgetId, UpdateBudgetRequest request) {
        Objects.requireNonNull(budgetId, "budgetId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("PUT", "/api/v1/budgets/" + budgetId, request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, Budget.class);
            }
        }, "updateBudget");
    }

    /**
     * Deletes a budget.
     *
     * @param budgetId the budget ID
     */
    public void deleteBudget(String budgetId) {
        Objects.requireNonNull(budgetId, "budgetId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("DELETE", "/api/v1/budgets/" + budgetId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() && response.code() != 204) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deleteBudget");
    }

    // ========================================
    // COST CONTROLS - BUDGET STATUS & ALERTS
    // ========================================

    /**
     * Gets the current status of a budget.
     *
     * @param budgetId the budget ID
     * @return the budget status
     */
    public BudgetStatus getBudgetStatus(String budgetId) {
        Objects.requireNonNull(budgetId, "budgetId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId + "/status", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, BudgetStatus.class);
            }
        }, "getBudgetStatus");
    }

    /**
     * Gets alerts for a budget.
     *
     * @param budgetId the budget ID
     * @return the budget alerts
     */
    public BudgetAlertsResponse getBudgetAlerts(String budgetId) {
        Objects.requireNonNull(budgetId, "budgetId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId + "/alerts", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, BudgetAlertsResponse.class);
            }
        }, "getBudgetAlerts");
    }

    /**
     * Performs a pre-flight budget check.
     *
     * @param request the check request
     * @return the budget decision
     */
    public BudgetDecision checkBudget(BudgetCheckRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/budgets/check", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, BudgetDecision.class);
            }
        }, "checkBudget");
    }

    // ========================================
    // COST CONTROLS - USAGE
    // ========================================

    /**
     * Gets usage summary for a period.
     *
     * @param period the period (daily, weekly, monthly, quarterly, yearly)
     * @return the usage summary
     */
    public UsageSummary getUsageSummary(String period) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/usage");
            if (period != null && !period.isEmpty()) {
                path.append("?period=").append(period);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, UsageSummary.class);
            }
        }, "getUsageSummary");
    }

    /**
     * Gets usage summary with default period.
     *
     * @return the usage summary
     */
    public UsageSummary getUsageSummary() {
        return getUsageSummary(null);
    }

    /**
     * Gets usage breakdown by a grouping dimension.
     *
     * @param groupBy the dimension to group by (provider, model, agent, team, workflow)
     * @param period the period (daily, weekly, monthly, quarterly, yearly)
     * @return the usage breakdown
     */
    public UsageBreakdown getUsageBreakdown(String groupBy, String period) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/usage/breakdown");
            StringBuilder query = new StringBuilder();

            if (groupBy != null && !groupBy.isEmpty()) {
                appendQueryParam(query, "group_by", groupBy);
            }
            if (period != null && !period.isEmpty()) {
                appendQueryParam(query, "period", period);
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, UsageBreakdown.class);
            }
        }, "getUsageBreakdown");
    }

    /**
     * Lists usage records.
     *
     * @param options filtering and pagination options
     * @return list of usage records
     */
    public UsageRecordsResponse listUsageRecords(ListUsageRecordsOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/usage/records");
            StringBuilder query = new StringBuilder();

            if (options != null) {
                if (options.getLimit() != null) {
                    appendQueryParam(query, "limit", options.getLimit().toString());
                }
                if (options.getOffset() != null) {
                    appendQueryParam(query, "offset", options.getOffset().toString());
                }
                if (options.getProvider() != null) {
                    appendQueryParam(query, "provider", options.getProvider());
                }
                if (options.getModel() != null) {
                    appendQueryParam(query, "model", options.getModel());
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, UsageRecordsResponse.class);
            }
        }, "listUsageRecords");
    }

    /**
     * Lists usage records with default options.
     *
     * @return list of usage records
     */
    public UsageRecordsResponse listUsageRecords() {
        return listUsageRecords(null);
    }

    // ========================================
    // COST CONTROLS - PRICING
    // ========================================

    /**
     * Gets pricing information for models.
     *
     * @param provider filter by provider (optional)
     * @param model filter by model (optional)
     * @return pricing information
     */
    public PricingListResponse getPricing(String provider, String model) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/pricing");
            StringBuilder query = new StringBuilder();

            if (provider != null && !provider.isEmpty()) {
                appendQueryParam(query, "provider", provider);
            }
            if (model != null && !model.isEmpty()) {
                appendQueryParam(query, "model", model);
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new AxonFlowException("Failed to get pricing: " + body);
                }

                // Handle single object or array response
                if (body.trim().startsWith("{") && body.contains("\"provider\"")) {
                    // Single object response - wrap in list
                    PricingInfo singlePricing = objectMapper.readValue(body, PricingInfo.class);
                    PricingListResponse result = new PricingListResponse();
                    result.setPricing(Collections.singletonList(singlePricing));
                    return result;
                } else {
                    return objectMapper.readValue(body, PricingListResponse.class);
                }
            }
        }, "getPricing");
    }

    /**
     * Gets all pricing information.
     *
     * @return all pricing information
     */
    public PricingListResponse getPricing() {
        return getPricing(null, null);
    }

    // ========================================
    // COST CONTROLS - ASYNC METHODS
    // ========================================

    /**
     * Asynchronously creates a budget.
     *
     * @param request the budget creation request
     * @return a future containing the created budget
     */
    public CompletableFuture<Budget> createBudgetAsync(CreateBudgetRequest request) {
        return CompletableFuture.supplyAsync(() -> createBudget(request), asyncExecutor);
    }

    /**
     * Asynchronously gets a budget.
     *
     * @param budgetId the budget ID
     * @return a future containing the budget
     */
    public CompletableFuture<Budget> getBudgetAsync(String budgetId) {
        return CompletableFuture.supplyAsync(() -> getBudget(budgetId), asyncExecutor);
    }

    /**
     * Asynchronously lists budgets.
     *
     * @param options filtering and pagination options
     * @return a future containing the list of budgets
     */
    public CompletableFuture<BudgetsResponse> listBudgetsAsync(ListBudgetsOptions options) {
        return CompletableFuture.supplyAsync(() -> listBudgets(options), asyncExecutor);
    }

    /**
     * Asynchronously gets budget status.
     *
     * @param budgetId the budget ID
     * @return a future containing the budget status
     */
    public CompletableFuture<BudgetStatus> getBudgetStatusAsync(String budgetId) {
        return CompletableFuture.supplyAsync(() -> getBudgetStatus(budgetId), asyncExecutor);
    }

    /**
     * Asynchronously gets usage summary.
     *
     * @param period the period
     * @return a future containing the usage summary
     */
    public CompletableFuture<UsageSummary> getUsageSummaryAsync(String period) {
        return CompletableFuture.supplyAsync(() -> getUsageSummary(period), asyncExecutor);
    }

    // ========================================================================
    // Workflow Control Plane
    // ========================================================================
    // The Workflow Control Plane provides governance gates for external
    // orchestrators like LangChain, LangGraph, and CrewAI.
    //
    // "LangChain runs the workflow. AxonFlow decides when it's allowed to move forward."

    /**
     * Creates a new workflow for governance tracking.
     *
     * <p>Registers a new workflow with AxonFlow. Call this at the start of your
     * external orchestrator workflow (LangChain, LangGraph, CrewAI, etc.).
     *
     * @param request workflow creation request
     * @return created workflow with ID
     * @throws AxonFlowException if creation fails
     *
     * @example
     * <pre>{@code
     * CreateWorkflowResponse workflow = axonflow.createWorkflow(
     *     CreateWorkflowRequest.builder()
     *         .workflowName("code-review-pipeline")
     *         .source(WorkflowSource.LANGGRAPH)
     *         .build()
     * );
     * System.out.println("Workflow created: " + workflow.getWorkflowId());
     * }</pre>
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse createWorkflow(
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/workflows", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse>() {});
            }
        }, "createWorkflow");
    }

    /**
     * Gets the status of a workflow.
     *
     * @param workflowId workflow ID
     * @return workflow status including steps
     * @throws AxonFlowException if workflow not found
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse getWorkflow(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/workflows/" + workflowId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse>() {});
            }
        }, "getWorkflow");
    }

    /**
     * Checks if a workflow step is allowed to proceed (step gate).
     *
     * <p>This is the core governance method. Call this before executing each step
     * in your workflow to check if the step is allowed based on policies.
     *
     * @param workflowId workflow ID
     * @param stepId unique step identifier (you provide this)
     * @param request step gate request with step details
     * @return gate decision: allow, block, or require_approval
     * @throws AxonFlowException if check fails
     *
     * @example
     * <pre>{@code
     * StepGateResponse gate = axonflow.stepGate(
     *     workflow.getWorkflowId(),
     *     "step-1",
     *     StepGateRequest.builder()
     *         .stepName("Generate Code")
     *         .stepType(StepType.LLM_CALL)
     *         .model("gpt-4")
     *         .provider("openai")
     *         .build()
     * );
     *
     * if (gate.isBlocked()) {
     *     throw new RuntimeException("Step blocked: " + gate.getReason());
     * } else if (gate.requiresApproval()) {
     *     System.out.println("Approval needed: " + gate.getApprovalUrl());
     * } else {
     *     // Execute the step
     *     executeStep();
     * }
     * }</pre>
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse stepGate(
            String workflowId,
            String stepId,
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest request) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/gate", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse>() {});
            }
        }, "stepGate");
    }

    /**
     * Marks a step as completed.
     *
     * <p>Call this after successfully executing a step to record its completion.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @param request optional completion request with output data
     */
    public void markStepCompleted(
            String workflowId,
            String stepId,
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.MarkStepCompletedRequest request) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/complete",
                request != null ? request : Collections.emptyMap());
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "markStepCompleted");
    }

    /**
     * Marks a step as completed with no output data.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     */
    public void markStepCompleted(String workflowId, String stepId) {
        markStepCompleted(workflowId, stepId, null);
    }

    /**
     * Completes a workflow successfully.
     *
     * <p>Call this when your workflow has completed all steps successfully.
     *
     * @param workflowId workflow ID
     */
    public void completeWorkflow(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/complete", Collections.emptyMap());
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "completeWorkflow");
    }

    /**
     * Aborts a workflow.
     *
     * <p>Call this when you need to stop a workflow due to an error or user request.
     *
     * @param workflowId workflow ID
     * @param reason optional reason for aborting
     */
    public void abortWorkflow(String workflowId, String reason) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        retryExecutor.execute(() -> {
            Map<String, String> body = reason != null ?
                Collections.singletonMap("reason", reason) : Collections.emptyMap();
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/abort", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "abortWorkflow");
    }

    /**
     * Aborts a workflow with no reason.
     *
     * @param workflowId workflow ID
     */
    public void abortWorkflow(String workflowId) {
        abortWorkflow(workflowId, null);
    }

    /**
     * Fails a workflow.
     *
     * <p>Call this when a workflow has encountered an unrecoverable error and should
     * be marked as failed.
     *
     * @param workflowId workflow ID
     * @param reason optional reason for failing
     */
    public void failWorkflow(String workflowId, String reason) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        retryExecutor.execute(() -> {
            Map<String, String> body = reason != null ?
                Collections.singletonMap("reason", reason) : Collections.emptyMap();
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/fail", body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "failWorkflow");
    }

    /**
     * Fails a workflow with no reason.
     *
     * @param workflowId workflow ID
     */
    public void failWorkflow(String workflowId) {
        failWorkflow(workflowId, null);
    }

    /**
     * Asynchronously fails a workflow.
     *
     * @param workflowId workflow ID
     * @param reason optional reason for failing
     * @return a future that completes when the workflow has been failed
     */
    public CompletableFuture<Void> failWorkflowAsync(String workflowId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            failWorkflow(workflowId, reason);
            return null;
        }, asyncExecutor);
    }

    /**
     * Resumes a workflow after approval.
     *
     * <p>Call this after a step has been approved to continue the workflow.
     *
     * @param workflowId workflow ID
     */
    public void resumeWorkflow(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflows/" + workflowId + "/resume", Collections.emptyMap());
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "resumeWorkflow");
    }

    /**
     * Lists workflows with optional filters.
     *
     * @param options filter and pagination options
     * @return list of workflows
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsResponse listWorkflows(
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsOptions options) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/workflows");
            StringBuilder query = new StringBuilder();

            if (options != null) {
                if (options.getStatus() != null) {
                    appendQueryParam(query, "status", options.getStatus().getValue());
                }
                if (options.getSource() != null) {
                    appendQueryParam(query, "source", options.getSource().getValue());
                }
                if (options.getLimit() > 0) {
                    appendQueryParam(query, "limit", String.valueOf(options.getLimit()));
                }
                if (options.getOffset() > 0) {
                    appendQueryParam(query, "offset", String.valueOf(options.getOffset()));
                }
                if (options.getTraceId() != null) {
                    appendQueryParam(query, "trace_id", options.getTraceId());
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsResponse>() {});
            }
        }, "listWorkflows");
    }

    /**
     * Lists all workflows with default options.
     *
     * @return list of workflows
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsResponse listWorkflows() {
        return listWorkflows(null);
    }

    /**
     * Asynchronously creates a workflow.
     *
     * @param request workflow creation request
     * @return a future containing the created workflow
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse> createWorkflowAsync(
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowRequest request) {
        return CompletableFuture.supplyAsync(() -> createWorkflow(request), asyncExecutor);
    }

    /**
     * Asynchronously checks a step gate.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @param request step gate request
     * @return a future containing the gate decision
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse> stepGateAsync(
            String workflowId,
            String stepId,
            com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest request) {
        return CompletableFuture.supplyAsync(() -> stepGate(workflowId, stepId, request), asyncExecutor);
    }

    // ========================================================================
    // WCP Approval Methods
    // ========================================================================

    /**
     * Approves a workflow step that requires human approval.
     *
     * <p>Call this when a step gate returns {@code require_approval} to approve
     * the step and allow the workflow to proceed.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @return the approval response
     * @throws AxonFlowException if the approval fails
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse approveStep(
            String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflow-control/" + workflowId + "/steps/" + stepId + "/approve",
                Collections.emptyMap());
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse>() {});
            }
        }, "approveStep");
    }

    /**
     * Asynchronously approves a workflow step.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @return a future containing the approval response
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse> approveStepAsync(
            String workflowId, String stepId) {
        return CompletableFuture.supplyAsync(() -> approveStep(workflowId, stepId), asyncExecutor);
    }

    /**
     * Rejects a workflow step that requires human approval.
     *
     * <p>Call this when a step gate returns {@code require_approval} to reject
     * the step and prevent the workflow from proceeding.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @return the rejection response
     * @throws AxonFlowException if the rejection fails
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse rejectStep(
            String workflowId, String stepId) {
        return rejectStep(workflowId, stepId, null);
    }

    /**
     * Rejects a workflow step that requires human approval, with a reason.
     *
     * <p>Call this when a step gate returns {@code require_approval} to reject
     * the step and prevent the workflow from proceeding.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @param reason optional reason for rejection (included in request body)
     * @return the rejection response
     * @throws AxonFlowException if the rejection fails
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse rejectStep(
            String workflowId, String stepId, String reason) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");

        return retryExecutor.execute(() -> {
            Map<String, Object> body = new HashMap<>();
            if (reason != null && !reason.isEmpty()) {
                body.put("reason", reason);
            }
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/workflow-control/" + workflowId + "/steps/" + stepId + "/reject",
                body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse>() {});
            }
        }, "rejectStep");
    }

    /**
     * Asynchronously rejects a workflow step.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @return a future containing the rejection response
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse> rejectStepAsync(
            String workflowId, String stepId) {
        return rejectStepAsync(workflowId, stepId, null);
    }

    /**
     * Asynchronously rejects a workflow step with a reason.
     *
     * @param workflowId workflow ID
     * @param stepId step ID
     * @param reason optional reason for rejection
     * @return a future containing the rejection response
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse> rejectStepAsync(
            String workflowId, String stepId, String reason) {
        return CompletableFuture.supplyAsync(() -> rejectStep(workflowId, stepId, reason), asyncExecutor);
    }

    /**
     * Gets pending approvals with a limit.
     *
     * @param limit maximum number of pending approvals to return
     * @return the pending approvals response
     * @throws AxonFlowException if the request fails
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse getPendingApprovals(int limit) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/workflow-control/pending-approvals");
            if (limit > 0) {
                path.append("?limit=").append(limit);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response,
                    new TypeReference<com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse>() {});
            }
        }, "getPendingApprovals");
    }

    /**
     * Gets all pending approvals with default limit.
     *
     * @return the pending approvals response
     * @throws AxonFlowException if the request fails
     */
    public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse getPendingApprovals() {
        return getPendingApprovals(0);
    }

    /**
     * Asynchronously gets pending approvals with a limit.
     *
     * @param limit maximum number of pending approvals to return
     * @return a future containing the pending approvals response
     */
    public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse> getPendingApprovalsAsync(
            int limit) {
        return CompletableFuture.supplyAsync(() -> getPendingApprovals(limit), asyncExecutor);
    }

    // ========================================================================
    // Webhook Subscriptions
    // ========================================================================

    /**
     * Creates a new webhook subscription.
     *
     * @param request the webhook creation request
     * @return the created webhook subscription
     * @throws AxonFlowException if creation fails
     */
    public WebhookSubscription createWebhook(CreateWebhookRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/webhooks", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, WebhookSubscription.class);
            }
        }, "createWebhook");
    }

    /**
     * Asynchronously creates a new webhook subscription.
     *
     * @param request the webhook creation request
     * @return a future containing the created webhook subscription
     */
    public CompletableFuture<WebhookSubscription> createWebhookAsync(CreateWebhookRequest request) {
        return CompletableFuture.supplyAsync(() -> createWebhook(request), asyncExecutor);
    }

    /**
     * Gets a webhook subscription by ID.
     *
     * @param webhookId the webhook ID
     * @return the webhook subscription
     * @throws AxonFlowException if the webhook is not found
     */
    public WebhookSubscription getWebhook(String webhookId) {
        Objects.requireNonNull(webhookId, "webhookId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET",
                "/api/v1/webhooks/" + webhookId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, WebhookSubscription.class);
            }
        }, "getWebhook");
    }

    /**
     * Asynchronously gets a webhook subscription by ID.
     *
     * @param webhookId the webhook ID
     * @return a future containing the webhook subscription
     */
    public CompletableFuture<WebhookSubscription> getWebhookAsync(String webhookId) {
        return CompletableFuture.supplyAsync(() -> getWebhook(webhookId), asyncExecutor);
    }

    /**
     * Updates an existing webhook subscription.
     *
     * @param webhookId the webhook ID
     * @param request   the update request
     * @return the updated webhook subscription
     * @throws AxonFlowException if the update fails
     */
    public WebhookSubscription updateWebhook(String webhookId, UpdateWebhookRequest request) {
        Objects.requireNonNull(webhookId, "webhookId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("PUT",
                "/api/v1/webhooks/" + webhookId, request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, WebhookSubscription.class);
            }
        }, "updateWebhook");
    }

    /**
     * Asynchronously updates an existing webhook subscription.
     *
     * @param webhookId the webhook ID
     * @param request   the update request
     * @return a future containing the updated webhook subscription
     */
    public CompletableFuture<WebhookSubscription> updateWebhookAsync(String webhookId, UpdateWebhookRequest request) {
        return CompletableFuture.supplyAsync(() -> updateWebhook(webhookId, request), asyncExecutor);
    }

    /**
     * Deletes a webhook subscription.
     *
     * @param webhookId the webhook ID
     * @throws AxonFlowException if the deletion fails
     */
    public void deleteWebhook(String webhookId) {
        Objects.requireNonNull(webhookId, "webhookId cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("DELETE",
                "/api/v1/webhooks/" + webhookId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "deleteWebhook");
    }

    /**
     * Asynchronously deletes a webhook subscription.
     *
     * @param webhookId the webhook ID
     * @return a future that completes when the webhook is deleted
     */
    public CompletableFuture<Void> deleteWebhookAsync(String webhookId) {
        return CompletableFuture.runAsync(() -> deleteWebhook(webhookId), asyncExecutor);
    }

    /**
     * Lists all webhook subscriptions.
     *
     * @return the list of webhook subscriptions
     * @throws AxonFlowException if the request fails
     */
    public ListWebhooksResponse listWebhooks() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/webhooks", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, ListWebhooksResponse.class);
            }
        }, "listWebhooks");
    }

    /**
     * Asynchronously lists all webhook subscriptions.
     *
     * @return a future containing the list of webhook subscriptions
     */
    public CompletableFuture<ListWebhooksResponse> listWebhooksAsync() {
        return CompletableFuture.supplyAsync(this::listWebhooks, asyncExecutor);
    }

    // ========================================================================
    // HITL (Human-in-the-Loop) Queue
    // ========================================================================

    /**
     * Lists pending HITL approval requests.
     *
     * <p>Returns approval requests from the HITL queue, optionally filtered
     * by status and severity.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @param opts filtering and pagination options (may be null)
     * @return the list response containing approval requests
     * @throws AxonFlowException if the request fails
     */
    public HITLQueueListResponse listHITLQueue(HITLQueueListOptions opts) {
        return retryExecutor.execute(() -> {
            StringBuilder path = new StringBuilder("/api/v1/hitl/queue");
            StringBuilder query = new StringBuilder();

            if (opts != null) {
                if (opts.getStatus() != null) {
                    appendQueryParam(query, "status", opts.getStatus());
                }
                if (opts.getSeverity() != null) {
                    appendQueryParam(query, "severity", opts.getSeverity());
                }
                if (opts.getLimit() != null) {
                    appendQueryParam(query, "limit", opts.getLimit().toString());
                }
                if (opts.getOffset() != null) {
                    appendQueryParam(query, "offset", opts.getOffset().toString());
                }
            }

            if (query.length() > 0) {
                path.append("?").append(query);
            }

            Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);

                // Server wraps response: {"success": true, "data": [...], "meta": {...}}
                HITLQueueListResponse result = new HITLQueueListResponse();
                if (node.has("data") && node.get("data").isArray()) {
                    List<HITLApprovalRequest> items = objectMapper.convertValue(
                        node.get("data"), new TypeReference<List<HITLApprovalRequest>>() {});
                    result.setItems(items);
                }
                if (node.has("meta")) {
                    JsonNode meta = node.get("meta");
                    long total = 0;
                    long offset = 0;
                    if (meta.has("total")) {
                        total = meta.get("total").asLong();
                        result.setTotal(total);
                    }
                    if (meta.has("offset")) {
                        offset = meta.get("offset").asLong();
                    }
                    // Compute hasMore from total/offset/items (consistent with Go/TS SDKs)
                    result.setHasMore((offset + result.getItems().size()) < total);
                }
                return result;
            }
        }, "listHITLQueue");
    }

    /**
     * Lists pending HITL approval requests with default options.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @return the list response containing approval requests
     * @throws AxonFlowException if the request fails
     */
    public HITLQueueListResponse listHITLQueue() {
        return listHITLQueue(null);
    }

    /**
     * Asynchronously lists pending HITL approval requests.
     *
     * @param opts filtering and pagination options (may be null)
     * @return a future containing the list response
     */
    public CompletableFuture<HITLQueueListResponse> listHITLQueueAsync(HITLQueueListOptions opts) {
        return CompletableFuture.supplyAsync(() -> listHITLQueue(opts), asyncExecutor);
    }

    /**
     * Gets a specific HITL approval request by ID.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @param requestId the approval request ID
     * @return the approval request
     * @throws AxonFlowException if the request is not found or the call fails
     */
    public HITLApprovalRequest getHITLRequest(String requestId) {
        Objects.requireNonNull(requestId, "requestId cannot be null");

        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET",
                "/api/v1/hitl/queue/" + requestId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);

                // Server wraps response: {"success": true, "data": {...}}
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), HITLApprovalRequest.class);
                }
                return objectMapper.treeToValue(node, HITLApprovalRequest.class);
            }
        }, "getHITLRequest");
    }

    /**
     * Asynchronously gets a specific HITL approval request by ID.
     *
     * @param requestId the approval request ID
     * @return a future containing the approval request
     */
    public CompletableFuture<HITLApprovalRequest> getHITLRequestAsync(String requestId) {
        return CompletableFuture.supplyAsync(() -> getHITLRequest(requestId), asyncExecutor);
    }

    /**
     * Approves a HITL approval request.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @param requestId the approval request ID
     * @param review    the review input containing reviewer details
     * @throws AxonFlowException if the approval fails
     */
    public void approveHITLRequest(String requestId, HITLReviewInput review) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(review, "review cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/hitl/queue/" + requestId + "/approve", review);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "approveHITLRequest");
    }

    /**
     * Asynchronously approves a HITL approval request.
     *
     * @param requestId the approval request ID
     * @param review    the review input containing reviewer details
     * @return a future that completes when the request has been approved
     */
    public CompletableFuture<Void> approveHITLRequestAsync(String requestId, HITLReviewInput review) {
        return CompletableFuture.runAsync(() -> approveHITLRequest(requestId, review), asyncExecutor);
    }

    /**
     * Rejects a HITL approval request.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @param requestId the approval request ID
     * @param review    the review input containing reviewer details
     * @throws AxonFlowException if the rejection fails
     */
    public void rejectHITLRequest(String requestId, HITLReviewInput review) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(review, "review cannot be null");

        retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("POST",
                "/api/v1/hitl/queue/" + requestId + "/reject", review);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }
                return null;
            }
        }, "rejectHITLRequest");
    }

    /**
     * Asynchronously rejects a HITL approval request.
     *
     * @param requestId the approval request ID
     * @param review    the review input containing reviewer details
     * @return a future that completes when the request has been rejected
     */
    public CompletableFuture<Void> rejectHITLRequestAsync(String requestId, HITLReviewInput review) {
        return CompletableFuture.runAsync(() -> rejectHITLRequest(requestId, review), asyncExecutor);
    }

    /**
     * Gets HITL dashboard statistics.
     *
     * <p>Returns aggregate statistics about the HITL queue including
     * total pending requests, priority breakdowns, and age metrics.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     *
     * @return the dashboard statistics
     * @throws AxonFlowException if the request fails
     */
    public HITLStats getHITLStats() {
        return retryExecutor.execute(() -> {
            Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/hitl/stats", null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                JsonNode node = parseResponseNode(response);

                // Server wraps response: {"success": true, "data": {...}}
                if (node.has("data") && node.get("data").isObject()) {
                    return objectMapper.treeToValue(node.get("data"), HITLStats.class);
                }
                return objectMapper.treeToValue(node, HITLStats.class);
            }
        }, "getHITLStats");
    }

    /**
     * Asynchronously gets HITL dashboard statistics.
     *
     * @return a future containing the dashboard statistics
     */
    public CompletableFuture<HITLStats> getHITLStatsAsync() {
        return CompletableFuture.supplyAsync(this::getHITLStats, asyncExecutor);
    }

    // ========================================================================
    // MAS FEAT Namespace Inner Class
    // ========================================================================

    /**
     * MAS FEAT (Monetary Authority of Singapore - Fairness, Ethics, Accountability,
     * Transparency) compliance namespace.
     *
     * <p>Provides methods for AI system registry, FEAT assessments, and kill switch
     * management for Singapore financial services compliance.
     *
     * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
     */
    public final class MASFEATNamespace {

        private static final String BASE_PATH = "/api/v1/masfeat";

        /**
         * Registers a new AI system in the MAS FEAT registry.
         *
         * @param request the registration request
         * @return the registered system
         */
        public AISystemRegistry registerSystem(RegisterSystemRequest request) {
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                // Map SDK field names to backend field names
                Map<String, Object> body = new HashMap<>();
                body.put("system_id", request.getSystemId());
                body.put("system_name", request.getSystemName());
                if (request.getDescription() != null) {
                    body.put("description", request.getDescription());
                }
                if (request.getUseCase() != null) {
                    body.put("use_case", request.getUseCase().getValue());
                }
                body.put("owner_team", request.getOwnerTeam());
                if (request.getTechnicalOwner() != null) {
                    body.put("technical_owner", request.getTechnicalOwner());
                }
                // businessOwner maps to owner_email
                if (request.getBusinessOwner() != null) {
                    body.put("owner_email", request.getBusinessOwner());
                }
                // Risk rating fields
                body.put("risk_rating_impact", request.getCustomerImpact());
                body.put("risk_rating_complexity", request.getModelComplexity());
                body.put("risk_rating_reliance", request.getHumanReliance());
                if (request.getMetadata() != null) {
                    body.put("metadata", request.getMetadata());
                }

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/registry", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseSystemResponse(response);
                }
            }, "masfeat.registerSystem");
        }

        /**
         * Activates an AI system (changes status to 'active').
         *
         * @param systemId the system UUID (not the systemId string)
         * @return the activated system
         */
        public AISystemRegistry activateSystem(String systemId) {
            Objects.requireNonNull(systemId, "systemId cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("status", "active");

                Request httpRequest = buildOrchestratorRequest("PUT", BASE_PATH + "/registry/" + systemId, body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseSystemResponse(response);
                }
            }, "masfeat.activateSystem");
        }

        /**
         * Gets an AI system by its UUID.
         *
         * @param systemId the system UUID
         * @return the system
         */
        public AISystemRegistry getSystem(String systemId) {
            Objects.requireNonNull(systemId, "systemId cannot be null");

            return retryExecutor.execute(() -> {
                Request httpRequest = buildOrchestratorRequest("GET", BASE_PATH + "/registry/" + systemId, null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseSystemResponse(response);
                }
            }, "masfeat.getSystem");
        }

        /**
         * Gets the registry summary statistics.
         *
         * @return the registry summary
         */
        public RegistrySummary getRegistrySummary() {
            return retryExecutor.execute(() -> {
                Request httpRequest = buildOrchestratorRequest("GET", BASE_PATH + "/registry/summary", null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseSummaryResponse(response);
                }
            }, "masfeat.getRegistrySummary");
        }

        /**
         * Creates a new FEAT assessment.
         *
         * @param request the assessment creation request
         * @return the created assessment
         */
        public FEATAssessment createAssessment(CreateAssessmentRequest request) {
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("system_id", request.getSystemId());
                body.put("assessment_type", request.getAssessmentType());
                if (request.getAssessors() != null) {
                    body.put("assessors", request.getAssessors());
                }

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/assessments", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.createAssessment");
        }

        /**
         * Gets a FEAT assessment by its ID.
         *
         * @param assessmentId the assessment ID
         * @return the assessment
         */
        public FEATAssessment getAssessment(String assessmentId) {
            Objects.requireNonNull(assessmentId, "assessmentId cannot be null");

            return retryExecutor.execute(() -> {
                Request httpRequest = buildOrchestratorRequest("GET", BASE_PATH + "/assessments/" + assessmentId, null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.getAssessment");
        }

        /**
         * Updates a FEAT assessment with pillar scores and details.
         *
         * @param assessmentId the assessment ID
         * @param request the update request
         * @return the updated assessment
         */
        public FEATAssessment updateAssessment(String assessmentId, UpdateAssessmentRequest request) {
            Objects.requireNonNull(assessmentId, "assessmentId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                if (request.getFairnessScore() != null) {
                    body.put("fairness_score", request.getFairnessScore());
                }
                if (request.getEthicsScore() != null) {
                    body.put("ethics_score", request.getEthicsScore());
                }
                if (request.getAccountabilityScore() != null) {
                    body.put("accountability_score", request.getAccountabilityScore());
                }
                if (request.getTransparencyScore() != null) {
                    body.put("transparency_score", request.getTransparencyScore());
                }
                if (request.getFairnessDetails() != null) {
                    body.put("fairness_details", request.getFairnessDetails());
                }
                if (request.getEthicsDetails() != null) {
                    body.put("ethics_details", request.getEthicsDetails());
                }
                if (request.getAccountabilityDetails() != null) {
                    body.put("accountability_details", request.getAccountabilityDetails());
                }
                if (request.getTransparencyDetails() != null) {
                    body.put("transparency_details", request.getTransparencyDetails());
                }
                if (request.getFindings() != null) {
                    body.put("findings", request.getFindings());
                }
                if (request.getRecommendations() != null) {
                    body.put("recommendations", request.getRecommendations());
                }
                if (request.getAssessors() != null) {
                    body.put("assessors", request.getAssessors());
                }

                Request httpRequest = buildOrchestratorRequest("PUT", BASE_PATH + "/assessments/" + assessmentId, body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.updateAssessment");
        }

        /**
         * Submits a FEAT assessment for review.
         *
         * @param assessmentId the assessment ID
         * @return the submitted assessment
         */
        public FEATAssessment submitAssessment(String assessmentId) {
            Objects.requireNonNull(assessmentId, "assessmentId cannot be null");

            return retryExecutor.execute(() -> {
                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/assessments/" + assessmentId + "/submit", null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.submitAssessment");
        }

        /**
         * Approves a FEAT assessment.
         *
         * @param assessmentId the assessment ID
         * @param request the approval request
         * @return the approved assessment
         */
        public FEATAssessment approveAssessment(String assessmentId, ApproveAssessmentRequest request) {
            Objects.requireNonNull(assessmentId, "assessmentId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("approved_by", request.getApprovedBy());
                if (request.getComments() != null) {
                    body.put("comments", request.getComments());
                }

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/assessments/" + assessmentId + "/approve", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.approveAssessment");
        }

        /**
         * Rejects a FEAT assessment.
         *
         * @param assessmentId the assessment ID
         * @param request the rejection request
         * @return the rejected assessment
         */
        public FEATAssessment rejectAssessment(String assessmentId, RejectAssessmentRequest request) {
            Objects.requireNonNull(assessmentId, "assessmentId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("rejected_by", request.getRejectedBy());
                body.put("reason", request.getReason());

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/assessments/" + assessmentId + "/reject", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseAssessmentResponse(response);
                }
            }, "masfeat.rejectAssessment");
        }

        /**
         * Gets the kill switch configuration for an AI system.
         *
         * @param systemId the system ID (string ID, not UUID)
         * @return the kill switch configuration
         */
        public KillSwitch getKillSwitch(String systemId) {
            Objects.requireNonNull(systemId, "systemId cannot be null");

            return retryExecutor.execute(() -> {
                Request httpRequest = buildOrchestratorRequest("GET", BASE_PATH + "/killswitch/" + systemId, null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseKillSwitchResponse(response);
                }
            }, "masfeat.getKillSwitch");
        }

        /**
         * Configures the kill switch for an AI system.
         *
         * @param systemId the system ID (string ID, not UUID)
         * @param request the configuration request
         * @return the configured kill switch
         */
        public KillSwitch configureKillSwitch(String systemId, ConfigureKillSwitchRequest request) {
            Objects.requireNonNull(systemId, "systemId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                if (request.getAccuracyThreshold() != null) {
                    body.put("accuracy_threshold", request.getAccuracyThreshold());
                }
                if (request.getBiasThreshold() != null) {
                    body.put("bias_threshold", request.getBiasThreshold());
                }
                if (request.getErrorRateThreshold() != null) {
                    body.put("error_rate_threshold", request.getErrorRateThreshold());
                }
                if (request.getAutoTriggerEnabled() != null) {
                    body.put("auto_trigger_enabled", request.getAutoTriggerEnabled());
                }

                // Note: configure uses POST, not PUT
                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/killswitch/" + systemId + "/configure", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseKillSwitchResponse(response);
                }
            }, "masfeat.configureKillSwitch");
        }

        /**
         * Triggers the kill switch for an AI system.
         *
         * @param systemId the system ID (string ID, not UUID)
         * @param request the trigger request
         * @return the triggered kill switch
         */
        public KillSwitch triggerKillSwitch(String systemId, TriggerKillSwitchRequest request) {
            Objects.requireNonNull(systemId, "systemId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("reason", request.getReason());
                if (request.getTriggeredBy() != null) {
                    body.put("triggered_by", request.getTriggeredBy());
                }

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/killswitch/" + systemId + "/trigger", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseKillSwitchResponse(response);
                }
            }, "masfeat.triggerKillSwitch");
        }

        /**
         * Restores the kill switch for an AI system after remediation.
         *
         * @param systemId the system ID (string ID, not UUID)
         * @param request the restore request
         * @return the restored kill switch
         */
        public KillSwitch restoreKillSwitch(String systemId, RestoreKillSwitchRequest request) {
            Objects.requireNonNull(systemId, "systemId cannot be null");
            Objects.requireNonNull(request, "request cannot be null");

            return retryExecutor.execute(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("reason", request.getReason());
                if (request.getRestoredBy() != null) {
                    body.put("restored_by", request.getRestoredBy());
                }

                Request httpRequest = buildOrchestratorRequest("POST", BASE_PATH + "/killswitch/" + systemId + "/restore", body);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseKillSwitchResponse(response);
                }
            }, "masfeat.restoreKillSwitch");
        }

        /**
         * Gets the kill switch event history for an AI system.
         *
         * @param systemId the system ID (string ID, not UUID)
         * @param limit maximum number of events to return
         * @return list of kill switch events
         */
        public List<KillSwitchEvent> getKillSwitchHistory(String systemId, int limit) {
            Objects.requireNonNull(systemId, "systemId cannot be null");

            return retryExecutor.execute(() -> {
                String path = BASE_PATH + "/killswitch/" + systemId + "/history";
                if (limit > 0) {
                    path += "?limit=" + limit;
                }

                Request httpRequest = buildOrchestratorRequest("GET", path, null);
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    return parseKillSwitchHistoryResponse(response);
                }
            }, "masfeat.getKillSwitchHistory");
        }

        // ========================================================================
        // Response Parsing Helpers
        // ========================================================================

        private AISystemRegistry parseSystemResponse(Response response) throws IOException {
            handleErrorResponse(response);

            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }

            String json = body.string();
            JsonNode node = objectMapper.readTree(json);

            AISystemRegistry system = new AISystemRegistry();
            system.setId(getTextOrNull(node, "id"));
            system.setOrgId(getTextOrNull(node, "org_id"));
            system.setSystemId(getTextOrNull(node, "system_id"));
            system.setSystemName(getTextOrNull(node, "system_name"));
            system.setDescription(getTextOrNull(node, "description"));
            system.setOwnerTeam(getTextOrNull(node, "owner_team"));
            system.setTechnicalOwner(getTextOrNull(node, "technical_owner"));
            system.setBusinessOwner(getTextOrNull(node, "owner_email"));
            system.setCreatedBy(getTextOrNull(node, "created_by"));

            // Handle use_case enum
            String useCase = getTextOrNull(node, "use_case");
            if (useCase != null) {
                try {
                    system.setUseCase(AISystemUseCase.fromValue(useCase));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown use case: {}", useCase);
                }
            }

            // Handle risk ratings
            system.setCustomerImpact(getIntOrZero(node, "risk_rating_impact"));
            system.setModelComplexity(getIntOrZero(node, "risk_rating_complexity"));
            system.setHumanReliance(getIntOrZero(node, "risk_rating_reliance"));

            // Handle materiality (may be "materiality" or "materiality_classification")
            String materiality = getTextOrNull(node, "materiality");
            if (materiality == null) {
                materiality = getTextOrNull(node, "materiality_classification");
            }
            if (materiality != null) {
                try {
                    system.setMateriality(MaterialityClassification.fromValue(materiality));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown materiality: {}", materiality);
                }
            }

            // Handle status
            String status = getTextOrNull(node, "status");
            if (status != null) {
                try {
                    system.setStatus(SystemStatus.fromValue(status));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown status: {}", status);
                }
            }

            // Handle timestamps
            system.setCreatedAt(parseInstant(node, "created_at"));
            system.setUpdatedAt(parseInstant(node, "updated_at"));

            // Handle metadata
            if (node.has("metadata") && !node.get("metadata").isNull()) {
                system.setMetadata(objectMapper.convertValue(node.get("metadata"),
                    new TypeReference<Map<String, Object>>() {}));
            }

            return system;
        }

        private RegistrySummary parseSummaryResponse(Response response) throws IOException {
            handleErrorResponse(response);

            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }

            String json = body.string();
            JsonNode node = objectMapper.readTree(json);

            RegistrySummary summary = new RegistrySummary();
            summary.setTotalSystems(getIntOrZero(node, "total_systems"));
            summary.setActiveSystems(getIntOrZero(node, "active_systems"));

            // Handle high_materiality_count (may be "high_materiality_count" or "high_materiality")
            int highMateriality = getIntOrZero(node, "high_materiality_count");
            if (highMateriality == 0) {
                highMateriality = getIntOrZero(node, "high_materiality");
            }
            summary.setHighMaterialityCount(highMateriality);

            summary.setMediumMaterialityCount(getIntOrZero(node, "medium_materiality_count"));
            summary.setLowMaterialityCount(getIntOrZero(node, "low_materiality_count"));

            if (node.has("by_use_case") && !node.get("by_use_case").isNull()) {
                summary.setByUseCase(objectMapper.convertValue(node.get("by_use_case"),
                    new TypeReference<Map<String, Integer>>() {}));
            }

            if (node.has("by_status") && !node.get("by_status").isNull()) {
                summary.setByStatus(objectMapper.convertValue(node.get("by_status"),
                    new TypeReference<Map<String, Integer>>() {}));
            }

            return summary;
        }

        private FEATAssessment parseAssessmentResponse(Response response) throws IOException {
            handleErrorResponse(response);

            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }

            String json = body.string();
            JsonNode node = objectMapper.readTree(json);

            FEATAssessment assessment = new FEATAssessment();
            assessment.setId(getTextOrNull(node, "id"));
            assessment.setOrgId(getTextOrNull(node, "org_id"));
            assessment.setSystemId(getTextOrNull(node, "system_id"));
            assessment.setAssessmentType(getTextOrNull(node, "assessment_type"));
            assessment.setApprovedBy(getTextOrNull(node, "approved_by"));
            assessment.setCreatedBy(getTextOrNull(node, "created_by"));

            // Handle status
            String status = getTextOrNull(node, "status");
            if (status != null) {
                try {
                    assessment.setStatus(FEATAssessmentStatus.fromValue(status));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown assessment status: {}", status);
                }
            }

            // Handle scores
            assessment.setFairnessScore(getIntegerOrNull(node, "fairness_score"));
            assessment.setEthicsScore(getIntegerOrNull(node, "ethics_score"));
            assessment.setAccountabilityScore(getIntegerOrNull(node, "accountability_score"));
            assessment.setTransparencyScore(getIntegerOrNull(node, "transparency_score"));

            // Overall score may be int or float
            if (node.has("overall_score") && !node.get("overall_score").isNull()) {
                JsonNode scoreNode = node.get("overall_score");
                if (scoreNode.isNumber()) {
                    assessment.setOverallScore(scoreNode.asInt());
                }
            }

            // Handle timestamps
            assessment.setAssessmentDate(parseInstant(node, "assessment_date"));
            assessment.setValidUntil(parseInstant(node, "valid_until"));
            assessment.setApprovedAt(parseInstant(node, "approved_at"));
            assessment.setCreatedAt(parseInstant(node, "created_at"));
            assessment.setUpdatedAt(parseInstant(node, "updated_at"));

            // Handle details
            if (node.has("fairness_details") && !node.get("fairness_details").isNull()) {
                assessment.setFairnessDetails(objectMapper.convertValue(node.get("fairness_details"),
                    new TypeReference<Map<String, Object>>() {}));
            }
            if (node.has("ethics_details") && !node.get("ethics_details").isNull()) {
                assessment.setEthicsDetails(objectMapper.convertValue(node.get("ethics_details"),
                    new TypeReference<Map<String, Object>>() {}));
            }
            if (node.has("accountability_details") && !node.get("accountability_details").isNull()) {
                assessment.setAccountabilityDetails(objectMapper.convertValue(node.get("accountability_details"),
                    new TypeReference<Map<String, Object>>() {}));
            }
            if (node.has("transparency_details") && !node.get("transparency_details").isNull()) {
                assessment.setTransparencyDetails(objectMapper.convertValue(node.get("transparency_details"),
                    new TypeReference<Map<String, Object>>() {}));
            }

            // Handle assessors
            if (node.has("assessors") && node.get("assessors").isArray()) {
                assessment.setAssessors(objectMapper.convertValue(node.get("assessors"),
                    new TypeReference<List<String>>() {}));
            }

            // Handle recommendations
            if (node.has("recommendations") && node.get("recommendations").isArray()) {
                assessment.setRecommendations(objectMapper.convertValue(node.get("recommendations"),
                    new TypeReference<List<String>>() {}));
            }

            return assessment;
        }

        private KillSwitch parseKillSwitchResponse(Response response) throws IOException {
            handleErrorResponse(response);

            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }

            String json = body.string();
            JsonNode node = objectMapper.readTree(json);

            // Handle nested response: {"kill_switch": {...}, "message": "..."}
            if (node.has("kill_switch") && !node.get("kill_switch").isNull()) {
                node = node.get("kill_switch");
            }

            KillSwitch ks = new KillSwitch();
            ks.setId(getTextOrNull(node, "id"));
            ks.setOrgId(getTextOrNull(node, "org_id"));
            ks.setSystemId(getTextOrNull(node, "system_id"));
            ks.setTriggeredBy(getTextOrNull(node, "triggered_by"));
            ks.setRestoredBy(getTextOrNull(node, "restored_by"));

            // Handle triggered_reason (may be "triggered_reason" or "trigger_reason")
            String triggeredReason = getTextOrNull(node, "triggered_reason");
            if (triggeredReason == null) {
                triggeredReason = getTextOrNull(node, "trigger_reason");
            }
            ks.setTriggeredReason(triggeredReason);

            // Handle status
            String status = getTextOrNull(node, "status");
            if (status != null) {
                try {
                    ks.setStatus(KillSwitchStatus.fromValue(status));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown kill switch status: {}", status);
                }
            }

            // Handle auto_trigger
            if (node.has("auto_trigger_enabled") && !node.get("auto_trigger_enabled").isNull()) {
                ks.setAutoTriggerEnabled(node.get("auto_trigger_enabled").asBoolean());
            }

            // Handle thresholds
            ks.setAccuracyThreshold(getDoubleOrNull(node, "accuracy_threshold"));
            ks.setBiasThreshold(getDoubleOrNull(node, "bias_threshold"));
            ks.setErrorRateThreshold(getDoubleOrNull(node, "error_rate_threshold"));

            // Handle timestamps
            ks.setTriggeredAt(parseInstant(node, "triggered_at"));
            ks.setRestoredAt(parseInstant(node, "restored_at"));
            ks.setCreatedAt(parseInstant(node, "created_at"));
            ks.setUpdatedAt(parseInstant(node, "updated_at"));

            return ks;
        }

        private List<KillSwitchEvent> parseKillSwitchHistoryResponse(Response response) throws IOException {
            handleErrorResponse(response);

            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }

            String json = body.string();
            JsonNode node = objectMapper.readTree(json);

            // Handle nested response: {"history": [...]} or direct array
            JsonNode eventsNode;
            if (node.has("history") && node.get("history").isArray()) {
                eventsNode = node.get("history");
            } else if (node.has("events") && node.get("events").isArray()) {
                eventsNode = node.get("events");
            } else if (node.isArray()) {
                eventsNode = node;
            } else {
                return new ArrayList<>();
            }

            List<KillSwitchEvent> events = new ArrayList<>();
            for (JsonNode eventNode : eventsNode) {
                KillSwitchEvent event = new KillSwitchEvent();
                event.setId(getTextOrNull(eventNode, "id"));
                event.setKillSwitchId(getTextOrNull(eventNode, "kill_switch_id"));

                // Handle event_type (may be "event_type" or "action")
                String eventType = getTextOrNull(eventNode, "event_type");
                if (eventType == null) {
                    eventType = getTextOrNull(eventNode, "action");
                }
                event.setEventType(eventType);

                // Handle created_by (may be "created_by" or "performed_by")
                String createdBy = getTextOrNull(eventNode, "created_by");
                if (createdBy == null) {
                    createdBy = getTextOrNull(eventNode, "performed_by");
                }
                event.setCreatedBy(createdBy);

                // Handle created_at (may be "created_at" or "performed_at")
                java.time.Instant createdAt = parseInstant(eventNode, "created_at");
                if (createdAt == null) {
                    createdAt = parseInstant(eventNode, "performed_at");
                }
                event.setCreatedAt(createdAt);

                // Handle event_data
                if (eventNode.has("event_data") && !eventNode.get("event_data").isNull()) {
                    event.setEventData(objectMapper.convertValue(eventNode.get("event_data"),
                        new TypeReference<Map<String, Object>>() {}));
                } else {
                    // Build event_data from individual fields if present
                    Map<String, Object> eventData = new HashMap<>();
                    String prevStatus = getTextOrNull(eventNode, "previous_status");
                    String newStatus = getTextOrNull(eventNode, "new_status");
                    String reason = getTextOrNull(eventNode, "reason");
                    if (prevStatus != null) eventData.put("previous_status", prevStatus);
                    if (newStatus != null) eventData.put("new_status", newStatus);
                    if (reason != null) eventData.put("reason", reason);
                    if (!eventData.isEmpty()) {
                        event.setEventData(eventData);
                    }
                }

                events.add(event);
            }

            return events;
        }

        // ========================================================================
        // JSON Helper Methods
        // ========================================================================

        private String getTextOrNull(JsonNode node, String field) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
            return null;
        }

        private int getIntOrZero(JsonNode node, String field) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asInt();
            }
            return 0;
        }

        private Integer getIntegerOrNull(JsonNode node, String field) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asInt();
            }
            return null;
        }

        private Double getDoubleOrNull(JsonNode node, String field) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asDouble();
            }
            return null;
        }

        private java.time.Instant parseInstant(JsonNode node, String field) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText();
                try {
                    return java.time.Instant.parse(value);
                } catch (Exception e) {
                    logger.warn("Failed to parse timestamp '{}': {}", value, e.getMessage());
                }
            }
            return null;
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        cache.clear();
        logger.info("AxonFlow client closed");
    }
}
