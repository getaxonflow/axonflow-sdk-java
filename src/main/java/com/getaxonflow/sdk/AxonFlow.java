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
import com.getaxonflow.sdk.types.codegovernance.*;
import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

        // Gateway Mode: Let server decide if credentials are required based on DEPLOYMENT_MODE
        // Community/self-hosted deployments work without credentials

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/policy/pre-check", request);
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

        // Gateway Mode: Let server decide if credentials are required based on DEPLOYMENT_MODE
        // Community/self-hosted deployments work without credentials

        return retryExecutor.execute(() -> {
            Request httpRequest = buildRequest("POST", "/api/audit/llm-call", options);
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

            // Determine clientId: prefer config.getClientId(), fallback to license key
            String clientId = config.getClientId();
            if (clientId == null || clientId.isEmpty()) {
                clientId = config.getLicenseKey();
            }

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
                    null  // processingTime not available from ClientResponse
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
                return wrapper.getPolicies() != null ? wrapper.getPolicies() : java.util.Collections.emptyList();
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
                return wrapper.getStaticPolicies() != null ? wrapper.getStaticPolicies() : java.util.Collections.emptyList();
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
            String path = buildDynamicPolicyQueryString("/api/v1/policies", options);
            Request httpRequest = buildRequest("GET", path, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<List<DynamicPolicy>>() {});
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
            Request httpRequest = buildRequest("GET", "/api/v1/policies/" + policyId, null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, DynamicPolicy.class);
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
            Request httpRequest = buildRequest("POST", "/api/v1/policies", request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, DynamicPolicy.class);
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
            Request httpRequest = buildRequest("PUT", "/api/v1/policies/" + policyId, request);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, DynamicPolicy.class);
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
            Request httpRequest = buildRequest("DELETE", "/api/v1/policies/" + policyId, null);
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
            Request httpRequest = buildPatchRequest("/api/v1/policies/" + policyId, body);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, DynamicPolicy.class);
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
            StringBuilder path = new StringBuilder("/api/v1/policies/effective");
            if (options != null) {
                String query = buildEffectivePoliciesQuery(options);
                if (!query.isEmpty()) {
                    path.append("?").append(query);
                }
            }
            Request httpRequest = buildRequest("GET", path.toString(), null);
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response, new TypeReference<List<DynamicPolicy>>() {});
            }
        }, "getEffectiveDynamicPolicies");
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

        // Add tenant ID for policy APIs (uses clientId)
        if (config.getClientId() != null && !config.getClientId().isEmpty()) {
            builder.header("X-Tenant-ID", config.getClientId());
        }

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
        HttpUrl url = HttpUrl.parse(config.getAgentUrl() + path);
        if (url == null) {
            throw new ConfigurationException("Invalid URL: " + config.getAgentUrl() + path);
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

        if (options.getCategory() != null) {
            appendQueryParam(query, "category", options.getCategory().getValue());
        }
        if (options.getTier() != null) {
            appendQueryParam(query, "tier", options.getTier().getValue());
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
        // Add auth headers only when credentials are provided
        // Community/self-hosted mode works without credentials
        if (!config.hasCredentials()) {
            logger.debug("No credentials configured - community/self-hosted mode");
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

    /**
     * Requires credentials for enterprise features.
     *
     * @param feature the feature name for error message
     * @throws AuthenticationException if no credentials are configured
     */
    private void requireCredentials(String feature) {
        if (!config.hasCredentials()) {
            throw new AuthenticationException(
                feature + " requires credentials. Set licenseKey or clientId/clientSecret in config."
            );
        }
    }

    private void addTenantIdHeader(Request.Builder builder) {
        if (config.getClientId() != null && !config.getClientId().isEmpty()) {
            builder.header("X-Tenant-ID", config.getClientId());
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

    // ========================================================================
    // Code Governance - Git Provider APIs (Enterprise)
    // ========================================================================

    /**
     * Validates Git provider credentials without saving them.
     *
     * @param request the validation request with provider type and credentials
     * @return validation result
     * @throws IOException if the request fails
     */
    public ValidateGitProviderResponse validateGitProvider(ValidateGitProviderRequest request) throws IOException {
        logger.debug("Validating Git provider: {}", request.getType());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/git-providers/validate")
                .post(body);

        addAuthHeaders(builder);

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
        logger.debug("Configuring Git provider: {}", request.getType());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/git-providers")
                .post(body);

        addAuthHeaders(builder);

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
        logger.debug("Listing Git providers");

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/git-providers")
                .get();

        addAuthHeaders(builder);

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
        logger.debug("Deleting Git provider: {}", providerType);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/git-providers/" + providerType.getValue())
                .delete();

        addAuthHeaders(builder);

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
        logger.debug("Creating PR: {} in {}/{}", request.getTitle(), request.getOwner(), request.getRepo());

        String json = objectMapper.writeValueAsString(request);
        RequestBody body = RequestBody.create(json, JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/prs")
                .post(body);

        addAuthHeaders(builder);

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
        logger.debug("Listing PRs");

        StringBuilder url = new StringBuilder(config.getAgentUrl() + "/api/v1/code-governance/prs");
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

        addAuthHeaders(builder);

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
        logger.debug("Getting PR: {}", prId);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/prs/" + prId)
                .get();

        addAuthHeaders(builder);

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
        logger.debug("Syncing PR status: {}", prId);

        RequestBody body = RequestBody.create("{}", JSON);

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/prs/" + prId + "/sync")
                .post(body);

        addAuthHeaders(builder);

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
        logger.debug("Getting code governance metrics");

        Request.Builder builder = new Request.Builder()
                .url(config.getAgentUrl() + "/api/v1/code-governance/metrics")
                .get();

        addAuthHeaders(builder);
        addTenantIdHeader(builder);

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
        logger.debug("Exporting code governance data");

        StringBuilder url = new StringBuilder(config.getAgentUrl() + "/api/v1/code-governance/export");
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

        addAuthHeaders(builder);
        addTenantIdHeader(builder);

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
        logger.debug("Exporting code governance data as CSV");

        StringBuilder url = new StringBuilder(config.getAgentUrl() + "/api/v1/code-governance/export");
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

        addAuthHeaders(builder);
        addTenantIdHeader(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            handleErrorResponse(response);
            ResponseBody body = response.body();
            if (body == null) {
                throw new AxonFlowException("Empty response body", response.code(), null);
            }
            return body.string();
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
