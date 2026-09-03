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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.getaxonflow.sdk.authzen.AuthZENBulk;
import com.getaxonflow.sdk.authzen.AuthZENContract;
import com.getaxonflow.sdk.authzen.AuthZENDecision;
import com.getaxonflow.sdk.authzen.AuthZENEnvelope;
import com.getaxonflow.sdk.authzen.AuthZENError;
import com.getaxonflow.sdk.authzen.AuthZENErrorCode;
import com.getaxonflow.sdk.authzen.AuthZENEvaluationException;
import com.getaxonflow.sdk.authzen.AuthZENRefusedException;
import com.getaxonflow.sdk.authzen.AuthZENRequest;
import com.getaxonflow.sdk.authzen.AuthZENResponse;
import com.getaxonflow.sdk.authzen.AuthZENTransportException;
import com.getaxonflow.sdk.authzen.AuthZENUnreadableProfileException;
import com.getaxonflow.sdk.authzen.AuthZENUnresolvedException;
import com.getaxonflow.sdk.authzen.AuthZENUnusableResponseException;
import com.getaxonflow.sdk.exceptions.*;
import com.getaxonflow.sdk.identity.ReadIdentity;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import com.getaxonflow.sdk.simulation.*;
import com.getaxonflow.sdk.telemetry.HeartbeatState;
import com.getaxonflow.sdk.telemetry.TelemetryReporter;
import com.getaxonflow.sdk.types.*;
import com.getaxonflow.sdk.types.codegovernance.*;
import com.getaxonflow.sdk.types.costcontrols.CostControlTypes.*;
import com.getaxonflow.sdk.types.executionreplay.ExecutionReplayTypes.*;
import com.getaxonflow.sdk.types.hitl.HITLTypes.*;
import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
import com.getaxonflow.sdk.types.webhook.WebhookTypes.*;
import com.getaxonflow.sdk.util.*;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import okhttp3.*;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main client for interacting with the AxonFlow API.
 *
 * <p>The AxonFlow client provides methods for:
 *
 * <ul>
 *   <li><strong>Gateway Mode:</strong> Pre-check and audit for your own LLM calls
 *   <li><strong>Proxy Mode:</strong> Let AxonFlow handle policy and LLM routing
 *   <li><strong>Planning:</strong> Multi-agent planning (MAP) operations
 *   <li><strong>Connectors:</strong> MCP connector discovery and queries
 * </ul>
 *
 * <h2>Gateway Mode Example</h2>
 *
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
 *
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

  // Single-threaded daemon executor for async heartbeat dispatch from the
  // request hot path. Bounded to one worker so concurrent gate calls land
  // serially on the gate's mutex (the gate itself coalesces them via
  // in-flight + 1-hour cache). Daemon thread never blocks JVM exit.
  // Static so 10k req/s creates 0 extra threads — the alternative
  // (`new Thread()` per request) costs ~1ms per spawn at scale.
  private static final ExecutorService HEARTBEAT_EXECUTOR =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "axonflow-heartbeat");
              t.setDaemon(true);
              return t;
            }
          });

  private final AxonFlowConfig config;
  private final OkHttpClient httpClient;

  // Telemetry heartbeat is process-global — see HeartbeatState.shared().
  // No instance field here on purpose: concurrent AxonFlow constructions
  // on the same JVM must coalesce onto a single ping per
  // heartbeatInterval, which requires a shared singleton, not a per-
  // instance gate. Access via {@link #invokeHeartbeat()}.

  /**
   * Clone of {@link #httpClient} with {@code callTimeout} overridden to {@code
   * config.getMapTimeout()}. Used for every plan-lifecycle call (generate, execute, get, update,
   * cancel, resume, rollback) where a single call may outlive the default request timeout. MAP
   * plans chain multiple LLM calls end-to-end and commonly take 60-120s; the global timeout
   * (default 60s) would cut them off. Shares the connection pool, interceptors, and dispatcher with
   * {@link #httpClient} — only the call-timeout attribute differs.
   */
  private final OkHttpClient planHttpClient;

  private final ObjectMapper objectMapper;
  private final RetryExecutor retryExecutor;
  private final ResponseCache cache;
  private final Executor asyncExecutor;
  private volatile String sessionCookie; // Session cookie for Customer Portal authentication
  private final MASFEATNamespace masfeatNamespace;

  private AxonFlow(AxonFlowConfig config) {
    this.config = Objects.requireNonNull(config, "config cannot be null");

    // Reject clientSecret without clientId — licensed mode must specify tenant
    if (config.getClientSecret() != null
        && !config.getClientSecret().isEmpty()
        && (config.getClientId() == null || config.getClientId().isEmpty())) {
      throw new ConfigurationException(
          "clientId is required when clientSecret is set. "
              + "Set clientId to your tenant identity to avoid data being stored under the wrong tenant.",
          "clientId");
    }

    this.httpClient = HttpClientFactory.create(config);
    this.planHttpClient =
        this.httpClient
            .newBuilder()
            .callTimeout(
                config.getMapTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(
                config.getMapTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(
                config.getMapTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build();
    this.objectMapper = createObjectMapper();
    // FAIL_ON_UNKNOWN_PROPERTIES is the decode-side half of the surface's rule:
    // an unknown member in a decision is a server speaking a profile this build
    // does not understand, and quietly dropping it means acting on a partial
    // reading of an authorization decision.
    //
    // The coercion settings are the other half, and they close a quieter hole
    // (the ruling that landed in the Python sibling): without them Jackson
    // COERCES, so a decision arriving as the string "true" was read as the
    // boolean true and an obligation whose `mandatory` member arrived as 1 was
    // read as true. Those are type errors on the wire being silently repaired
    // into a reading nobody sent — on exactly the members that decide whether an
    // unsupported obligation must DENY.
    ObjectMapper strictReader =
        this.objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    strictReader.configure(
        com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
    com.fasterxml.jackson.databind.cfg.MutableCoercionConfig booleans =
        strictReader.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Boolean);
    booleans.setCoercion(
        com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    booleans.setCoercion(
        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    com.fasterxml.jackson.databind.cfg.MutableCoercionConfig integers =
        strictReader.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer);
    integers.setCoercion(
        com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    integers.setCoercion(
        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,
        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    // Float -> Integer is a THIRD shape, and it is the one that stays on after
    // ALLOW_COERCION_OF_SCALARS=false: `"quorum": 2.7` decoded as 2, silently
    // discarding the fraction. A quorum is a count of people, so a truncation
    // here is not a rounding difference but a different requirement being
    // enforced from the one the server sent. Every shape has to be named; the
    // blanket switch does not cover this one.
    integers.setCoercion(
        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    this.authzenReader = strictReader;
    this.retryExecutor = new RetryExecutor(config.getRetryConfig());
    this.cache = new ResponseCache(config.getCacheConfig());
    this.asyncExecutor = ForkJoinPool.commonPool();
    this.masfeatNamespace = new MASFEATNamespace();

    logger.info("AxonFlow client initialized for {}", config.getEndpoint());

    // Heartbeat gate — at most one anonymous ping per machine per 7 days,
    // gated by SDK activity. The constructor runs the gate synchronously
    // so a fresh install on a short-lived JVM (CLI binaries, AWS Lambda
    // cold-starts, quickstart scripts) still delivers the first ping
    // before main() returns. Subsequent gate runs (from executeHttp) are
    // dispatched ASYNCHRONOUSLY through a shared single-threaded daemon
    // executor so a 3-second telemetry POST never delays a user request.
    // See HeartbeatState for the full algorithm.
    invokeHeartbeat();
  }

  /**
   * A client identical to this one but presenting {@code userToken}.
   *
   * <p>The shape to reach for when one process acts on behalf of several people — a gateway, a bot.
   * Unlike the per-call {@code userToken} overload, which only the read methods accept, this
   * reaches EVERY method: there is no carve-out to remember and no path on which the identity
   * silently widens back to the process's own.
   *
   * <pre>{@code
   * AxonFlow forAlice = client.asUser(aliceToken);
   * List<DecisionSummary> rows = forAlice.listDecisions(null);
   * }</pre>
   *
   * <p>The returned client shares this one's CONNECTION POOL and dispatcher (OkHttp's {@code
   * newBuilder()} contract), so deriving one per request is cheap. It does NOT share the identity
   * interceptor: that one is rebuilt against the derived config, because an interceptor captured
   * from this client would keep reading THIS client's identity and {@code asUser} would silently
   * have no effect. Every proxy, timeout and TLS setting is carried across by {@code newBuilder()},
   * so the derivation cannot quietly lose an egress proxy either.
   *
   * <p>Sub-objects that hold a reference back to a client are rebuilt against the derived one for
   * the same reason. The session cookie is copied by value, so a portal login on either after the
   * derivation is invisible to the other; derive after logging in if the derived client needs the
   * portal plane, which authenticates with the cookie rather than with this identity.
   *
   * <p>An empty token returns a client presenting no identity at all, which on an enterprise stack
   * reads nothing.
   *
   * <p><b>Do not {@link #close()} a derived client.</b> Sharing the transport has one sharp edge:
   * {@code close()} shuts the dispatcher's executor and evicts the connection pool, and both are
   * the PARENT's. Closing a per-request derived client therefore takes the parent's transport down
   * with it, and every other derived client with it. A derived client owns no transport to release,
   * so there is nothing to close; let it be collected and close the parent once, at the end.
   *
   * @param userToken the per-user identity, or {@code null}/empty for none
   * @return a derived client bound to that identity
   */
  public AxonFlow asUser(String userToken) {
    String trimmed = userToken == null ? null : userToken.trim();
    AxonFlowConfig derivedConfig =
        config.toBuilder().userToken(trimmed == null || trimmed.isEmpty() ? null : trimmed).build();
    return new AxonFlow(this, derivedConfig);
  }

  /**
   * Derivation constructor: same pool, different identity.
   *
   * <p>Deliberately NOT a copy of every field. Anything holding a reference to the client that
   * created it is rebuilt here; copying such a reference would hand the derived client an object
   * that still calls through the ORIGINAL — with the original's identity — and the bug would only
   * appear when the parent touched it first, which is exactly the ordering a long-lived gateway
   * has.
   */
  private AxonFlow(AxonFlow parent, AxonFlowConfig derivedConfig) {
    this.config = derivedConfig;

    // Same pool and dispatcher (newBuilder()'s contract), and every proxy,
    // timeout and TLS setting carried across — but the identity interceptor is
    // REPLACED. The parent's captures the parent's config, so reusing it would
    // leave the derived client reading the parent's identity and asUser would
    // silently do nothing.
    OkHttpClient.Builder derivedBuilder = parent.httpClient.newBuilder();
    derivedBuilder
        .networkInterceptors()
        .removeIf(interceptor -> interceptor instanceof ReadIdentity.IdentityInterceptor);
    derivedBuilder.addNetworkInterceptor(
        ReadIdentity.interceptor(derivedConfig.getEndpoint(), derivedConfig::getUserToken));
    this.httpClient = derivedBuilder.build();

    this.planHttpClient =
        this.httpClient
            .newBuilder()
            .callTimeout(
                derivedConfig.getMapTimeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(
                derivedConfig.getMapTimeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(
                derivedConfig.getMapTimeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)
            .build();

    // Shared: stateless or deliberately common across the pair.
    this.objectMapper = parent.objectMapper;
    this.authzenReader = parent.authzenReader;
    this.retryExecutor = parent.retryExecutor;
    this.cache = parent.cache;
    this.asyncExecutor = parent.asyncExecutor;
    this.sessionCookie = parent.sessionCookie;

    // Rebuilt, NOT copied: it holds a reference to the client that created it,
    // so a copy would still call through the parent, under the parent's
    // identity. The R3 mutant that replaced this with
    // `parent.masfeatNamespace` SURVIVED 33 tests — the line was right and
    // nothing checked it, which is the same shape as a claim with no test.
    // ReadIdentityTest#aDerivedClientRebuildsEveryClientBoundMember now does.
    this.masfeatNamespace = new MASFEATNamespace();

    // No heartbeat here: the gate is process-global and the parent already ran
    // it. Firing one per derived client would turn a per-request derivation
    // into a per-request telemetry gate.
  }

  /**
   * Run the heartbeat gate against the process-global singleton. Constructs the gating decision
   * from this client's mode/config, then asks {@link HeartbeatState#shared()} to decide whether to
   * send (and to write the stamp on success).
   *
   * <p>This call is synchronous and bounded by the per-call HTTP timeout (3s) WHEN the gate decides
   * to fire. When the gate decides not to fire (typical hot-path case after the first cold-start
   * ping), the cost is a single mutex acquire and a {@code System.currentTimeMillis()} comparison.
   *
   * <p>For the request hot path, see {@link #invokeHeartbeatAsync()}, which delegates to a daemon
   * thread so a 3-second firing-block never delays a user API call.
   */
  private void invokeHeartbeat() {
    String modeStr = config.getMode() != null ? config.getMode().getValue() : "production";
    String envOptOut = System.getenv("AXONFLOW_TELEMETRY");
    // v8: AXONFLOW_TELEMETRY=off is the SOLE opt-out path. The v7.x mode-based suppression
    // and the AxonFlowConfig.telemetry(Boolean) override were both removed. Sandbox-mode
    // pings now fire and are tagged stream="sandbox" in the payload.
    boolean isEnabled = TelemetryReporter.isEnabled(envOptOut);
    HeartbeatState.shared()
        .maybeSendHeartbeat(
            isEnabled,
            () ->
                TelemetryReporter.sendPingNow(
                    modeStr,
                    config.getEndpoint(),
                    config.isDebug(),
                    System.getenv("AXONFLOW_CHECKPOINT_URL")));
  }

  /**
   * Async variant of {@link #invokeHeartbeat()} — dispatches the gate onto {@link
   * #HEARTBEAT_EXECUTOR} so a user-facing API call is never delayed by the 3-second telemetry POST
   * when the gate decides to fire.
   *
   * <p>The executor is a single-threaded daemon — concurrent dispatches queue rather than spawning
   * threads (10k req/s would otherwise create 10k threads/s pre-fix). The gate's in-flight + 1-hour
   * cache means queued runs immediately fast-path past the work, so queue depth is bounded in
   * practice.
   *
   * <p>Daemon thread choice: long-running services have stable JVMs so the executor completes the
   * POST normally. Short-lived processes (Lambda cold start, CLI binaries) deliver the boot ping
   * via the synchronous {@link #invokeHeartbeat} call from the constructor, so the async
   * request-path heartbeat is "extra" — its loss to JVM exit is acceptable and only matters across
   * the 7-day boundary.
   */
  private void invokeHeartbeatAsync() {
    try {
      HEARTBEAT_EXECUTOR.execute(this::invokeHeartbeat);
    } catch (RuntimeException e) {
      // Executor rejected (e.g. shutdown during JVM teardown) — telemetry
      // is best-effort; the user request continues unaffected.
      logger.debug("heartbeat dispatch rejected", e);
    }
  }

  /**
   * Single HTTP wrapper used by every public-API request path. Invokes the heartbeat gate as a side
   * effect, ASYNCHRONOUSLY so the user's API call is never delayed by telemetry.
   *
   * <p>IMPORTANT: This wrapper must NOT be called from telemetry code itself ({@link
   * TelemetryReporter#sendPingNow} or its private helpers). Those build their own throw-away {@code
   * OkHttpClient} instances to avoid any recursive heartbeat triggering.
   */
  private Response executeHttp(OkHttpClient client, Request request) throws java.io.IOException {
    invokeHeartbeatAsync();
    return client.newCall(request).execute();
  }

  // MIRROR NOTE: wire-shape Gate 5's introspection probe
  // (scripts/wire_shape/AuditWireKeysProbe.java) obtains its mapper by
  // reflecting THIS factory, so its view of the wire always matches
  // production configuration. Renaming or removing this method breaks the
  // gate loudly (probe exit 2 -> gate FAIL), which is intentional - update
  // the probe in the same change.
  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
    return mapper;
  }

  /**
   * Compares two semantic version strings numerically (major.minor.patch). Returns negative if a <
   * b, zero if equal, positive if a > b.
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
          String cleanA =
              partsA[i].contains("-") ? partsA[i].substring(0, partsA[i].indexOf("-")) : partsA[i];
          numA = Integer.parseInt(cleanA);
        } catch (NumberFormatException ignored) {
          // default to 0
        }
      }
      if (i < partsB.length) {
        try {
          String cleanB =
              partsB[i].contains("-") ? partsB[i].substring(0, partsB[i].indexOf("-")) : partsB[i];
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
    return new AxonFlow(AxonFlowConfig.builder().agentUrl(agentUrl).mode(Mode.SANDBOX).build());
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
    HealthStatus status =
        retryExecutor.execute(
            () -> {
              Request request = buildRequest("GET", "/health", null);
              try (Response response = executeHttp(httpClient, request)) {
                return parseResponse(response, HealthStatus.class);
              }
            },
            "healthCheck");

    String minJavaVersion =
        status.getSdkCompatibility() != null
            ? status.getSdkCompatibility().getMinSdkVersionFor("java")
            : null;
    if (minJavaVersion != null
        && !"unknown".equals(AxonFlowConfig.SDK_VERSION)
        && compareSemver(AxonFlowConfig.SDK_VERSION, minJavaVersion) < 0) {
      logger.warn(
          "SDK version {} is below minimum supported version {}. Please upgrade.",
          AxonFlowConfig.SDK_VERSION,
          minJavaVersion);
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
   * Returns the MAS FEAT (Monetary Authority of Singapore - Fairness, Ethics, Accountability,
   * Transparency) compliance namespace.
   *
   * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
   *
   * <p>Example usage:
   *
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
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("GET", "/health", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              return new HealthStatus("unhealthy", null, null, null, null, null);
            }
            return parseResponse(response, HealthStatus.class);
          }
        },
        "orchestratorHealthCheck");
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
   * <p>This is the first step in Gateway Mode. If approved, make your LLM call directly, then call
   * {@link #auditLLMCall(AuditOptions)} to complete the flow.
   *
   * @param request the policy approval request
   * @return the approval result with context ID for auditing
   * @throws PolicyViolationException if the request is blocked by policy
   * @throws AuthenticationException if authentication fails
   */
  public PolicyApprovalResult getPolicyApprovedContext(PolicyApprovalRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    // Use smart default for clientId - enables zero-config community mode
    String effectiveClientId =
        (request.getClientId() != null && !request.getClientId().isEmpty())
            ? request.getClientId()
            : getEffectiveClientId();

    Map<String, Object> ctx = request.getContext();
    PolicyApprovalRequest effectiveRequest =
        PolicyApprovalRequest.builder()
            .userToken(request.getUserToken())
            .query(request.getQuery())
            .dataSources(request.getDataSources())
            .context(ctx == null || ctx.isEmpty() ? null : ctx)
            .clientId(effectiveClientId)
            .build();

    final PolicyApprovalRequest finalRequest = effectiveRequest;
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("POST", "/api/policy/pre-check", finalRequest);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            PolicyApprovalResult result = parseResponse(response, PolicyApprovalResult.class);

            if (!result.isApproved()) {
              throw new PolicyViolationException(
                  result.getBlockReason(), result.getBlockingPolicyName(), result.getPolicies());
            }

            return result;
          }
        },
        "getPolicyApprovedContext");
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
  public CompletableFuture<PolicyApprovalResult> getPolicyApprovedContextAsync(
      PolicyApprovalRequest request) {
    return CompletableFuture.supplyAsync(() -> getPolicyApprovedContext(request), asyncExecutor);
  }

  /**
   * Audits an LLM call for compliance tracking (Gateway Mode - Step 3).
   *
   * <p>Call this after making your direct LLM call to record it for compliance and observability.
   *
   * @param options the audit options including context ID from pre-check
   * @return the audit result
   * @throws AxonFlowException if the audit fails
   */
  public AuditResult auditLLMCall(AuditOptions options) {
    Objects.requireNonNull(options, "options cannot be null");

    // Use smart default for clientId - enables zero-config community mode
    String effectiveClientId =
        (options.getClientId() != null && !options.getClientId().isEmpty())
            ? options.getClientId()
            : getEffectiveClientId();

    // Create effective options with the smart default clientId
    AuditOptions.Builder builder =
        AuditOptions.builder()
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("POST", "/api/audit/llm-call", effectiveOptions);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, AuditResult.class);
          }
        },
        "auditLLMCall");
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
   *
   * <pre>{@code
   * AuditSearchResponse response = axonflow.searchAuditLogs(
   *     AuditSearchRequest.builder()
   *         .userEmail("analyst@company.com")
   *         .startTime(Instant.now().minus(Duration.ofDays(7)))
   *         .action("blocked")
   *         .limit(100)
   *         .build());
   *
   * for (AuditLogEntry entry : response.getEntries()) {
   *     System.out.println(entry.getId() + ": " + entry.getPolicyDecision());
   * }
   * }</pre>
   *
   * @param request the search request with optional filters
   * @return the search response containing matching audit log entries
   * @throws AxonFlowException if the search fails
   */
  public AuditSearchResponse searchAuditLogs(AuditSearchRequest request) {
    return retryExecutor.execute(
        () -> {
          AuditSearchRequest req = request != null ? request : AuditSearchRequest.builder().build();
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/audit/search", req);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            AuditSearchResponse page =
                decodeAuditPage(
                    node,
                    req.getLimit() != null ? req.getLimit() : 100,
                    req.getOffset() != null ? req.getOffset() : 0);
            // The audit reads are in the same role-scoped family as decisions
            // (platform/orchestrator applyReadScopeHeader), so they inherit the
            // same rule: an empty page under scope `none` could not have
            // contained a row, and reporting it as data is the vacuous read
            // this SDK now refuses. Applied to the DECODED page, so it holds on
            // BOTH wire shapes rather than on whichever branch the server took.
            ReadScopeException scoped =
                ReadIdentity.refuseVacuousScopedPage(
                    response,
                    "audit entries",
                    page.getEntries() == null ? 0 : page.getEntries().size());
            if (scoped != null) {
              throw scoped;
            }
            return page;
          }
        },
        "searchAuditLogs");
  }

  /**
   * Decodes an audit page in either shape the platform sends: a bare array, or the wrapped envelope
   * with its own total/limit/offset.
   *
   * <p>Extracted because both audit reads decoded it inline, identically, with a separate return
   * per shape — four places for one rule to be applied in, and the scope refusal above would have
   * had to be written in all four to hold.
   */
  private AuditSearchResponse decodeAuditPage(JsonNode node, int limit, int offset)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    if (node.isArray()) {
      List<AuditLogEntry> entries =
          objectMapper.convertValue(node, new TypeReference<List<AuditLogEntry>>() {});
      return AuditSearchResponse.fromArray(entries, limit, offset);
    }
    return objectMapper.treeToValue(node, AuditSearchResponse.class);
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
   * Fetches the full explanation for a previously-made policy decision.
   *
   * <p>Implements ADR-043 (Explainability Data Contract). Calls {@code GET
   * /api/v1/decisions/:id/explain} and returns a {@link DecisionExplanation} including matched
   * policies, risk level, reason, override availability, existing override ID (if any), and a
   * rolling-24h session hit count for the matched rule.
   *
   * <p>The caller must either own the decision (user_email match) or belong to the same tenant as
   * the decision's originator.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * DecisionExplanation exp = axonflow.explainDecision("dec_wf123_step4");
   * if (exp.isOverrideAvailable()) {
   *     // offer the user a governed override action
   * }
   * }</pre>
   *
   * @param decisionId the global decision identifier returned in the original step gate or policy
   *     evaluation response
   * @return the decision explanation (frozen shape per ADR-043)
   * @throws IllegalArgumentException if decisionId is null or empty
   * @throws AxonFlowException if the request fails or the decision is past retention
   */
  public DecisionExplanation explainDecision(String decisionId) {
    return explainDecision(decisionId, null);
  }

  /**
   * Fetches a decision explanation as a specific per-user identity.
   *
   * <p>Overrides the client-wide {@code userToken} for THIS call only. Use it when one process acts
   * on behalf of several people. An empty string is not an identity: it makes this read explicitly
   * unidentified rather than falling back to the client-wide one — a distinction that has to exist,
   * because "unidentified" is a state the platform treats as different from every other.
   *
   * <p>For a process acting for several people across MANY methods, prefer {@link #asUser(String)}:
   * this overload exists only on the read methods, while a derived client reaches every method with
   * no carve-out.
   *
   * @param decisionId the global decision identifier
   * @param userToken the per-user identity for this call, or {@code null} to use the client's
   * @return the decision explanation
   * @throws ReadScopeException when the decision is not among the rows this identity can see, or no
   *     identity was resolved. Check {@code isIdentityMissing()} to tell those apart.
   */
  public DecisionExplanation explainDecision(String decisionId, String userToken) {
    if (decisionId == null || decisionId.isEmpty()) {
      throw new IllegalArgumentException("decisionId is required");
    }
    AxonFlow scoped = userToken == null ? this : asUser(userToken);
    return scoped.explainDecisionScoped(decisionId);
  }

  private DecisionExplanation explainDecisionScoped(String decisionId) {
    return retryExecutor.execute(
        () -> {
          // Path-segment encoding: URLEncoder is application/x-www-form-urlencoded
          // (space -> '+'), which is wrong for path segments. Replacing '+' with
          // '%20' converts the form-encoded output into a valid percent-encoded
          // path segment, matching how Go / Python / TypeScript escape the
          // decision_id in this path.
          String encoded =
              java.net.URLEncoder.encode(decisionId, java.nio.charset.StandardCharsets.UTF_8)
                  .replace("+", "%20");
          String path = "/api/v1/decisions/" + encoded + "/explain";
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // A scoped miss reports WHY it missed. Only 404 is interpreted: the
            // scope header is stamped before the handler writes its status, so
            // it also rides a 500 from further down the handler, and explaining
            // a server fault as a scoping outcome would be exactly the
            // confidently-wrong diagnosis this type exists to prevent.
            if (response.code() == 404) {
              ReadScopeException scoped =
                  ReadIdentity.scopeErrorFor(
                      "decision", decisionId, ReadIdentity.scopeOf(response), response.code());
              if (scoped != null) {
                throw scoped;
              }
            }
            JsonNode node = parseResponseNode(response);
            return objectMapper.treeToValue(node, DecisionExplanation.class);
          }
        },
        "explainDecision");
  }

  /**
   * Asynchronously fetches a decision explanation.
   *
   * @param decisionId the global decision identifier
   * @return a future containing the decision explanation
   */
  public CompletableFuture<DecisionExplanation> explainDecisionAsync(String decisionId) {
    return CompletableFuture.supplyAsync(() -> explainDecision(decisionId), asyncExecutor);
  }

  // ============================================================================
  // listDecisions — Session γ (#1982)
  // ============================================================================

  /**
   * Lists recent policy decisions for the caller's tenant (Session γ / #1982).
   *
   * <p>Returns the slim 5-field {@link DecisionSummary} page; the platform applies a tier-gated cap
   * (5/24h Free + Community, 100/30d Pro + Evaluation, 1000/full retention Enterprise). Over-cap
   * requests yield a 429 with the V1 upgrade envelope, surfaced as {@link RateLimitException}
   * carrying {@code limitType}, {@code tier}, and {@code upgrade.{tier,compareUrl,buyUrl}}.
   *
   * <p>Filters compose; null fields are omitted from the URL so the platform applies tier defaults.
   *
   * <p>Example:
   *
   * <pre>{@code
   * try {
   *     List<DecisionSummary> decisions = axonflow.listDecisions(
   *         ListDecisionsOptions.builder().decision("blocked").limit(10).build());
   *     for (DecisionSummary d : decisions) {
   *         System.out.println(d.getDecisionId() + " " + d.getDecision());
   *     }
   * } catch (RateLimitException rle) {
   *     if (rle.getUpgrade() != null) {
   *         System.out.println("Upgrade at: " + rle.getUpgrade().getBuyUrl());
   *     }
   * }
   * }</pre>
   *
   * @param opts filter and page-size options; null returns the tier-default page
   * @return list of {@code DecisionSummary} rows ordered newest-first
   * @throws RateLimitException 429 tier-cap; {@code rle.getUpgrade()} exposes
   *     tier/compareUrl/buyUrl
   * @throws AxonFlowException other HTTP errors (401, 5xx, etc.)
   */
  public List<DecisionSummary> listDecisions(ListDecisionsOptions opts) {
    return listDecisions(opts, null);
  }

  /**
   * Lists the decisions visible to a specific per-user identity.
   *
   * <p>Overrides the client-wide {@code userToken} for THIS call only. See {@link
   * #explainDecision(String, String)} for the semantics, and {@link #asUser(String)} for the shape
   * to prefer when one process acts for several people across many methods.
   *
   * @param opts filter + page-size options (may be null)
   * @param userToken the per-user identity for this call, or {@code null} to use the client's
   * @return the decisions visible to that identity
   * @throws ReadScopeException when the page was empty because no per-user identity was resolved,
   *     so it could not have contained a row
   */
  public List<DecisionSummary> listDecisions(ListDecisionsOptions opts, String userToken) {
    AxonFlow scoped = userToken == null ? this : asUser(userToken);
    return scoped.listDecisionsScoped(opts);
  }

  private List<DecisionSummary> listDecisionsScoped(ListDecisionsOptions opts) {
    return retryExecutor.execute(
        () -> {
          String path = "/api/v1/decisions" + buildListDecisionsQuery(opts);
          Request httpRequest = buildOrchestratorRequest("GET", path, null);

          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (response.code() == 429) {
              // Try to parse the V1 upgrade envelope. If the body changed
              // shape we still surface the 429 — never silently succeed.
              String body = response.body() != null ? response.body().string() : "";
              try {
                JsonNode envelope = objectMapper.readTree(body);
                JsonNode limitTypeNode = envelope.get("limit_type");
                if (limitTypeNode != null && !limitTypeNode.isNull()) {
                  RateLimitException.UpgradeInfo upgrade = null;
                  JsonNode upgradeNode = envelope.get("upgrade");
                  if (upgradeNode != null && upgradeNode.isObject()) {
                    upgrade =
                        new RateLimitException.UpgradeInfo(
                            optString(upgradeNode, "tier"),
                            optString(upgradeNode, "wording"),
                            optString(upgradeNode, "compare_url"),
                            optString(upgradeNode, "buy_url"));
                  }
                  throw new RateLimitException(
                      optString(envelope, "error"),
                      envelope.has("limit") ? envelope.get("limit").asInt() : 0,
                      envelope.has("remaining") ? envelope.get("remaining").asInt() : 0,
                      null,
                      limitTypeNode.asText(),
                      optString(envelope, "tier"),
                      upgrade);
                }
              } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // fall through — never silently succeed on 429
              }
              throw new AxonFlowException("Too Many Requests: " + body, 429, null);
            }
            JsonNode node = parseResponseNode(response);
            JsonNode decisionsNode = node.get("decisions");
            java.util.List<DecisionSummary> result = new java.util.ArrayList<>();
            if (decisionsNode != null && decisionsNode.isArray()) {
              for (JsonNode row : decisionsNode) {
                result.add(objectMapper.treeToValue(row, DecisionSummary.class));
              }
            }
            // An empty page under ReadScope.NONE is the fail-closed shape, not
            // a finding: the platform returned zero rows because it resolved no
            // identity to scope on, so the page says nothing about what exists.
            ReadScopeException scoped =
                ReadIdentity.refuseVacuousScopedPage(response, "decisions", result.size());
            if (scoped != null) {
              throw scoped;
            }
            return result;
          }
        },
        "listDecisions");
  }

  /**
   * Asynchronously lists recent decisions for the caller's tenant.
   *
   * @param opts filter + page-size options (may be null)
   * @return a future resolving to the list of summaries
   */
  public CompletableFuture<List<DecisionSummary>> listDecisionsAsync(ListDecisionsOptions opts) {
    return CompletableFuture.supplyAsync(() -> listDecisions(opts), asyncExecutor);
  }

  /** Reads a string field, returning null when absent or not textual. */
  private static String optString(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return (v == null || v.isNull()) ? null : v.asText();
  }

  /**
   * Serialize {@link ListDecisionsOptions} into a "?k=v&k=v" query string. Empty when opts or all
   * fields are null. Stable field order so test mocks can match the URL exactly.
   */
  static String buildListDecisionsQuery(ListDecisionsOptions opts) {
    if (opts == null) {
      return "";
    }
    java.util.List<String> pairs = new java.util.ArrayList<>(5);
    if (opts.getSince() != null) {
      // Instant.toString() already emits RFC 3339 with the "Z" UTC marker.
      pairs.add("since=" + urlEncode(opts.getSince().toString()));
    }
    if (opts.getDecision() != null) {
      pairs.add("decision=" + urlEncode(opts.getDecision()));
    }
    if (opts.getPolicyId() != null) {
      pairs.add("policy_id=" + urlEncode(opts.getPolicyId()));
    }
    if (opts.getToolSignature() != null) {
      pairs.add("tool_signature=" + urlEncode(opts.getToolSignature()));
    }
    if (opts.getLimit() != null) {
      pairs.add("limit=" + opts.getLimit());
    }
    if (pairs.isEmpty()) {
      return "";
    }
    return "?" + String.join("&", pairs);
  }

  private static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Gets audit logs for a specific tenant.
   *
   * <p>Example usage:
   *
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

    return retryExecutor.execute(
        () -> {
          AuditQueryOptions opts = options != null ? options : AuditQueryOptions.defaults();
          String encodedTenantId = java.net.URLEncoder.encode(tenantId, "UTF-8");
          String path =
              "/api/v1/audit/tenant/"
                  + encodedTenantId
                  + "?limit="
                  + opts.getLimit()
                  + "&offset="
                  + opts.getOffset();

          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            AuditSearchResponse page = decodeAuditPage(node, opts.getLimit(), opts.getOffset());
            // Same rule, same family as searchAuditLogs.
            ReadScopeException scoped =
                ReadIdentity.refuseVacuousScopedPage(
                    response,
                    "audit entries",
                    page.getEntries() == null ? 0 : page.getEntries().size());
            if (scoped != null) {
              throw scoped;
            }
            return page;
          }
        },
        "getAuditLogsByTenant");
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
  public CompletableFuture<AuditSearchResponse> getAuditLogsByTenantAsync(
      String tenantId, AuditQueryOptions options) {
    return CompletableFuture.supplyAsync(
        () -> getAuditLogsByTenant(tenantId, options), asyncExecutor);
  }

  // ========================================================================
  // Audit Tool Call
  // ========================================================================

  /**
   * Audits a non-LLM tool call for compliance and observability.
   *
   * <p>Records tool invocations such as function calls, MCP operations, or API calls to the audit
   * log.
   *
   * <p>Example usage:
   *
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/audit/tool-call", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, AuditToolCallResponse.class);
          }
        },
        "auditToolCall");
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
   *
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
    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/circuit-breaker/status", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), CircuitBreakerStatusResponse.class);
            }
            return objectMapper.treeToValue(node, CircuitBreakerStatusResponse.class);
          }
        },
        "getCircuitBreakerStatus");
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
   *
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

    return retryExecutor.execute(
        () -> {
          String path = "/api/v1/circuit-breaker/history?limit=" + limit;
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(
                  node.get("data"), CircuitBreakerHistoryResponse.class);
            }
            return objectMapper.treeToValue(node, CircuitBreakerHistoryResponse.class);
          }
        },
        "getCircuitBreakerHistory");
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
   *
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

    return retryExecutor.execute(
        () -> {
          String path =
              "/api/v1/circuit-breaker/config?tenant_id="
                  + java.net.URLEncoder.encode(tenantId, java.nio.charset.StandardCharsets.UTF_8);
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), CircuitBreakerConfig.class);
            }
            return objectMapper.treeToValue(node, CircuitBreakerConfig.class);
          }
        },
        "getCircuitBreakerConfig");
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
   *
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
  public CircuitBreakerConfigUpdateResponse updateCircuitBreakerConfig(
      CircuitBreakerConfigUpdate config) {
    Objects.requireNonNull(config, "config cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("PUT", "/api/v1/circuit-breaker/config", config);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(
                  node.get("data"), CircuitBreakerConfigUpdateResponse.class);
            }
            return objectMapper.treeToValue(node, CircuitBreakerConfigUpdateResponse.class);
          }
        },
        "updateCircuitBreakerConfig");
  }

  /**
   * Asynchronously updates the circuit breaker configuration for a tenant.
   *
   * @param config the configuration update
   * @return a future containing the update confirmation
   */
  public CompletableFuture<CircuitBreakerConfigUpdateResponse> updateCircuitBreakerConfigAsync(
      CircuitBreakerConfigUpdate config) {
    return CompletableFuture.supplyAsync(() -> updateCircuitBreakerConfig(config), asyncExecutor);
  }

  // ========================================================================
  // Policy Simulation
  // ========================================================================

  /**
   * Simulates policy evaluation against a query without actually enforcing policies.
   *
   * <p>This is a dry-run mode that shows which policies would match and what actions would be
   * taken, without blocking the request.
   *
   * <p>Example usage:
   *
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/policies/simulate", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), SimulatePoliciesResponse.class);
            }
            return objectMapper.treeToValue(node, SimulatePoliciesResponse.class);
          }
        },
        "simulatePolicies");
  }

  /**
   * Asynchronously simulates policy evaluation against a query.
   *
   * @param request the simulation request
   * @return a future containing the simulation result
   */
  public CompletableFuture<SimulatePoliciesResponse> simulatePoliciesAsync(
      SimulatePoliciesRequest request) {
    return CompletableFuture.supplyAsync(() -> simulatePolicies(request), asyncExecutor);
  }

  /**
   * Generates a policy impact report by testing a set of inputs against a specific policy.
   *
   * <p>This helps you understand how a policy would affect real traffic before deploying it.
   *
   * <p>Example usage:
   *
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/policies/impact-report", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), ImpactReportResponse.class);
            }
            return objectMapper.treeToValue(node, ImpactReportResponse.class);
          }
        },
        "getPolicyImpactReport");
  }

  /**
   * Asynchronously generates a policy impact report.
   *
   * @param request the impact report request
   * @return a future containing the impact report
   */
  public CompletableFuture<ImpactReportResponse> getPolicyImpactReportAsync(
      ImpactReportRequest request) {
    return CompletableFuture.supplyAsync(() -> getPolicyImpactReport(request), asyncExecutor);
  }

  /**
   * Scans all active policies for conflicts.
   *
   * <p>Example usage:
   *
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
   * Detects conflicts between a specific policy and other active policies, or scans all policies if
   * policyId is null.
   *
   * <p>Example usage:
   *
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

    return retryExecutor.execute(
        () -> {
          Object body;
          if (policyId != null) {
            body = java.util.Map.of("policy_id", policyId);
          } else {
            body = java.util.Map.of();
          }
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/policies/conflicts", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), PolicyConflictResponse.class);
            }
            return objectMapper.treeToValue(node, PolicyConflictResponse.class);
          }
        },
        "detectPolicyConflicts");
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
   *
   * <ul>
   *   <li>Evaluate policies before the LLM call
   *   <li>Make the LLM call to the configured provider
   *   <li>Filter/redact sensitive data from responses
   *   <li>Automatically track costs and audit the interaction
   * </ul>
   *
   * <p>For Gateway Mode (lower latency, you make the LLM call), use:
   *
   * <ul>
   *   <li>{@link #getPolicyApprovedContext} before your LLM call
   *   <li>{@link #auditLLMCall} after your LLM call
   * </ul>
   *
   * @param request the client request
   * @return the response from AxonFlow
   * @throws PolicyViolationException if the request is blocked by policy
   * @throws AuthenticationException if authentication fails
   */
  public ClientResponse proxyLLMCall(ClientRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    // Auto-populate clientId from config if not set in request (matches Go/Python/TypeScript SDK
    // behavior)
    ClientRequest effectiveRequest = request;
    if ((request.getClientId() == null || request.getClientId().isEmpty())
        && config.getClientId() != null
        && !config.getClientId().isEmpty()) {
      effectiveRequest =
          ClientRequest.builder()
              .query(request.getQuery())
              .userToken(request.getUserToken())
              .clientId(config.getClientId())
              .requestType(
                  request.getRequestType() != null
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
    String cacheKey =
        ResponseCache.generateKey(
            finalRequest.getRequestType(), finalRequest.getQuery(), finalRequest.getUserToken());

    if (!hasMedia) {
      java.util.Optional<ClientResponse> cached = cache.get(cacheKey, ClientResponse.class);
      if (cached.isPresent()) {
        return cached.get();
      }
    }

    ClientResponse response =
        retryExecutor.execute(
            () -> {
              Request httpRequest = buildRequest("POST", "/api/request", finalRequest);
              try (Response httpResponse = executeHttp(httpClient, httpRequest)) {
                ClientResponse result = parseResponse(httpResponse, ClientResponse.class);

                if (result.isBlocked()) {
                  throw new PolicyViolationException(
                      result.getBlockReason(),
                      result.getBlockingPolicyName(),
                      result.getPolicyInfo() != null
                          ? result.getPolicyInfo().getPoliciesEvaluated()
                          : null);
                }

                return result;
              }
            },
            "proxyLLMCall");

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
   * <p>This method uses the Agent API with request_type "multi-agent-plan" to generate and execute
   * plans through the governance layer.
   *
   * @param request the plan request
   * @return the generated plan
   * @throws PlanExecutionException if plan generation fails
   */
  public PlanResponse generatePlan(PlanRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parsePlanResponse(response, request.getDomain());
          }
        },
        "generatePlan");
  }

  /**
   * Parses the Agent API response format into PlanResponse. The Agent API returns: {success,
   * plan_id, data: {steps, domain, ...}, metadata, result}
   */
  @SuppressWarnings("unchecked")
  private PlanResponse parsePlanResponse(Response response, String requestDomain)
      throws IOException {
    handleErrorResponse(response);

    ResponseBody body = response.body();
    if (body == null) {
      throw new AxonFlowException("Empty response body", response.code(), null);
    }

    String json = body.string();
    Map<String, Object> agentResponse =
        objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

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
        steps =
            rawSteps.stream()
                .map(stepMap -> objectMapper.convertValue(stepMap, PlanStep.class))
                .collect(java.util.stream.Collectors.toList());
      }
      domain = data.get("domain") != null ? (String) data.get("domain") : domain;
      complexity =
          data.get("complexity") != null ? ((Number) data.get("complexity")).intValue() : null;
      parallel = (Boolean) data.get("parallel");
      estimatedDuration = (String) data.get("estimated_duration");
    }

    return new PlanResponse(
        planId, steps, domain, complexity, parallel, estimatedDuration, metadata, null, result);
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

    // executePlan is a mutation — do NOT retry (retrying causes 409 "Plan has already been
    // executed")
    try {
      // Build agent request format - like generatePlan but with request_type "execute-plan"
      String token =
          userToken != null
              ? userToken
              : (config.getClientId() != null ? config.getClientId() : "default");
      String clientId = config.getClientId() != null ? config.getClientId() : "default";

      Map<String, Object> agentRequest = new java.util.HashMap<>();
      agentRequest.put("query", "");
      agentRequest.put("user_token", token);
      agentRequest.put("client_id", clientId);
      agentRequest.put("request_type", "execute-plan");
      agentRequest.put("context", Map.of("plan_id", planId));

      Request httpRequest = buildRequest("POST", "/api/request", agentRequest);
      try (Response response = executeHttp(planHttpClient, httpRequest)) {
        return parseExecutePlanResponse(response, planId);
      }
    } catch (AxonFlowException e) {
      throw e;
    } catch (Exception e) {
      throw new PlanExecutionException("executePlan failed: " + e.getMessage(), planId, null, e);
    }
  }

  /** Parses the execute plan response. */
  @SuppressWarnings("unchecked")
  private PlanResponse parseExecutePlanResponse(Response response, String planId)
      throws IOException {
    handleErrorResponse(response);

    ResponseBody body = response.body();
    if (body == null) {
      throw new AxonFlowException("Empty response body", response.code(), null);
    }

    String json = body.string();
    Map<String, Object> agentResponse =
        objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

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
    return new PlanResponse(
        planId, Collections.emptyList(), null, null, null, null, null, status, result);
  }

  /**
   * Gets the status of a plan.
   *
   * @param planId the plan ID
   * @return the plan status
   */
  public PlanResponse getPlanStatus(String planId) {
    Objects.requireNonNull(planId, "planId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/plan/" + planId, null);
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parseResponse(response, PlanResponse.class);
          }
        },
        "getPlanStatus");
  }

  /**
   * Generates a multi-agent plan with additional options.
   *
   * <p>This overload allows specifying execution mode and other generation options beyond what is
   * in the base {@link PlanRequest}.
   *
   * @param request the plan request
   * @param options additional generation options
   * @return the generated plan
   * @throws PlanExecutionException if plan generation fails
   */
  public PlanResponse generatePlan(PlanRequest request, GeneratePlanOptions options) {
    Objects.requireNonNull(request, "request cannot be null");
    Objects.requireNonNull(options, "options cannot be null");

    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parsePlanResponse(response, request.getDomain());
          }
        },
        "generatePlan");
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

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = new java.util.HashMap<>();
          if (reason != null) {
            body.put("reason", reason);
          }

          Request httpRequest =
              buildRequest(
                  "POST", "/api/v1/plan/" + planId + "/cancel", body.isEmpty() ? null : body);
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parseResponse(response, CancelPlanResponse.class);
          }
        },
        "cancelPlan");
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
   * <p>The request must include the expected version number. If the version does not match the
   * current server version, a {@link VersionConflictException} is thrown.
   *
   * @param planId the ID of the plan to update
   * @param request the update request with version and changes
   * @return the update result
   * @throws VersionConflictException if the plan version has changed
   */
  public UpdatePlanResponse updatePlan(String planId, UpdatePlanRequest request) {
    Objects.requireNonNull(planId, "planId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    try {
      return retryExecutor.execute(
          () -> {
            Request httpRequest = buildRequest("PUT", "/api/v1/plan/" + planId, request);
            try (Response response = executeHttp(planHttpClient, httpRequest)) {
              return parseResponse(response, UpdatePlanResponse.class);
            }
          },
          "updatePlan");
    } catch (AxonFlowException e) {
      if (e.getStatusCode() == 409) {
        throw new VersionConflictException(e.getMessage(), planId, request.getVersion(), null);
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/plan/" + planId + "/versions", null);
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parseResponse(response, PlanVersionsResponse.class);
          }
        },
        "getPlanVersions");
  }

  /**
   * Resumes a paused plan, optionally approving or rejecting it.
   *
   * @param planId the ID of the plan to resume
   * @param approved whether to approve the plan to continue (true) or reject it (false)
   * @return the resume result
   */
  public ResumePlanResponse resumePlan(String planId, Boolean approved) {
    Objects.requireNonNull(planId, "planId cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = new java.util.HashMap<>();
          body.put("approved", approved != null ? approved : true);

          Request httpRequest = buildRequest("POST", "/api/v1/plan/" + planId + "/resume", body);
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parseResponse(response, ResumePlanResponse.class);
          }
        },
        "resumePlan");
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
   * @param planId the ID of the plan to roll back
   * @param targetVersion the version number to roll back to
   * @return the rollback result
   * @throws AxonFlowException if the rollback fails
   */
  public RollbackPlanResponse rollbackPlan(String planId, int targetVersion) {
    Objects.requireNonNull(planId, "planId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildRequest("POST", "/api/v1/plan/" + planId + "/rollback/" + targetVersion, null);
          try (Response response = executeHttp(planHttpClient, httpRequest)) {
            return parseResponse(response, RollbackPlanResponse.class);
          }
        },
        "rollbackPlan");
  }

  /**
   * Asynchronously rolls back a plan to a previous version.
   *
   * @param planId the ID of the plan to roll back
   * @param targetVersion the version number to roll back to
   * @return a future containing the rollback result
   */
  public CompletableFuture<RollbackPlanResponse> rollbackPlanAsync(
      String planId, int targetVersion) {
    return CompletableFuture.supplyAsync(() -> rollbackPlan(planId, targetVersion), asyncExecutor);
  }

  // ========================================================================
  // MCP Connectors
  // ========================================================================

  /**
   * Lists configured LLM providers from a SINGLE page of results.
   *
   * <p>Calls {@code GET /api/v1/llm-providers}. Mirrors the Python SDK's {@code list_providers()},
   * the Go SDK's {@code ListProviders()}, and the TypeScript SDK's {@code listProviders()}. For
   * multi-page traversal use {@link #listAllLLMProviders}; for pagination metadata use {@link
   * #listLLMProvidersPaged}.
   *
   * @return list of configured providers
   */
  public List<LLMProvider> listLLMProviders() {
    return listLLMProviders(null, null);
  }

  /**
   * Lists configured LLM providers, optionally filtered by type and/or enabled flag.
   *
   * @param type filter by provider type (e.g. {@code "openai"}, {@code "anthropic"}); null for no
   *     filter
   * @param enabled filter by the enabled boolean; null for no filter
   * @return list of matching providers
   */
  public List<LLMProvider> listLLMProviders(String type, Boolean enabled) {
    return listLLMProviders(type, enabled, null, null);
  }

  /**
   * Lists configured LLM providers with full filter + pagination control.
   *
   * @param type filter by provider type (null for no filter)
   * @param enabled filter by the enabled boolean (null for no filter)
   * @param page 1-indexed page number (null or non-positive for server default)
   * @param pageSize items per page, server cap 100 (null or non-positive for default)
   * @return providers from the requested page
   */
  public List<LLMProvider> listLLMProviders(
      String type, Boolean enabled, Integer page, Integer pageSize) {
    return listLLMProvidersPaged(type, enabled, page, pageSize).getProviders();
  }

  /**
   * Lists one page of providers along with pagination metadata so callers can paginate manually.
   */
  public LLMProviderListResponse listLLMProvidersPaged(
      String type, Boolean enabled, Integer page, Integer pageSize) {
    return retryExecutor.execute(
        () -> {
          HttpUrl base = HttpUrl.parse(config.getEndpoint() + "/api/v1/llm-providers");
          if (base == null) {
            throw new AxonFlowException("Invalid endpoint URL: " + config.getEndpoint());
          }
          HttpUrl.Builder b = base.newBuilder();
          if (type != null && !type.isEmpty()) {
            b.addQueryParameter("type", type);
          }
          if (enabled != null) {
            b.addQueryParameter("enabled", enabled.toString());
          }
          if (page != null && page > 0) {
            b.addQueryParameter("page", page.toString());
          }
          if (pageSize != null && pageSize > 0) {
            b.addQueryParameter("page_size", pageSize.toString());
          }
          HttpUrl url = b.build();

          Request.Builder reqBuilder = new Request.Builder().url(url).get();
          addAuthHeaders(reqBuilder);
          Request httpRequest = reqBuilder.build();
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);
            // The platform always wraps in {providers, pagination}, but tolerate
            // bare arrays from older builds via a synthetic single-page meta.
            if (node.isArray()) {
              List<LLMProvider> providers =
                  objectMapper.convertValue(node, new TypeReference<List<LLMProvider>>() {});
              PaginationMeta synthetic =
                  new PaginationMeta(1, providers.size(), providers.size(), 1);
              return new LLMProviderListResponse(providers, synthetic);
            }
            return objectMapper.convertValue(node, LLMProviderListResponse.class);
          }
        },
        "listLLMProvidersPaged");
  }

  /**
   * Walks every page of providers and returns the combined list.
   *
   * <p>Defaults to {@code pageSize=100} (the server-side max) to minimise round trips.
   *
   * @param type filter by provider type (null for no filter)
   * @param enabled filter by the enabled boolean (null for no filter)
   * @return all providers across every page
   */
  public List<LLMProvider> listAllLLMProviders(String type, Boolean enabled) {
    return listAllLLMProviders(type, enabled, 100);
  }

  /** As {@link #listAllLLMProviders(String, Boolean)} but with explicit page size. */
  public List<LLMProvider> listAllLLMProviders(String type, Boolean enabled, int pageSize) {
    java.util.ArrayList<LLMProvider> all = new java.util.ArrayList<>();
    int page = 1;
    while (true) {
      LLMProviderListResponse resp = listLLMProvidersPaged(type, enabled, page, pageSize);
      all.addAll(resp.getProviders());
      PaginationMeta meta = resp.getPagination();
      if (meta == null || meta.getTotalPages() <= page || resp.getProviders().isEmpty()) {
        break;
      }
      page += 1;
    }
    return all;
  }

  /**
   * Asynchronously lists configured LLM providers.
   *
   * @return a future containing the list of providers
   */
  public CompletableFuture<List<LLMProvider>> listLLMProvidersAsync() {
    return CompletableFuture.supplyAsync(this::listLLMProviders, asyncExecutor);
  }

  /**
   * Lists available MCP connectors.
   *
   * @return list of available connectors
   */
  public List<ConnectorInfo> listConnectors() {
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/connectors", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Response is wrapped: {"connectors": [...], "total": N}
            JsonNode node = parseResponseNode(response);
            if (node.has("connectors")) {
              return objectMapper.convertValue(
                  node.get("connectors"), new TypeReference<List<ConnectorInfo>>() {});
            }
            return objectMapper.convertValue(node, new TypeReference<List<ConnectorInfo>>() {});
          }
        },
        "listConnectors");
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
   * @param config the connector configuration
   * @return the installed connector info
   */
  public ConnectorInfo installConnector(String connectorId, Map<String, Object> config) {
    Objects.requireNonNull(connectorId, "connectorId cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = Map.of("config", config != null ? config : Map.of());
          String path = "/api/v1/connectors/" + connectorId + "/install";
          Request httpRequest = buildOrchestratorRequest("POST", path, body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ConnectorInfo.class);
          }
        },
        "installConnector");
  }

  /**
   * Uninstalls an MCP connector.
   *
   * @param connectorName the name of the connector to uninstall
   */
  public void uninstallConnector(String connectorName) {
    Objects.requireNonNull(connectorName, "connectorName cannot be null");

    retryExecutor.execute(
        () -> {
          String path = "/api/v1/connectors/" + connectorName;
          Request httpRequest = buildOrchestratorRequest("DELETE", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "uninstallConnector");
  }

  /**
   * Gets details for a specific connector by ID.
   *
   * @param connectorId the connector ID
   * @return the connector info
   */
  public ConnectorInfo getConnector(String connectorId) {
    Objects.requireNonNull(connectorId, "connectorId cannot be null");

    return retryExecutor.execute(
        () -> {
          String path = "/api/v1/connectors/" + connectorId;
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ConnectorInfo.class);
          }
        },
        "getConnector");
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

    return retryExecutor.execute(
        () -> {
          String path = "/api/v1/connectors/" + connectorId + "/health";
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ConnectorHealthStatus.class);
          }
        },
        "getConnectorHealth");
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
   * <p>This method sends the query to the AxonFlow Agent using the standard request format with
   * request_type: "mcp-query", which is routed to the configured MCP connector.
   *
   * @param query the connector query
   * @return the query response
   * @throws ConnectorException if the query fails
   */
  public ConnectorResponse queryConnector(ConnectorQuery query) {
    Objects.requireNonNull(query, "query cannot be null");

    return retryExecutor.execute(
        () -> {
          // Build a ClientRequest with MCP_QUERY request type
          // This follows the same pattern as Go and TypeScript SDKs
          Map<String, Object> context = new HashMap<>();
          context.put("connector", query.getConnectorId());
          if (query.getParameters() != null && !query.getParameters().isEmpty()) {
            context.put("params", query.getParameters());
          }

          String clientId = config.getClientId();

          ClientRequest clientRequest =
              ClientRequest.builder()
                  .query(query.getOperation())
                  .userToken(query.getUserToken() != null ? query.getUserToken() : clientId)
                  .clientId(clientId)
                  .requestType(RequestType.MCP_QUERY)
                  .context(context)
                  .build();

          Request httpRequest = buildRequest("POST", "/api/request", clientRequest);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            ClientResponse clientResponse = parseResponse(response, ClientResponse.class);

            // Convert ClientResponse to ConnectorResponse
            ConnectorResponse result =
                new ConnectorResponse(
                    clientResponse.isSuccess(),
                    clientResponse.getData(),
                    clientResponse.getError(),
                    query.getConnectorId(),
                    query.getOperation(),
                    null, // processingTime not available from ClientResponse
                    false, // redacted - not available from this endpoint
                    null, // redactedFields - not available from this endpoint
                    null // policyInfo - not available from this endpoint
                    );

            if (!result.isSuccess()) {
              throw new ConnectorException(
                  result.getError(), query.getConnectorId(), query.getOperation());
            }

            return result;
          }
        },
        "queryConnector");
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
   *
   * <ul>
   *   <li>Request-phase policy evaluation (SQLi blocking, PII blocking)
   *   <li>Response-phase policy evaluation (PII redaction)
   *   <li>PolicyInfo metadata in responses
   * </ul>
   *
   * <p>Example usage:
   *
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
  public ConnectorResponse mcpQuery(
      String connector, String statement, Map<String, Object> options) {
    Objects.requireNonNull(connector, "connector cannot be null");
    Objects.requireNonNull(statement, "statement cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = new HashMap<>();
          body.put("connector", connector);
          body.put("statement", statement);
          if (options != null && !options.isEmpty()) {
            body.put("options", options);
          }

          Request httpRequest = buildRequest("POST", "/mcp/resources/query", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Parse the response body
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
              throw new ConnectorException("Empty response from MCP query", connector, "mcpQuery");
            }
            String responseJson = responseBody.string();

            // Handle policy blocks (403 responses)
            if (!response.isSuccessful()) {
              try {
                Map<String, Object> errorData =
                    objectMapper.readValue(
                        responseJson,
                        new com.fasterxml.jackson.core.type.TypeReference<
                            Map<String, Object>>() {});
                String errorMsg =
                    errorData.get("error") != null
                        ? errorData.get("error").toString()
                        : "MCP query failed: " + response.code();
                throw new ConnectorException(errorMsg, connector, "mcpQuery");
              } catch (JsonProcessingException e) {
                throw new ConnectorException(
                    "MCP query failed: " + response.code(), connector, "mcpQuery");
              }
            }

            return objectMapper.readValue(responseJson, ConnectorResponse.class);
          }
        },
        "mcpQuery");
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
  public CompletableFuture<ConnectorResponse> mcpQueryAsync(
      String connector, String statement, Map<String, Object> options) {
    return CompletableFuture.supplyAsync(
        () -> mcpQuery(connector, statement, options), asyncExecutor);
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
   * <p>This method calls the agent's {@code /api/v1/mcp/check-input} endpoint to pre-validate a
   * statement before sending it to the connector. Useful for checking SQL injection patterns,
   * blocked operations, and input policy violations.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * MCPCheckInputResponse result = axonflow.mcpCheckInput("postgres", "SELECT * FROM users");
   * if (!result.isAllowed()) {
   *     System.out.println("Blocked: " + result.getBlockReason());
   * }
   * }</pre>
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
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
   * @param statement the statement to validate
   * @param options optional parameters: "operation" (String), "parameters" (Map), "tool" (String)
   * @return MCPCheckInputResponse with allowed status, block reason, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckInputResponse mcpCheckInput(
      String connectorType, String statement, Map<String, Object> options) {
    Objects.requireNonNull(connectorType, "connectorType cannot be null");
    Objects.requireNonNull(statement, "statement cannot be null");

    return retryExecutor.execute(
        () -> {
          MCPCheckInputRequest request;
          if (options != null) {
            String operation = (String) options.getOrDefault("operation", "execute");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) options.get("parameters");
            // content_type selects the request-redaction detector (ADR-056 / #2563); null
            // defaults to text/plain server-side.
            String contentType = (String) options.get("content_type");
            // tool identifies the specific tool/action invoked on the MCP server, distinct
            // from connectorType which identifies the server/connector itself (epic #2905 /
            // #2904).
            String tool = (String) options.get("tool");
            request =
                new MCPCheckInputRequest(
                    connectorType, statement, parameters, operation, contentType, tool);
          } else {
            request = new MCPCheckInputRequest(connectorType, statement);
          }

          Request httpRequest = buildRequest("POST", "/api/v1/mcp/check-input", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
              throw new ConnectorException(
                  "Empty response from MCP check-input", connectorType, "mcpCheckInput");
            }
            String responseJson = responseBody.string();

            // 403 means policy blocked — the body is still a valid response
            if (!response.isSuccessful() && response.code() != 403) {
              try {
                Map<String, Object> errorData =
                    objectMapper.readValue(
                        responseJson, new TypeReference<Map<String, Object>>() {});
                String errorMsg =
                    errorData.get("error") != null
                        ? errorData.get("error").toString()
                        : "MCP check-input failed: " + response.code();
                throw new ConnectorException(errorMsg, connectorType, "mcpCheckInput");
              } catch (JsonProcessingException e) {
                throw new ConnectorException(
                    "MCP check-input failed: " + response.code(), connectorType, "mcpCheckInput");
              }
            }

            return objectMapper.readValue(responseJson, MCPCheckInputResponse.class);
          }
        },
        "mcpCheckInput");
  }

  /**
   * Asynchronously validates an MCP input statement against configured policies.
   *
   * @param connectorType name of the MCP connector type
   * @param statement the statement to validate
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckInputResponse> mcpCheckInputAsync(
      String connectorType, String statement) {
    return CompletableFuture.supplyAsync(
        () -> mcpCheckInput(connectorType, statement), asyncExecutor);
  }

  /**
   * Asynchronously validates an MCP input statement against configured policies with options.
   *
   * @param connectorType name of the MCP connector type
   * @param statement the statement to validate
   * @param options optional parameters
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckInputResponse> mcpCheckInputAsync(
      String connectorType, String statement, Map<String, Object> options) {
    return CompletableFuture.supplyAsync(
        () -> mcpCheckInput(connectorType, statement, options), asyncExecutor);
  }

  // ========================================================================
  // Decision Mode PEP — decide -> fulfill -> forward (ADR-056, epic #2563)
  // ========================================================================

  /**
   * Asks the PDP for a verdict on a request ({@code POST /api/v1/decide}).
   *
   * <p>This is the PDP step of a PEP. {@code /decide} is a pure decision point: it NEVER mutates
   * content. When an allow verdict carries a {@code redact_pii} obligation, discharge it with
   * {@link #fulfillRequest(DecideResponse, String)} (or use the one-call {@link
   * #decideAndFulfill(DecideRequest)}) — never by redacting locally.
   *
   * <p>Decision Mode auth is HTTP Basic (org:license), which this client already sends; demo /
   * wrong credentials are refused with HTTP 401 → {@link
   * com.getaxonflow.sdk.exceptions.AuthenticationException}. A deny verdict is returned in the body
   * with HTTP 200, not as an error.
   *
   * @param request the {@link DecideRequest} ({@code stage} ∈ {@code {"llm","tool","agent"}} and
   *     {@code query} are required)
   * @return the {@link DecideResponse} verdict, with {@code obligations} always a (possibly empty)
   *     list
   * @throws com.getaxonflow.sdk.exceptions.AuthenticationException on HTTP 401 (bad / demo
   *     credentials)
   * @throws AxonFlowException on other non-200 responses
   */
  public DecideResponse decide(DecideRequest request) {
    Objects.requireNonNull(request, "request cannot be null");
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("POST", Pep.DECIDE_PATH, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, DecideResponse.class);
          }
        },
        "decide");
  }

  /**
   * Asynchronously asks the PDP for a verdict on a request.
   *
   * @param request the {@link DecideRequest}
   * @return a future containing the {@link DecideResponse}
   */
  public CompletableFuture<DecideResponse> decideAsync(DecideRequest request) {
    return CompletableFuture.supplyAsync(() -> decide(request), asyncExecutor);
  }

  /**
   * Discharges every request-phase {@code redact_pii} obligation on {@code decision} by calling the
   * engine endpoint each obligation names, returning the engine-redacted statement to forward.
   *
   * <p>There is NO code path in which this method redacts locally — fulfillment is always the
   * engine round-trip (ADR-056 / #2563). It NEVER returns the original statement under an
   * unfulfillable condition; it throws {@link
   * com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException} so the caller fails closed.
   *
   * @param decision the verdict whose obligations to discharge (null is treated as no obligations)
   * @param statement the request content to redact
   * @return a {@link FulfillResult} with the (possibly engine-redacted) content and whether the
   *     engine actually changed it
   * @throws com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException when a {@code
   *     redact_pii} obligation named no request-phase fulfillment, advertised a content-type the
   *     PEP is not holding, named an endpoint this client will not call, the engine call failed, or
   *     the engine reported the redactor did not run ({@code redaction_evaluated=false}). The
   *     caller MUST fail closed (block) — never forward the original {@code statement}.
   */
  public FulfillResult fulfillRequest(DecideResponse decision, String statement) {
    if (decision == null) {
      return new FulfillResult(statement, false);
    }
    String redacted = statement;
    boolean didRedact = false;
    for (Obligation ob : decision.getObligations()) {
      if (ob == null || !Pep.OBLIGATION_REDACT_PII.equals(ob.getType())) {
        // redact_pii is the only content-mutating obligation today; other types are
        // pass-through by contract.
        continue;
      }
      ObligationFulfillment f = ob.getFulfillment();
      if (f == null || !Pep.PHASE_REQUEST.equals(f.getPhase())) {
        throw new ObligationNotFulfillableException(
            "redact_pii obligation missing request-phase fulfillment");
      }
      List<String> contentTypes = f.getContentTypes();
      if (contentTypes != null
          && !contentTypes.isEmpty()
          && !contentTypes.contains(Pep.CONTENT_TYPE_TEXT)) {
        throw new ObligationNotFulfillableException(
            "fulfillment endpoint does not advertise a " + Pep.CONTENT_TYPE_TEXT + " detector");
      }
      if (!Pep.endpointPathMatches(f.getEndpoint(), Pep.REQUEST_REDACTION_PATH)) {
        throw new ObligationNotFulfillableException(
            "fulfillment endpoint '" + f.getEndpoint() + "' is not the request-redaction endpoint");
      }
      redacted = fulfillViaCheckInput(redacted);
      // didRedact reflects whether the ENGINE changed the content, not merely that an
      // obligation was present.
      if (!redacted.equals(statement)) {
        didRedact = true;
      }
    }
    return new FulfillResult(redacted, didRedact);
  }

  /**
   * POSTs {@code statement} to the request-redaction engine endpoint and returns the engine-masked
   * statement. Fails closed (throws {@link
   * com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException}) when the engine call errors,
   * the engine returns a non-200, or {@code redaction_evaluated} is false — never returns
   * unredacted content under an unfulfillable condition.
   */
  private String fulfillViaCheckInput(String statement) {
    MCPCheckInputResponse result;
    try {
      Map<String, Object> options = new HashMap<>();
      options.put("operation", "execute");
      options.put("content_type", Pep.CONTENT_TYPE_TEXT);
      result = mcpCheckInput("gateway", statement, options);
    } catch (AxonFlowException e) {
      throw new ObligationNotFulfillableException(
          "request-redaction engine call failed: " + e.getMessage(), e);
    }
    // FAIL CLOSED if the redactor did not actually run (#2563 B1). Without this the PEP cannot
    // distinguish "engine looked, found nothing" (safe to forward) from "engine wasn't looking"
    // (would leak PII).
    if (!result.isRedactionEvaluated()) {
      throw new ObligationNotFulfillableException(
          "engine reported the redactor did not run (redaction disabled)");
    }
    if (result.isRedacted()) {
      // FAIL CLOSED on a self-contradictory engine response: redacted=true with
      // no (or empty) redacted_statement means the engine claims it masked
      // something but gave us nothing to forward — never fall back to the
      // unredacted original.
      if (result.getRedactedStatement() == null || result.getRedactedStatement().isEmpty()) {
        throw new ObligationNotFulfillableException(
            "engine reported redacted=true but returned no redacted_statement");
      }
      return result.getRedactedStatement();
    }
    // Redactor ran and found nothing to mask — forward unchanged.
    return statement;
  }

  /**
   * One-call PEP path: decide, then fulfill any request-phase obligation (ADR-056 / #2563).
   *
   * <p>Returns a {@link DecideAndFulfillResult} carrying the verdict, the content to forward
   * (engine-redacted when an obligation applied), and the raw decision. Branch on the verdict:
   * forward {@link DecideAndFulfillResult#getContent()} on {@code allow}; block on {@code deny} /
   * {@code needs_approval}.
   *
   * <p>On the not-fulfillable path this throws {@link
   * com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException} — a caller that catches it
   * has NO content to accidentally forward, so the path is fail-closed by construction.
   *
   * @param request the {@link DecideRequest}
   * @return the verdict, content, and decision
   * @throws com.getaxonflow.sdk.exceptions.AuthenticationException on HTTP 401
   * @throws com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException when an allow verdict
   *     carries an unfulfillable {@code redact_pii} obligation
   */
  public DecideAndFulfillResult decideAndFulfill(DecideRequest request) {
    DecideResponse decision = decide(request);
    if (!Pep.VERDICT_ALLOW.equals(decision.getVerdict())) {
      return new DecideAndFulfillResult(decision.getVerdict(), request.getQuery(), decision);
    }
    FulfillResult fulfilled = fulfillRequest(decision, request.getQuery());
    return new DecideAndFulfillResult(decision.getVerdict(), fulfilled.getContent(), decision);
  }

  /**
   * Asynchronously runs the one-call PEP path.
   *
   * @param request the {@link DecideRequest}
   * @return a future containing the {@link DecideAndFulfillResult}
   */
  public CompletableFuture<DecideAndFulfillResult> decideAndFulfillAsync(DecideRequest request) {
    return CompletableFuture.supplyAsync(() -> decideAndFulfill(request), asyncExecutor);
  }

  /**
   * Result of {@link #fulfillRequest(DecideResponse, String)}: the content to forward and whether
   * the engine actually changed it.
   */
  public static final class FulfillResult {
    private final String content;
    private final boolean didRedact;

    FulfillResult(String content, boolean didRedact) {
      this.content = content;
      this.didRedact = didRedact;
    }

    /** Returns the content to forward (engine-redacted when an obligation mutated the request). */
    public String getContent() {
      return content;
    }

    /** Returns whether the engine actually changed the content. */
    public boolean didRedact() {
      return didRedact;
    }
  }

  /**
   * Result of {@link #decideAndFulfill(DecideRequest)}: verdict, content to forward, and decision.
   */
  public static final class DecideAndFulfillResult {
    private final String verdict;
    private final String content;
    private final DecideResponse decision;

    DecideAndFulfillResult(String verdict, String content, DecideResponse decision) {
      this.verdict = verdict;
      this.content = content;
      this.decision = decision;
    }

    /** Returns the verdict: {@code allow}, {@code deny}, or {@code needs_approval}. */
    public String getVerdict() {
      return verdict;
    }

    /**
     * Returns the content to forward on {@code allow} (engine-redacted when an obligation applied),
     * or the original query on a non-allow verdict.
     */
    public String getContent() {
      return content;
    }

    /** Returns the raw PDP decision. */
    public DecideResponse getDecision() {
      return decision;
    }
  }

  /**
   * Validates MCP response data against configured policies.
   *
   * <p>This method calls the agent's {@code /api/v1/mcp/check-output} endpoint to check response
   * data for PII content, exfiltration limit violations, and other output policy violations. If PII
   * redaction is active, {@code redactedData} contains the sanitized version.
   *
   * <p>Example usage:
   *
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
   * @param responseData the response data rows to validate
   * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckOutputResponse mcpCheckOutput(
      String connectorType, List<Map<String, Object>> responseData) {
    return mcpCheckOutput(connectorType, responseData, null);
  }

  /**
   * Validates MCP response data against configured policies with options.
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   * @param options optional parameters: "message" (String), "metadata" (Map), "row_count" (int),
   *     "tool" (String)
   * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckOutputResponse mcpCheckOutput(
      String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
    Objects.requireNonNull(connectorType, "connectorType cannot be null");
    // responseData can be null for execute-style requests that use message instead

    return retryExecutor.execute(
        () -> {
          String message = options != null ? (String) options.get("message") : null;
          @SuppressWarnings("unchecked")
          Map<String, Object> metadata =
              options != null ? (Map<String, Object>) options.get("metadata") : null;
          int rowCount = options != null ? (int) options.getOrDefault("row_count", 0) : 0;
          // tool identifies the specific tool/action invoked on the MCP server, distinct
          // from connectorType which identifies the server/connector itself (epic #2905 /
          // #2904).
          String tool = options != null ? (String) options.get("tool") : null;

          MCPCheckOutputRequest request =
              new MCPCheckOutputRequest(
                  connectorType, responseData, message, metadata, rowCount, tool);

          Request httpRequest = buildRequest("POST", "/api/v1/mcp/check-output", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
              throw new ConnectorException(
                  "Empty response from MCP check-output", connectorType, "mcpCheckOutput");
            }
            String responseJson = responseBody.string();

            // 403 means policy blocked — the body is still a valid response
            if (!response.isSuccessful() && response.code() != 403) {
              try {
                Map<String, Object> errorData =
                    objectMapper.readValue(
                        responseJson, new TypeReference<Map<String, Object>>() {});
                String errorMsg =
                    errorData.get("error") != null
                        ? errorData.get("error").toString()
                        : "MCP check-output failed: " + response.code();
                throw new ConnectorException(errorMsg, connectorType, "mcpCheckOutput");
              } catch (JsonProcessingException e) {
                throw new ConnectorException(
                    "MCP check-output failed: " + response.code(), connectorType, "mcpCheckOutput");
              }
            }

            return objectMapper.readValue(responseJson, MCPCheckOutputResponse.class);
          }
        },
        "mcpCheckOutput");
  }

  /**
   * Asynchronously validates MCP response data against configured policies.
   *
   * @param connectorType name of the MCP connector type
   * @param responseData the response data rows to validate
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckOutputResponse> mcpCheckOutputAsync(
      String connectorType, List<Map<String, Object>> responseData) {
    return CompletableFuture.supplyAsync(
        () -> mcpCheckOutput(connectorType, responseData), asyncExecutor);
  }

  /**
   * Asynchronously validates MCP response data against configured policies with options.
   *
   * @param connectorType name of the MCP connector type
   * @param responseData the response data rows to validate
   * @param options optional parameters
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckOutputResponse> mcpCheckOutputAsync(
      String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
    return CompletableFuture.supplyAsync(
        () -> mcpCheckOutput(connectorType, responseData, options), asyncExecutor);
  }

  // ========================================================================
  // Tool Input/Output Check Aliases
  // ========================================================================

  /**
   * Alias for {@link #mcpCheckInput(String, String)}. Validates tool input against configured
   * policies.
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
   * @return MCPCheckInputResponse with allowed status, block reason, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckInputResponse checkToolInput(String connectorType, String statement) {
    return mcpCheckInput(connectorType, statement);
  }

  /**
   * Alias for {@link #mcpCheckInput(String, String, Map)}. Validates tool input against configured
   * policies.
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
   * @param options optional parameters: "operation" (String), "parameters" (Map)
   * @return MCPCheckInputResponse with allowed status, block reason, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckInputResponse checkToolInput(
      String connectorType, String statement, Map<String, Object> options) {
    return mcpCheckInput(connectorType, statement, options);
  }

  /**
   * Asynchronous alias for {@link #mcpCheckInputAsync(String, String)}.
   *
   * @param connectorType name of the MCP connector type
   * @param statement the statement to validate
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckInputResponse> checkToolInputAsync(
      String connectorType, String statement) {
    return mcpCheckInputAsync(connectorType, statement);
  }

  /**
   * Asynchronous alias for {@link #mcpCheckInputAsync(String, String, Map)}.
   *
   * @param connectorType name of the MCP connector type
   * @param statement the statement to validate
   * @param options optional parameters
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckInputResponse> checkToolInputAsync(
      String connectorType, String statement, Map<String, Object> options) {
    return mcpCheckInputAsync(connectorType, statement, options);
  }

  /**
   * Alias for {@link #mcpCheckOutput(String, List)}. Validates tool output against configured
   * policies.
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckOutputResponse checkToolOutput(
      String connectorType, List<Map<String, Object>> responseData) {
    return mcpCheckOutput(connectorType, responseData);
  }

  /**
   * Alias for {@link #mcpCheckOutput(String, List, Map)}. Validates tool output against configured
   * policies.
   *
   * @param connectorType name of the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   * @param options optional parameters: "message" (String), "metadata" (Map), "row_count" (int)
   * @return MCPCheckOutputResponse with allowed status, redacted data, and policy info
   * @throws ConnectorException if the request fails (note: 403 is not an error, it means blocked)
   */
  public MCPCheckOutputResponse checkToolOutput(
      String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
    return mcpCheckOutput(connectorType, responseData, options);
  }

  /**
   * Asynchronous alias for {@link #mcpCheckOutputAsync(String, List)}.
   *
   * @param connectorType name of the MCP connector type
   * @param responseData the response data rows to validate
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckOutputResponse> checkToolOutputAsync(
      String connectorType, List<Map<String, Object>> responseData) {
    return mcpCheckOutputAsync(connectorType, responseData);
  }

  /**
   * Asynchronous alias for {@link #mcpCheckOutputAsync(String, List, Map)}.
   *
   * @param connectorType name of the MCP connector type
   * @param responseData the response data rows to validate
   * @param options optional parameters
   * @return a future containing the check result
   */
  public CompletableFuture<MCPCheckOutputResponse> checkToolOutputAsync(
      String connectorType, List<Map<String, Object>> responseData, Map<String, Object> options) {
    return mcpCheckOutputAsync(connectorType, responseData, options);
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
    return retryExecutor.execute(
        () -> {
          String path = buildPolicyQueryString("/api/v1/static-policies", options);
          Request httpRequest = buildRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            StaticPoliciesResponse wrapper = parseResponse(response, StaticPoliciesResponse.class);
            // Handle null wrapper or null policies list (Issue #40)
            if (wrapper == null || wrapper.getPolicies() == null) {
              return java.util.Collections.emptyList();
            }
            return wrapper.getPolicies();
          }
        },
        "listStaticPolicies");
  }

  /**
   * Lists static policies filtered by tier and organization ID (Enterprise).
   *
   * @param tier the policy tier
   * @param organizationId the organization ID
   * @return list of static policies
   */
  public List<StaticPolicy> listStaticPolicies(PolicyTier tier, String organizationId) {
    return listStaticPolicies(
        ListStaticPoliciesOptions.builder().tier(tier).organizationId(organizationId).build());
  }

  /**
   * Lists static policies filtered by tier and category.
   *
   * @param tier the policy tier
   * @param category the policy category
   * @return list of static policies
   */
  public List<StaticPolicy> listStaticPolicies(PolicyTier tier, PolicyCategory category) {
    return listStaticPolicies(
        ListStaticPoliciesOptions.builder().tier(tier).category(category).build());
  }

  /**
   * Lists static policies filtered by category.
   *
   * @param category the policy category
   * @return list of static policies
   */
  public List<StaticPolicy> listStaticPolicies(PolicyCategory category) {
    return listStaticPolicies(ListStaticPoliciesOptions.builder().category(category).build());
  }

  /**
   * Gets a specific static policy by ID.
   *
   * @param policyId the policy ID
   * @return the static policy
   */
  public StaticPolicy getStaticPolicy(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/static-policies/" + policyId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, StaticPolicy.class);
          }
        },
        "getStaticPolicy");
  }

  /**
   * Creates a new static policy.
   *
   * @param request the create request
   * @return the created policy
   */
  public StaticPolicy createStaticPolicy(CreateStaticPolicyRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("POST", "/api/v1/static-policies", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, StaticPolicy.class);
          }
        },
        "createStaticPolicy");
  }

  /**
   * Updates an existing static policy.
   *
   * @param policyId the policy ID
   * @param request the update request
   * @return the updated policy
   */
  public StaticPolicy updateStaticPolicy(String policyId, UpdateStaticPolicyRequest request) {
    Objects.requireNonNull(policyId, "policyId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("PUT", "/api/v1/static-policies/" + policyId, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, StaticPolicy.class);
          }
        },
        "updateStaticPolicy");
  }

  /**
   * Deletes a static policy.
   *
   * @param policyId the policy ID
   */
  public void deleteStaticPolicy(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("DELETE", "/api/v1/static-policies/" + policyId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deleteStaticPolicy");
  }

  /**
   * Toggles a static policy's enabled status.
   *
   * @param policyId the policy ID
   * @param enabled the new enabled status
   * @return the updated policy
   */
  public StaticPolicy toggleStaticPolicy(String policyId, boolean enabled) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = Map.of("enabled", enabled);
          Request httpRequest = buildPatchRequest("/api/v1/static-policies/" + policyId, body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, StaticPolicy.class);
          }
        },
        "toggleStaticPolicy");
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
    return getEffectiveStaticPolicies(
        EffectivePoliciesOptions.builder().category(category).build());
  }

  /**
   * Gets effective static policies with options.
   *
   * @param options filtering options
   * @return list of effective policies
   */
  public List<StaticPolicy> getEffectiveStaticPolicies(EffectivePoliciesOptions options) {
    return retryExecutor.execute(
        () -> {
          StringBuilder path = new StringBuilder("/api/v1/static-policies/effective");
          if (options != null) {
            String query = buildEffectivePoliciesQuery(options);
            if (!query.isEmpty()) {
              path.append("?").append(query);
            }
          }
          Request httpRequest = buildRequest("GET", path.toString(), null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            EffectivePoliciesResponse wrapper =
                parseResponse(response, EffectivePoliciesResponse.class);
            // Handle null wrapper or null policies list (Issue #40)
            if (wrapper == null || wrapper.getStaticPolicies() == null) {
              return java.util.Collections.emptyList();
            }
            return wrapper.getStaticPolicies();
          }
        },
        "getEffectiveStaticPolicies");
  }

  /**
   * Tests a regex pattern against sample inputs.
   *
   * @param pattern the regex pattern
   * @param testInputs sample inputs to test
   * @return the test result
   */
  public TestPatternResult testPattern(String pattern, List<String> testInputs) {
    Objects.requireNonNull(pattern, "pattern cannot be null");
    Objects.requireNonNull(testInputs, "testInputs cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body =
              Map.of(
                  "pattern", pattern,
                  "inputs", testInputs);
          Request httpRequest = buildRequest("POST", "/api/v1/static-policies/test", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, TestPatternResult.class);
          }
        },
        "testPattern");
  }

  /**
   * Gets version history for a static policy.
   *
   * @param policyId the policy ID
   * @return list of policy versions
   */
  public List<PolicyVersion> getStaticPolicyVersions(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildRequest("GET", "/api/v1/static-policies/" + policyId + "/versions", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            Map<String, Object> wrapper =
                parseResponse(response, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> versionsRaw =
                (List<Map<String, Object>>) wrapper.get("versions");
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
        },
        "getStaticPolicyVersions");
  }

  // ========================================================================
  // Policy CRUD - Overrides (Enterprise)
  // ========================================================================

  /**
   * Creates a policy override.
   *
   * @param policyId the policy ID
   * @param request the override request
   * @return the created override
   */
  public PolicyOverride createPolicyOverride(String policyId, CreatePolicyOverrideRequest request) {
    Objects.requireNonNull(policyId, "policyId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildRequest("POST", "/api/v1/static-policies/" + policyId + "/override", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, PolicyOverride.class);
          }
        },
        "createPolicyOverride");
  }

  /**
   * Deletes a policy override.
   *
   * @param policyId the policy ID
   */
  public void deletePolicyOverride(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildRequest("DELETE", "/api/v1/static-policies/" + policyId + "/override", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deletePolicyOverride");
  }

  /**
   * Lists all active policy overrides (Enterprise).
   *
   * @return list of policy overrides
   */
  public List<PolicyOverride> listPolicyOverrides() {
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/static-policies/overrides", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Backend returns wrapped response: {"overrides": [...], "count": N}
            Map<String, Object> wrapper =
                parseResponse(response, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overridesRaw =
                (List<Map<String, Object>>) wrapper.get("overrides");
            if (overridesRaw == null) {
              return java.util.Collections.emptyList();
            }
            return overridesRaw.stream()
                .map(raw -> objectMapper.convertValue(raw, PolicyOverride.class))
                .collect(java.util.stream.Collectors.toList());
          }
        },
        "listPolicyOverrides");
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
    return retryExecutor.execute(
        () -> {
          String path = buildDynamicPolicyQueryString("/api/v1/dynamic-policies", options);
          Request httpRequest = buildOrchestratorRequest("GET", path, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policies": [...]} wrapper
            DynamicPoliciesResponse wrapper =
                parseResponse(response, DynamicPoliciesResponse.class);
            // Handle null wrapper or null policies list (Issue #40)
            if (wrapper == null || wrapper.getPolicies() == null) {
              return java.util.Collections.emptyList();
            }
            return wrapper.getPolicies();
          }
        },
        "listDynamicPolicies");
  }

  /**
   * Gets a specific dynamic policy by ID.
   *
   * @param policyId the policy ID
   * @return the dynamic policy
   */
  public DynamicPolicy getDynamicPolicy(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/dynamic-policies/" + policyId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
            DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
            return wrapper != null ? wrapper.getPolicy() : null;
          }
        },
        "getDynamicPolicy");
  }

  /**
   * Creates a new dynamic policy.
   *
   * @param request the create request
   * @return the created policy
   */
  public DynamicPolicy createDynamicPolicy(CreateDynamicPolicyRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/dynamic-policies", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
            DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
            return wrapper != null ? wrapper.getPolicy() : null;
          }
        },
        "createDynamicPolicy");
  }

  /**
   * Updates an existing dynamic policy.
   *
   * @param policyId the policy ID
   * @param request the update request
   * @return the updated policy
   */
  public DynamicPolicy updateDynamicPolicy(String policyId, UpdateDynamicPolicyRequest request) {
    Objects.requireNonNull(policyId, "policyId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("PUT", "/api/v1/dynamic-policies/" + policyId, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
            DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
            return wrapper != null ? wrapper.getPolicy() : null;
          }
        },
        "updateDynamicPolicy");
  }

  /**
   * Deletes a dynamic policy.
   *
   * @param policyId the policy ID
   */
  public void deleteDynamicPolicy(String policyId) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("DELETE", "/api/v1/dynamic-policies/" + policyId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deleteDynamicPolicy");
  }

  /**
   * Toggles a dynamic policy's enabled status.
   *
   * @param policyId the policy ID
   * @param enabled the new enabled status
   * @return the updated policy
   */
  public DynamicPolicy toggleDynamicPolicy(String policyId, boolean enabled) {
    Objects.requireNonNull(policyId, "policyId cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = Map.of("enabled", enabled);
          Request httpRequest =
              buildOrchestratorRequest("PUT", "/api/v1/dynamic-policies/" + policyId, body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policy": {...}} wrapper
            DynamicPolicyResponse wrapper = parseResponse(response, DynamicPolicyResponse.class);
            return wrapper != null ? wrapper.getPolicy() : null;
          }
        },
        "toggleDynamicPolicy");
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
    return retryExecutor.execute(
        () -> {
          StringBuilder path = new StringBuilder("/api/v1/dynamic-policies/effective");
          if (options != null) {
            String query = buildEffectivePoliciesQuery(options);
            if (!query.isEmpty()) {
              path.append("?").append(query);
            }
          }
          Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            // Agent proxy (Issue #886) returns {"policies": [...]} wrapper
            DynamicPoliciesResponse wrapper =
                parseResponse(response, DynamicPoliciesResponse.class);
            // Handle null wrapper or null policies list (Issue #40)
            if (wrapper == null || wrapper.getPolicies() == null) {
              return java.util.Collections.emptyList();
            }
            return wrapper.getPolicies();
          }
        },
        "getEffectiveDynamicPolicies");
  }

  // ========================================================================
  // Unified Execution Tracking (Issue #1075 - EPIC #1074)
  // ========================================================================

  /**
   * Gets the unified execution status for a given execution ID.
   *
   * <p>This method works for both MAP plans and WCP workflows, returning a consistent status format
   * regardless of execution type.
   *
   * @param executionId the execution ID (plan ID or workflow ID)
   * @return the unified execution status
   */
  public com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus getExecutionStatus(
      String executionId) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/unified/executions/" + executionId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response, com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus.class);
          }
        },
        "getExecutionStatus");
  }

  /**
   * Lists unified executions with optional filtering.
   *
   * @param request filter options
   * @return paginated list of executions
   */
  public com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsResponse
      listUnifiedExecutions(
          com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsRequest request) {

    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                com.getaxonflow.sdk.types.execution.ExecutionTypes.UnifiedListExecutionsResponse
                    .class);
          }
        },
        "listUnifiedExecutions");
  }

  /**
   * Cancels a unified execution (MAP plan or WCP workflow).
   *
   * <p>This method cancels an execution via the unified execution API, automatically propagating to
   * the correct subsystem (MAP or WCP).
   *
   * @param executionId the execution ID (plan ID or workflow ID)
   * @param reason optional reason for cancellation
   */
  public void cancelExecution(String executionId, String reason) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    retryExecutor.execute(
        () -> {
          Map<String, String> body =
              reason != null ? Collections.singletonMap("reason", reason) : Collections.emptyMap();
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/unified/executions/" + executionId + "/cancel", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "cancelExecution");
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
   * <p>Connects to the SSE streaming endpoint and invokes the callback with each {@link
   * com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus} update as it arrives. The
   * stream automatically closes when the execution reaches a terminal state (completed, failed,
   * cancelled, aborted, or expired).
   *
   * <p>Example usage:
   *
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

    HttpUrl url =
        HttpUrl.parse(
            config.getEndpoint() + "/api/v1/unified/executions/" + executionId + "/stream");
    if (url == null) {
      throw new ConfigurationException(
          "Invalid URL: "
              + config.getEndpoint()
              + "/api/v1/unified/executions/"
              + executionId
              + "/stream");
    }

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("X-Axonflow-Client", config.getClientHeader())
            .header("Accept", "text/event-stream")
            .get();

    addAuthHeaders(builder);

    Request httpRequest = builder.build();

    try {
      Response response = executeHttp(httpClient, httpRequest);
      try {
        if (!response.isSuccessful()) {
          handleErrorResponse(response);
        }

        ResponseBody body = response.body();
        if (body == null) {
          throw new AxonFlowException("SSE response has no body", 0, null);
        }

        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
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
                        objectMapper.readValue(
                            jsonStr,
                            com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus
                                .class);
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
   * <p>Returns per-tenant settings controlling whether media analysis is enabled and which
   * analyzers are allowed.
   *
   * @return the media governance configuration
   * @throws AxonFlowException if the request fails
   */
  public MediaGovernanceConfig getMediaGovernanceConfig() {
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/media-governance/config", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, MediaGovernanceConfig.class);
          }
        },
        "getMediaGovernanceConfig");
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
   * <p>Allows enabling/disabling media analysis and controlling which analyzers are permitted.
   *
   * @param request the update request
   * @return the updated media governance configuration
   * @throws AxonFlowException if the request fails
   */
  public MediaGovernanceConfig updateMediaGovernanceConfig(
      UpdateMediaGovernanceConfigRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("PUT", "/api/v1/media-governance/config", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, MediaGovernanceConfig.class);
          }
        },
        "updateMediaGovernanceConfig");
  }

  /**
   * Asynchronously updates the media governance configuration for the current tenant.
   *
   * @param request the update request
   * @return a future containing the updated media governance configuration
   */
  public CompletableFuture<MediaGovernanceConfig> updateMediaGovernanceConfigAsync(
      UpdateMediaGovernanceConfigRequest request) {
    return CompletableFuture.supplyAsync(() -> updateMediaGovernanceConfig(request), asyncExecutor);
  }

  /**
   * Gets the platform-level media governance status.
   *
   * <p>Returns whether media governance is available, default enablement, and the required license
   * tier.
   *
   * @return the media governance status
   * @throws AxonFlowException if the request fails
   */
  public MediaGovernanceStatus getMediaGovernanceStatus() {
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildRequest("GET", "/api/v1/media-governance/status", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, MediaGovernanceStatus.class);
          }
        },
        "getMediaGovernanceStatus");
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
  // AuthZEN-native authorization (ADR-065)
  // ========================================================================

  /** The AuthZEN evaluation endpoint. */
  public static final String AUTHZEN_PATH = "/api/v1/access/evaluation";

  /**
   * The mapper this surface DECODES with: the shared one, with unknown members made fatal.
   *
   * <p>{@code createObjectMapper} turns {@code FAIL_ON_UNKNOWN_PROPERTIES} off, which is right
   * everywhere else in this SDK — a server that added a member to a status payload should not break
   * an older client. It is exactly wrong for an authorization decision: an unknown member there
   * means the server is speaking a profile this build does not understand, and reading the members
   * it recognises is acting on a partial interpretation of a decision.
   *
   * <p>It is a COPY rather than a second mapper built from scratch, so it inherits the modules, the
   * date handling and anything added to {@code createObjectMapper} later. The one difference is the
   * one this surface needs, and it is visible in a single line.
   *
   * <p>A per-class {@code @JsonIgnoreProperties(ignoreUnknown = false)} does NOT achieve this, and
   * an earlier version of this code claimed it did: that value is Jackson's default and only
   * declines to IGNORE — whether declining becomes a failure is this feature's decision.
   */
  private final ObjectMapper authzenReader;

  /**
   * The header that negotiates the AxonFlow profile.
   *
   * <p>The SDK always sends it. AuthZEN 1.0's response is a bare boolean, and the four-valued
   * state, the obligations and the approval challenge ride in the response context, which the
   * server returns only to a caller that asked for it by version. This SDK understands the profile,
   * so there is no reason to ask for less than it can read.
   */
  public static final String AUTHZEN_PROFILE_HEADER = "X-Axonflow-AuthZEN-Profile";

  /**
   * Asks whether one subject may perform one action on one resource.
   *
   * <p>Fails closed: every outcome that is not a readable decision is an {@link
   * AuthZENEvaluationException}, and there is no path through this method that returns an allow it
   * could not fully read.
   *
   * <pre>{@code
   * AuthZENDecision decision =
   *     client.evaluate(
   *         AuthZENEvaluation.of(
   *                 new AuthZENSubject("gateway", "llm-gateway-01"),
   *                 new AuthZENAction("llm.completion"),
   *                 new AuthZENResource("llm", "llm"))
   *             .query(Attribute.known(userPrompt))
   *             .build());
   * if (!decision.isAllowed()) {
   *   throw new IllegalStateException("blocked: " + decision.getState());
   * }
   * }</pre>
   *
   * @param request the evaluation
   * @return the decision
   * @throws AuthZENRefusedException if the request was refused rather than evaluated
   * @throws AuthZENUnresolvedException if the request carries an attribute nobody could resolve
   * @throws AuthZENUnreadableProfileException if the server answered in an unknown profile
   * @throws AuthZENUnusableResponseException if the decision cannot be trusted
   * @throws AuthZENTransportException if the request never got an answer
   */
  public AuthZENDecision evaluate(AuthZENRequest request) {
    return evaluateEnvelope(new AuthZENEnvelope().setEvaluation(request));
  }

  /**
   * Asks whether ONE operation is permitted against SEVERAL preconditions.
   *
   * <p>It returns ONE decision, not one per entry. The entries of a bulk request are preconditions
   * of a single operation (moving a ticket must be authorized against the destination project as
   * well as against the ticket), so they combine to the least permissive outcome: one denied entry
   * denies the operation. An API returning a list would invite a caller to act on the entry it
   * liked.
   *
   * <p>Any member an entry omits is inherited from the envelope's shared base, so the common case
   * is a shared subject and action with one resource per entry.
   *
   * @param bulk the preconditions
   * @return the single decision they combine to
   * @throws AuthZENRefusedException if the request was refused rather than evaluated
   * @throws AuthZENUnresolvedException if the request carries an attribute nobody could resolve
   * @throws AuthZENUnreadableProfileException if the server answered in an unknown profile
   * @throws AuthZENUnusableResponseException if the decision cannot be trusted
   * @throws AuthZENTransportException if the request never got an answer
   */
  public AuthZENDecision evaluateAll(AuthZENBulk bulk) {
    return evaluateEnvelope(new AuthZENEnvelope().setEvaluations(bulk));
  }

  /**
   * The one transport path both entry points share.
   *
   * <p>It does NOT go through {@code retryExecutor}. That executor is wired to the proxy path's
   * request type, and retrying an authorization decision on the caller's behalf is a policy
   * decision this SDK does not make for them: a retried allow is a second evaluation the audit
   * trail did not ask for. Retry is the caller's, guided by {@link
   * com.getaxonflow.sdk.authzen.AuthZENEvaluationException#isRetryable()} - which is a different
   * question from {@code RetryExecutor}'s own, because a local refusal carries no HTTP status.
   */
  private AuthZENDecision evaluateEnvelope(AuthZENEnvelope envelope) {
    // Validated before the round trip. The server enforces the same rules and
    // answers with a typed refusal, so for most of these this is a convenience
    // -- a caller that mis-built an envelope learns it from a local error
    // naming the member instead of from a 422.
    //
    // For ONE class it is not a convenience but the whole point: an attribute
    // the caller could not resolve has no wire representation, so the server can
    // never refuse it. Only this check can.
    try {
      envelope.validate("");
    } catch (AuthZENRefusedException refusal) {
      // The one code that has to be re-read on THIS side. From the server,
      // `evaluation_unavailable` means "the evaluator could not be reached; send
      // these bytes again". Produced locally it means "an attribute in this
      // request was never resolved", and resending the identical request
      // reproduces the identical refusal forever. Same code, opposite action, so
      // they must not arrive as the same thing.
      if (AuthZENErrorCode.EVALUATION_UNAVAILABLE.equals(refusal.getCode())) {
        throw new AuthZENUnresolvedException(
            refusal.getPointer(), refusal.getRefusal().getMessage());
      }
      throw refusal;
    }

    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(envelope);
    } catch (JsonProcessingException e) {
      // Reachable when an attribute bag holds an unresolved member and
      // something bypassed validate(). Reported as an unusable response rather
      // than a transport failure: nothing was sent, and calling it a transport
      // problem would send an operator to look at the network.
      throw new AuthZENUnusableResponseException(
          "the envelope could not be encoded: " + e.getMessage());
    }

    // Built through the same helper every other call uses, so this surface
    // inherits the configured endpoint, user agent, auth headers and mode
    // rather than assembling a second opinion about any of them. Only the
    // profile header is added on top.
    Request httpRequest =
        buildRequest("POST", AUTHZEN_PATH, null)
            .newBuilder()
            .post(RequestBody.create(body, JSON))
            .header(AUTHZEN_PROFILE_HEADER, AuthZENContract.PROFILE_V1)
            .build();

    String raw;
    int status;
    try (Response response = executeHttp(httpClient, httpRequest)) {
      status = response.code();
      ResponseBody responseBody = response.body();
      raw = responseBody == null ? "" : responseBody.string();
    } catch (IOException e) {
      throw new AuthZENTransportException("the evaluation request failed: " + e.getMessage(), 0, e);
    }

    if (status != 200) {
      // A refusal is a typed document, so the caller can branch on the code and
      // be pointed at the member to fix. A body that is not one still surfaces
      // as an error -- never as a decision.
      // Decoded with the LENIENT mapper, deliberately. Strictness belongs on
      // the DECISION, where an unknown member means a profile this build cannot
      // read. A refusal is a DIAGNOSTIC: refusing to decode it because the
      // server added a `retry_after` collapses a typed error carrying a code and
      // a JSON Pointer into an opaque transport failure carrying neither, which
      // is the one thing a refusal exists to avoid. The Go reference makes the
      // same split; an earlier version of this method did not.
      AuthZENError refusal;
      try {
        refusal = objectMapper.readValue(raw, AuthZENError.class);
      } catch (IOException ignored) {
        refusal = null;
      }
      // A 5xx is only READ as a refusal when the code is one this build KNOWS.
      // An unrecognised code round-trips as an unknown carrier, which is
      // deliberately non-retryable - so an ingress or sidecar answering 503 with
      // its own JSON error body would otherwise turn a transient outage into a
      // permanent refusal that a `while (isRetryable())` loop will not retry. A
      // 4xx is still read as a refusal whatever the code, because "fix the
      // request" is right either way and the pointer is worth more than the
      // code.
      boolean usable =
          refusal != null
              && refusal.getCode() != null
              && !refusal.getCode().value().isEmpty()
              && refusal.getMessage() != null
              && !refusal.getMessage().isEmpty();
      if (usable && (status < 500 || refusal.getCode().isKnown())) {
        throw new AuthZENRefusedException(refusal);
      }
      throw new AuthZENTransportException(
          "the evaluation request failed with HTTP " + status + ": " + raw, status, null);
    }

    // Strict decoding on the success path, through authzenReader rather than
    // the shared mapper. An unknown member in a decision is a server speaking a
    // profile this build does not understand, and quietly dropping it would
    // mean acting on a partial reading of an authorization decision.
    AuthZENResponse decoded;
    try {
      decoded = authzenReader.readValue(raw, AuthZENResponse.class);
    } catch (IOException e) {
      throw new AuthZENUnusableResponseException(
          "the decision could not be decoded: " + e.getMessage() + "; body=" + raw);
    }
    return AuthZENDecision.from(decoded, AUTHZEN_PROFILE_HEADER);
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

  /** Clears the response cache. */
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

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("X-Axonflow-Client", config.getClientHeader())
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

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("X-Axonflow-Client", config.getClientHeader())
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

  private String buildDynamicPolicyQueryString(
      String basePath, ListDynamicPoliciesOptions options) {
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
    String encoded =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    builder.header("Authorization", "Basic " + encoded);
    // ADR-050 §4: every governed request to the agent carries X-Axonflow-Client
    // so the agent can derive request scope (sdk) and validate it against the
    // token's aud.scope via HasScope(). Sourced from SDK_VERSION; no env override.
    builder.header("X-Axonflow-Client", config.getClientHeader());
    // X-Client-ID (v9): server-side identity decisions don't have to
    // re-decode Basic auth. The agent's apiAuthMiddleware overwrites
    // the header with its auth-derived value, so caller-supplied
    // values are harmless (no spoofing surface).
    builder.header("X-Client-ID", effectiveClientId);
  }

  /**
   * Requires credentials for enterprise features. Get the effective clientId, using smart default
   * for community mode.
   *
   * <p>Returns the configured clientId if set, otherwise returns "community" as a smart default.
   * This enables zero-config usage for community/self-hosted deployments while still supporting
   * enterprise deployments with explicit credentials.
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
      throw new AxonFlowException(
          "Failed to parse response: " + e.getMessage(), response.code(), null, e);
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
      throw new AxonFlowException(
          "Failed to parse response: " + e.getMessage(), response.code(), null, e);
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
      throw new AxonFlowException(
          "Failed to parse response: " + e.getMessage(), response.code(), null, e);
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
        // A 403 is a policy violation only when the body actually signals a
        // block. Every agent error envelope carries a literal "blocked" key
        // (e.g. {"success":false,"error":"Tenant mismatch","blocked":false}),
        // so the old substring heuristic misclassified 403 auth rejections
        // as policy violations.
        if (isPolicyBlockBody(body)) {
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

  /**
   * Decides whether a 403 error body signals a genuine policy block.
   *
   * <p>Parses the JSON envelope and reads the {@code blocked} boolean. When the field is present it
   * is authoritative: {@code true} means a policy block ({@link PolicyViolationException}), {@code
   * false} means the 403 is an authorization rejection (e.g. tenant mismatch) even though the raw
   * body contains the literal substring {@code "blocked"}. Only when the body is not parseable
   * JSON, or carries no {@code blocked} boolean, does this fall back to the legacy policy-phrase
   * heuristic (for older agents whose error envelopes predate the field).
   */
  private boolean isPolicyBlockBody(String body) {
    if (body == null || body.isEmpty()) {
      return false;
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode blocked = node.get("blocked");
      if (blocked != null && blocked.isBoolean()) {
        return blocked.asBoolean();
      }
    } catch (JsonProcessingException e) {
      // Not JSON — fall through to the phrase heuristic.
    }
    return body.contains("policy") || body.contains("block_reason");
  }

  private String extractErrorMessage(String body, String defaultMessage) {
    if (body == null || body.isEmpty()) {
      return defaultMessage;
    }

    try {
      Map<String, Object> errorResponse =
          objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});

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
   * Login to Customer Portal and store session cookie. Required before using Code Governance
   * methods.
   *
   * @param orgId the organization ID
   * @param password the organization password
   * @return login response with session info
   * @throws IOException if the request fails
   * @example
   *     <pre>{@code
   * PortalLoginResponse login = axonflow.loginToPortal("test-org-001", "test123");
   * System.out.println("Logged in as: " + login.getName());
   *
   * // Now you can use Code Governance methods
   * ListGitProvidersResponse providers = axonflow.listGitProviders();
   * }</pre>
   */
  public PortalLoginResponse loginToPortal(String orgId, String password) throws IOException {
    logger.debug("Logging in to portal: {}", orgId);

    String json =
        objectMapper.writeValueAsString(java.util.Map.of("org_id", orgId, "password", password));
    RequestBody body = RequestBody.create(json, JSON);

    Request request =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/auth/login")
            .post(body)
            .header("Content-Type", "application/json")
            .build();

    try (Response response = executeHttp(httpClient, request)) {
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

  /** Logout from Customer Portal and clear session cookie. */
  public void logoutFromPortal() {
    if (sessionCookie == null) {
      return;
    }

    try {
      Request request =
          new Request.Builder()
              .url(config.getEndpoint() + "/api/v1/auth/logout")
              .post(RequestBody.create("", JSON))
              .header("Cookie", "axonflow_session=" + sessionCookie)
              .build();

      executeHttp(httpClient, request).close();
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
   * Validates Git provider credentials without saving them. Requires prior authentication via
   * loginToPortal().
   *
   * @param request the validation request with provider type and credentials
   * @return validation result
   * @throws IOException if the request fails
   */
  public ValidateGitProviderResponse validateGitProvider(ValidateGitProviderRequest request)
      throws IOException {
    requirePortalLogin();
    logger.debug("Validating Git provider: {}", request.getType());

    String json = objectMapper.writeValueAsString(request);
    RequestBody body = RequestBody.create(json, JSON);

    Request.Builder builder =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/code-governance/git-providers/validate")
            .post(body);

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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
  public ConfigureGitProviderResponse configureGitProvider(ConfigureGitProviderRequest request)
      throws IOException {
    requirePortalLogin();
    logger.debug("Configuring Git provider: {}", request.getType());

    String json = objectMapper.writeValueAsString(request);
    RequestBody body = RequestBody.create(json, JSON);

    Request.Builder builder =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/code-governance/git-providers")
            .post(body);

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/code-governance/git-providers")
            .get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder =
        new Request.Builder()
            .url(
                config.getEndpoint()
                    + "/api/v1/code-governance/git-providers/"
                    + providerType.getValue())
            .delete();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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
    logger.debug(
        "Creating PR: {} in {}/{}", request.getTitle(), request.getOwner(), request.getRepo());

    String json = objectMapper.writeValueAsString(request);
    RequestBody body = RequestBody.create(json, JSON);

    Request.Builder builder =
        new Request.Builder().url(config.getEndpoint() + "/api/v1/code-governance/prs").post(body);

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder = new Request.Builder().url(url.toString()).get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/code-governance/prs/" + prId)
            .get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder =
        new Request.Builder()
            .url(config.getEndpoint() + "/api/v1/code-governance/prs/" + prId + "/sync")
            .post(body);

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
      return parseResponse(response, PRRecord.class);
    }
  }

  /**
   * Closes a PR without merging and optionally deletes the branch. This is an enterprise feature
   * for cleaning up test/demo PRs. Supports all Git providers: GitHub, GitLab, Bitbucket.
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

    Request.Builder builder = new Request.Builder().url(url).delete();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder =
        new Request.Builder().url(config.getEndpoint() + "/api/v1/code-governance/metrics").get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder = new Request.Builder().url(url.toString()).get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

    Request.Builder builder = new Request.Builder().url(url.toString()).get();

    addPortalSessionCookie(builder);

    try (Response response = executeHttp(httpClient, builder.build())) {
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

  /** Builds a request for the orchestrator API. */
  private Request buildOrchestratorRequest(String method, String path, Object body) {
    HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
    if (url == null) {
      throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
    }

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("X-Axonflow-Client", config.getClientHeader())
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

  /** Requires portal login before making code governance requests. */
  private void requirePortalLogin() {
    if (sessionCookie == null) {
      throw new AuthenticationException(
          "Not logged in to Customer Portal. Call loginToPortal() first.");
    }
  }

  /** Adds the session cookie header for portal authentication. */
  private void addPortalSessionCookie(Request.Builder builder) {
    if (sessionCookie != null) {
      builder.header("Cookie", "axonflow_session=" + sessionCookie);
    }
  }

  /**
   * Builds a request for the Customer Portal API (enterprise features). Requires prior
   * authentication via loginToPortal().
   */
  private Request buildPortalRequest(String method, String path, Object body) {
    requirePortalLogin();

    HttpUrl url = HttpUrl.parse(config.getEndpoint() + path);
    if (url == null) {
      throw new ConfigurationException("Invalid URL: " + config.getEndpoint() + path);
    }

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", config.getUserAgent())
            .header("X-Axonflow-Client", config.getClientHeader())
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
   * @example
   *     <pre>{@code
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
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ListExecutionsResponse.class);
          }
        },
        "listExecutions");
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
   * @example
   *     <pre>{@code
   * ExecutionDetail detail = axonflow.getExecution("exec-abc123");
   * System.out.println("Status: " + detail.getSummary().getStatus());
   * for (ExecutionSnapshot step : detail.getSteps()) {
   *     System.out.println("Step " + step.getStepIndex() + ": " + step.getStepName());
   * }
   * }</pre>
   */
  public ExecutionDetail getExecution(String executionId) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/executions/" + executionId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ExecutionDetail.class);
          }
        },
        "getExecution");
  }

  /**
   * Gets all step snapshots for an execution.
   *
   * @param executionId the execution ID (request_id)
   * @return list of step snapshots
   */
  public List<ExecutionSnapshot> getExecutionSteps(String executionId) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/executions/" + executionId + "/steps", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, new TypeReference<List<ExecutionSnapshot>>() {});
          }
        },
        "getExecutionSteps");
  }

  /**
   * Gets a timeline view of execution events for visualization.
   *
   * @param executionId the execution ID (request_id)
   * @return list of timeline entries
   */
  public List<TimelineEntry> getExecutionTimeline(String executionId) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "GET", "/api/v1/executions/" + executionId + "/timeline", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, new TypeReference<List<TimelineEntry>>() {});
          }
        },
        "getExecutionTimeline");
  }

  /**
   * Exports a complete execution record for compliance or archival.
   *
   * @param executionId the execution ID (request_id)
   * @param options export options
   * @return execution data as a map
   * @example
   *     <pre>{@code
   * Map<String, Object> export = axonflow.exportExecution("exec-abc123",
   *     ExecutionExportOptions.builder()
   *         .setIncludeInput(true)
   *         .setIncludeOutput(true));
   * // Save to file for audit
   * }</pre>
   */
  public Map<String, Object> exportExecution(String executionId, ExecutionExportOptions options) {
    Objects.requireNonNull(executionId, "executionId cannot be null");

    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, new TypeReference<Map<String, Object>>() {});
          }
        },
        "exportExecution");
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

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("DELETE", "/api/v1/executions/" + executionId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deleteExecution");
  }

  /**
   * Asynchronously lists workflow executions.
   *
   * @param options filtering and pagination options
   * @return a future containing the list of executions
   */
  public CompletableFuture<ListExecutionsResponse> listExecutionsAsync(
      ListExecutionsOptions options) {
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/budgets", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, Budget.class);
          }
        },
        "createBudget");
  }

  /**
   * Gets a budget by ID.
   *
   * @param budgetId the budget ID
   * @return the budget
   */
  public Budget getBudget(String budgetId) {
    Objects.requireNonNull(budgetId, "budgetId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, Budget.class);
          }
        },
        "getBudget");
  }

  /**
   * Lists all budgets.
   *
   * @param options filtering and pagination options
   * @return list of budgets
   */
  public BudgetsResponse listBudgets(ListBudgetsOptions options) {
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, BudgetsResponse.class);
          }
        },
        "listBudgets");
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("PUT", "/api/v1/budgets/" + budgetId, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, Budget.class);
          }
        },
        "updateBudget");
  }

  /**
   * Deletes a budget.
   *
   * @param budgetId the budget ID
   */
  public void deleteBudget(String budgetId) {
    Objects.requireNonNull(budgetId, "budgetId cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("DELETE", "/api/v1/budgets/" + budgetId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful() && response.code() != 204) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deleteBudget");
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId + "/status", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, BudgetStatus.class);
          }
        },
        "getBudgetStatus");
  }

  /**
   * Gets alerts for a budget.
   *
   * @param budgetId the budget ID
   * @return the budget alerts
   */
  public BudgetAlertsResponse getBudgetAlerts(String budgetId) {
    Objects.requireNonNull(budgetId, "budgetId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/budgets/" + budgetId + "/alerts", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, BudgetAlertsResponse.class);
          }
        },
        "getBudgetAlerts");
  }

  /**
   * Performs a pre-flight budget check.
   *
   * @param request the check request
   * @return the budget decision
   */
  public BudgetDecision checkBudget(BudgetCheckRequest request) {
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/budgets/check", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, BudgetDecision.class);
          }
        },
        "checkBudget");
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
    return retryExecutor.execute(
        () -> {
          StringBuilder path = new StringBuilder("/api/v1/usage");
          if (period != null && !period.isEmpty()) {
            path.append("?period=").append(period);
          }

          Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, UsageSummary.class);
          }
        },
        "getUsageSummary");
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
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, UsageBreakdown.class);
          }
        },
        "getUsageBreakdown");
  }

  /**
   * Lists usage records.
   *
   * @param options filtering and pagination options
   * @return list of usage records
   */
  public UsageRecordsResponse listUsageRecords(ListUsageRecordsOptions options) {
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, UsageRecordsResponse.class);
          }
        },
        "listUsageRecords");
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
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
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
        },
        "getPricing");
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
   * <p>Registers a new workflow with AxonFlow. Call this at the start of your external orchestrator
   * workflow (LangChain, LangGraph, CrewAI, etc.).
   *
   * @param request workflow creation request
   * @return created workflow with ID
   * @throws AxonFlowException if creation fails
   * @example
   *     <pre>{@code
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/workflows", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse>() {});
          }
        },
        "createWorkflow");
  }

  /**
   * Gets the status of a workflow.
   *
   * @param workflowId workflow ID
   * @return workflow status including steps
   * @throws AxonFlowException if workflow not found
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse getWorkflow(
      String workflowId) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/workflows/" + workflowId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.WorkflowStatusResponse>() {});
          }
        },
        "getWorkflow");
  }

  /**
   * Checks if a workflow step is allowed to proceed (step gate).
   *
   * <p>This is the core governance method. Call this before executing each step in your workflow to
   * check if the step is allowed based on policies.
   *
   * @param workflowId workflow ID
   * @param stepId unique step identifier (you provide this)
   * @param request step gate request with step details
   * @return gate decision: allow, block, or require_approval
   * @throws AxonFlowException if check fails
   * @example
   *     <pre>{@code
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
    return stepGate(
        workflowId,
        stepId,
        request,
        com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateOptions.defaults());
  }

  /**
   * Checks if a workflow step is allowed to proceed, with call-level options.
   *
   * <p>Pass {@link
   * com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateOptions#includePriorOutput()} to send
   * {@code ?include_prior_output=true} so the response's {@code retry_context.prior_output} is
   * populated when a prior /complete has landed.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @param request step gate request
   * @param options call-level options (e.g. {@code includePriorOutput})
   * @return the step gate response
   * @throws IdempotencyKeyMismatchException if {@code request.idempotencyKey} conflicts with the
   *     key recorded on an earlier gate call for this (workflow, step)
   * @since 5.6.0
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse stepGate(
      String workflowId,
      String stepId,
      com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest request,
      com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateOptions options) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");
    Objects.requireNonNull(stepId, "stepId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");
    Objects.requireNonNull(options, "options cannot be null");

    String path = "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/gate";
    if (options.isIncludePriorOutput()) {
      path += "?include_prior_output=true";
    }
    final String fullPath = path;

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", fullPath, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (response.code() == 409) {
              throw toIdempotencyOrGeneric409(response, workflowId, stepId);
            }
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse>() {});
          }
        },
        "stepGate");
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

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST",
                  "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/complete",
                  request != null ? request : Collections.emptyMap());
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (response.code() == 409) {
              throw toIdempotencyOrGeneric409(response, workflowId, stepId);
            }
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "markStepCompleted");
  }

  /**
   * Inspects a 409 response on a step gate/complete call. If the body carries {@code error.code ==
   * "IDEMPOTENCY_KEY_MISMATCH"}, returns a typed {@link IdempotencyKeyMismatchException}; otherwise
   * falls back to a generic {@link AxonFlowException}.
   *
   * <p>Must only be called on responses with {@code response.code() == 409}. Consumes the response
   * body.
   */
  private AxonFlowException toIdempotencyOrGeneric409(
      Response response, String workflowId, String stepId) throws IOException {
    String body = response.body() != null ? response.body().string() : "";
    if (!body.isEmpty()) {
      try {
        JsonNode root = objectMapper.readTree(body);
        JsonNode err = root.path("error");
        if (err.path("code").asText("").equals("IDEMPOTENCY_KEY_MISMATCH")) {
          JsonNode details = err.path("details");
          String msg = err.path("message").asText("idempotency_key mismatch");
          String detailsWorkflowId = details.path("workflow_id").asText(workflowId);
          String detailsStepId = details.path("step_id").asText(stepId);
          String expected = details.path("expected_idempotency_key").asText("");
          String received = details.path("received_idempotency_key").asText("");
          return new IdempotencyKeyMismatchException(
              msg, detailsWorkflowId, detailsStepId, expected, received);
        }
      } catch (IOException ignored) {
        // Fall through to generic 409 handling
      }
    }
    String fallback = body.isEmpty() ? response.message() : body;
    return new AxonFlowException(fallback, 409, null);
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

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/workflows/" + workflowId + "/complete", Collections.emptyMap());
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "completeWorkflow");
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

    retryExecutor.execute(
        () -> {
          Map<String, String> body =
              reason != null ? Collections.singletonMap("reason", reason) : Collections.emptyMap();
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/workflows/" + workflowId + "/abort", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "abortWorkflow");
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
   * <p>Call this when a workflow has encountered an unrecoverable error and should be marked as
   * failed.
   *
   * @param workflowId workflow ID
   * @param reason optional reason for failing
   */
  public void failWorkflow(String workflowId, String reason) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");

    retryExecutor.execute(
        () -> {
          Map<String, String> body =
              reason != null ? Collections.singletonMap("reason", reason) : Collections.emptyMap();
          Request httpRequest =
              buildOrchestratorRequest("POST", "/api/v1/workflows/" + workflowId + "/fail", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "failWorkflow");
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
    return CompletableFuture.supplyAsync(
        () -> {
          failWorkflow(workflowId, reason);
          return null;
        },
        asyncExecutor);
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

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/workflows/" + workflowId + "/resume", Collections.emptyMap());
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "resumeWorkflow");
  }

  /**
   * Lists step-gate checkpoints for a workflow. Available in all tiers.
   *
   * @param workflowId workflow ID
   * @return checkpoint list
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.CheckpointListResponse getCheckpoints(
      String workflowId) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");
    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "GET", "/api/v1/workflows/" + workflowId + "/checkpoints", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.CheckpointListResponse>() {});
          }
        },
        "getCheckpoints");
  }

  /**
   * Resumes a workflow from its last resumable checkpoint. Evaluation+ tier.
   *
   * @param workflowId workflow ID
   * @return resume result with fresh decision
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ResumeFromCheckpointResponse
      resumeFromLastCheckpoint(String workflowId) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");
    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/workflows/" + workflowId + "/checkpoints/resume", "{}");
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes
                        .ResumeFromCheckpointResponse>() {});
          }
        },
        "resumeFromLastCheckpoint");
  }

  /**
   * Resumes a workflow from a specific checkpoint. Enterprise only.
   *
   * @param workflowId workflow ID
   * @param checkpointId checkpoint database ID
   * @return resume result with fresh decision
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ResumeFromCheckpointResponse
      resumeFromCheckpoint(String workflowId, long checkpointId) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");
    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST",
                  "/api/v1/workflows/" + workflowId + "/checkpoints/" + checkpointId + "/resume",
                  "{}");
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes
                        .ResumeFromCheckpointResponse>() {});
          }
        },
        "resumeFromCheckpoint");
  }

  /**
   * Lists workflows with optional filters.
   *
   * @param options filter and pagination options
   * @return list of workflows
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsResponse listWorkflows(
      com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsOptions options) {
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.ListWorkflowsResponse>() {});
          }
        },
        "listWorkflows");
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
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.CreateWorkflowResponse>
      createWorkflowAsync(
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
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse>
      stepGateAsync(
          String workflowId,
          String stepId,
          com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest request) {
    return CompletableFuture.supplyAsync(
        () -> stepGate(workflowId, stepId, request), asyncExecutor);
  }

  // ========================================================================
  // WCP Approval Methods
  // ========================================================================

  /**
   * Approves a workflow step that requires human approval.
   *
   * <p>Call this when a step gate returns {@code require_approval} to approve the step and allow
   * the workflow to proceed. Prefer the two-arg overload when you can pass a comment — the server
   * requires a comment (min 10 chars) as an audit justification.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @return the approval response
   * @throws AxonFlowException if the approval fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse approveStep(
      String workflowId, String stepId) {
    return approveStep(workflowId, stepId, null);
  }

  /**
   * Approves a workflow step that requires human approval, with an audit comment.
   *
   * <p>The server requires {@code comment} with a minimum of 10 characters — it's the audit-trail
   * justification that every approval carries into the workflow history.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @param comment audit justification for the approval (min 10 chars server-side)
   * @return the approval response
   * @throws AxonFlowException if the approval fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse approveStep(
      String workflowId, String stepId, String comment) {
    Objects.requireNonNull(workflowId, "workflowId cannot be null");
    Objects.requireNonNull(stepId, "stepId cannot be null");

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = new HashMap<>();
          if (comment != null && !comment.isEmpty()) {
            body.put("comment", comment);
          }
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST",
                  "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/approve",
                  body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse>() {});
          }
        },
        "approveStep");
  }

  /**
   * Asynchronously approves a workflow step.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @return a future containing the approval response
   */
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse>
      approveStepAsync(String workflowId, String stepId) {
    return approveStepAsync(workflowId, stepId, null);
  }

  /**
   * Asynchronously approves a workflow step with an audit comment.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @param comment audit justification
   * @return a future containing the approval response
   */
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse>
      approveStepAsync(String workflowId, String stepId, String comment) {
    return CompletableFuture.supplyAsync(
        () -> approveStep(workflowId, stepId, comment), asyncExecutor);
  }

  /**
   * Rejects a workflow step that requires human approval.
   *
   * <p>Call this when a step gate returns {@code require_approval} to reject the step and prevent
   * the workflow from proceeding.
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
   * <p>Call this when a step gate returns {@code require_approval} to reject the step and prevent
   * the workflow from proceeding.
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

    return retryExecutor.execute(
        () -> {
          Map<String, Object> body = new HashMap<>();
          if (reason != null && !reason.isEmpty()) {
            body.put("reason", reason);
          }
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/workflows/" + workflowId + "/steps/" + stepId + "/reject", body);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse>() {});
          }
        },
        "rejectStep");
  }

  /**
   * Asynchronously rejects a workflow step.
   *
   * @param workflowId workflow ID
   * @param stepId step ID
   * @return a future containing the rejection response
   */
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse>
      rejectStepAsync(String workflowId, String stepId) {
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
  public CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse>
      rejectStepAsync(String workflowId, String stepId, String reason) {
    return CompletableFuture.supplyAsync(
        () -> rejectStep(workflowId, stepId, reason), asyncExecutor);
  }

  /**
   * Gets pending approvals with a limit.
   *
   * @param limit maximum number of pending approvals to return
   * @return the pending approvals response
   * @throws AxonFlowException if the request fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse
      getPendingApprovals(int limit) {
    return retryExecutor.execute(
        () -> {
          StringBuilder path = new StringBuilder("/api/v1/workflows/approvals/pending");
          if (limit > 0) {
            path.append("?limit=").append(limit);
          }

          Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes
                        .PendingApprovalsResponse>() {});
          }
        },
        "getPendingApprovals");
  }

  /**
   * Gets MAP-plane pending approvals — the counterpart of {@link #getPendingApprovals(int)}.
   *
   * <p>Lists pending approvals for MAP-backed workflows ({@code GET
   * /api/v1/plans/approvals/pending}). Every returned entry has {@code planId} populated; WCP-only
   * approvals are not returned.
   *
   * <p>Requires an Evaluation or Enterprise license (same tier gate as the MAP step approve/reject
   * endpoints).
   *
   * @param limit maximum number of pending approvals to return (0 for server default)
   * @param planId optional plan id filter — when non-null, scopes the listing to a single plan
   * @return the pending approvals response
   * @throws AxonFlowException if the request fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse
      getPendingPlanApprovals(int limit, String planId) {
    return retryExecutor.execute(
        () -> {
          StringBuilder path = new StringBuilder("/api/v1/plans/approvals/pending");
          boolean hasQuery = false;
          if (limit > 0) {
            path.append("?limit=").append(limit);
            hasQuery = true;
          }
          if (planId != null && !planId.isEmpty()) {
            path.append(hasQuery ? '&' : '?')
                .append("plan_id=")
                .append(
                    java.net.URLEncoder.encode(planId, java.nio.charset.StandardCharsets.UTF_8));
          }

          Request httpRequest = buildOrchestratorRequest("GET", path.toString(), null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(
                response,
                new TypeReference<
                    com.getaxonflow.sdk.types.workflow.WorkflowTypes
                        .PendingApprovalsResponse>() {});
          }
        },
        "getPendingPlanApprovals");
  }

  /**
   * Gets all MAP-plane pending approvals with the server default limit and no plan filter.
   *
   * @return the pending approvals response
   * @throws AxonFlowException if the request fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse
      getPendingPlanApprovals() {
    return getPendingPlanApprovals(0, null);
  }

  /**
   * Gets MAP-plane pending approvals with a limit and no plan filter.
   *
   * @param limit maximum number of pending approvals to return
   * @return the pending approvals response
   * @throws AxonFlowException if the request fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse
      getPendingPlanApprovals(int limit) {
    return getPendingPlanApprovals(limit, null);
  }

  /**
   * Asynchronously gets MAP-plane pending approvals.
   *
   * @param limit maximum number of pending approvals to return
   * @param planId optional plan id filter
   * @return a future containing the pending approvals response
   */
  public CompletableFuture<
          com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse>
      getPendingPlanApprovalsAsync(int limit, String planId) {
    return CompletableFuture.supplyAsync(
        () -> getPendingPlanApprovals(limit, planId), asyncExecutor);
  }

  /**
   * Gets all pending approvals with default limit.
   *
   * @return the pending approvals response
   * @throws AxonFlowException if the request fails
   */
  public com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse
      getPendingApprovals() {
    return getPendingApprovals(0);
  }

  /**
   * Asynchronously gets pending approvals with a limit.
   *
   * @param limit maximum number of pending approvals to return
   * @return a future containing the pending approvals response
   */
  public CompletableFuture<
          com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse>
      getPendingApprovalsAsync(int limit) {
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/webhooks", request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, WebhookSubscription.class);
          }
        },
        "createWebhook");
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/webhooks/" + webhookId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, WebhookSubscription.class);
          }
        },
        "getWebhook");
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
   * @param request the update request
   * @return the updated webhook subscription
   * @throws AxonFlowException if the update fails
   */
  public WebhookSubscription updateWebhook(String webhookId, UpdateWebhookRequest request) {
    Objects.requireNonNull(webhookId, "webhookId cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("PUT", "/api/v1/webhooks/" + webhookId, request);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, WebhookSubscription.class);
          }
        },
        "updateWebhook");
  }

  /**
   * Asynchronously updates an existing webhook subscription.
   *
   * @param webhookId the webhook ID
   * @param request the update request
   * @return a future containing the updated webhook subscription
   */
  public CompletableFuture<WebhookSubscription> updateWebhookAsync(
      String webhookId, UpdateWebhookRequest request) {
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

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("DELETE", "/api/v1/webhooks/" + webhookId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "deleteWebhook");
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
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/webhooks", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            return parseResponse(response, ListWebhooksResponse.class);
          }
        },
        "listWebhooks");
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
   * <p>Returns approval requests from the HITL queue, optionally filtered by status and severity.
   *
   * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
   *
   * @param opts filtering and pagination options (may be null)
   * @return the list response containing approval requests
   * @throws AxonFlowException if the request fails
   */
  public HITLQueueListResponse listHITLQueue(HITLQueueListOptions opts) {
    return retryExecutor.execute(
        () -> {
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
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);

            // Server wraps response: {"success": true, "data": [...], "meta": {...}}
            HITLQueueListResponse result = new HITLQueueListResponse();
            if (node.has("data") && node.get("data").isArray()) {
              List<HITLApprovalRequest> items =
                  objectMapper.convertValue(
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
        },
        "listHITLQueue");
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

    return retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest("GET", "/api/v1/hitl/queue/" + requestId, null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);

            // Server wraps response: {"success": true, "data": {...}}
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), HITLApprovalRequest.class);
            }
            return objectMapper.treeToValue(node, HITLApprovalRequest.class);
          }
        },
        "getHITLRequest");
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
   * Creates a new HITL approval request via {@code POST /api/v1/hitl/queue}.
   *
   * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license. The platform returns 403
   * with {@code ErrHITLApprovalDisabledByTier} when called against a community tier that hasn't
   * enabled HITL, and 401 when credentials are invalid.
   *
   * <p>This is the explicit row-creation step for callers that detect {@code require_approval} from
   * a separate gate ({@code pre_check}, {@code check_tool_input}, MAP plan approvals) and want the
   * row enqueued so a reviewer can act on it. After creating, either poll {@link
   * #getHITLRequest(String)} until terminal state, or supply {@link
   * HITLCreateInput#setNotifyUrl(String) notifyUrl} so the platform fires a signed webhook on the
   * transition (n8n Wait-node "On Webhook Call" pattern, ADK plugin polling-free mode).
   *
   * <p>{@code clientId}, {@code originalQuery}, and {@code requestType} are required; all other
   * fields are optional. Bad {@code notifyUrl} schemes are rejected by the platform with HTTP 400
   * (surfaced here via {@link AxonFlowException}); only {@code https://} (and {@code http://} for
   * self-hosted local-dev) are accepted.
   *
   * @param input the create-request input
   * @return the created approval request with {@code requestId} populated
   * @throws AxonFlowException if validation or the platform call fails
   */
  public HITLApprovalRequest createHITLRequest(HITLCreateInput input) {
    Objects.requireNonNull(input, "input cannot be null");
    if (input.getClientId() == null || input.getClientId().isEmpty()) {
      throw new IllegalArgumentException("client_id is required");
    }
    if (input.getOriginalQuery() == null || input.getOriginalQuery().isEmpty()) {
      throw new IllegalArgumentException("original_query is required");
    }
    if (input.getRequestType() == null || input.getRequestType().isEmpty()) {
      throw new IllegalArgumentException("request_type is required");
    }

    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("POST", "/api/v1/hitl/queue", input);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);

            // Server wraps response: {"success": true, "data": {...}}
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), HITLApprovalRequest.class);
            }
            return objectMapper.treeToValue(node, HITLApprovalRequest.class);
          }
        },
        "createHITLRequest");
  }

  /**
   * Asynchronously creates a new HITL approval request.
   *
   * @param input the create-request input
   * @return a future containing the created approval request
   */
  public CompletableFuture<HITLApprovalRequest> createHITLRequestAsync(HITLCreateInput input) {
    return CompletableFuture.supplyAsync(() -> createHITLRequest(input), asyncExecutor);
  }

  /**
   * Approves a HITL approval request.
   *
   * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
   *
   * @param requestId the approval request ID
   * @param review the review input containing reviewer details
   * @throws AxonFlowException if the approval fails
   */
  public void approveHITLRequest(String requestId, HITLReviewInput review) {
    Objects.requireNonNull(requestId, "requestId cannot be null");
    Objects.requireNonNull(review, "review cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/hitl/queue/" + requestId + "/approve", review);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "approveHITLRequest");
  }

  /**
   * Asynchronously approves a HITL approval request.
   *
   * @param requestId the approval request ID
   * @param review the review input containing reviewer details
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
   * @param review the review input containing reviewer details
   * @throws AxonFlowException if the rejection fails
   */
  public void rejectHITLRequest(String requestId, HITLReviewInput review) {
    Objects.requireNonNull(requestId, "requestId cannot be null");
    Objects.requireNonNull(review, "review cannot be null");

    retryExecutor.execute(
        () -> {
          Request httpRequest =
              buildOrchestratorRequest(
                  "POST", "/api/v1/hitl/queue/" + requestId + "/reject", review);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            if (!response.isSuccessful()) {
              handleErrorResponse(response);
            }
            return null;
          }
        },
        "rejectHITLRequest");
  }

  /**
   * Asynchronously rejects a HITL approval request.
   *
   * @param requestId the approval request ID
   * @param review the review input containing reviewer details
   * @return a future that completes when the request has been rejected
   */
  public CompletableFuture<Void> rejectHITLRequestAsync(String requestId, HITLReviewInput review) {
    return CompletableFuture.runAsync(() -> rejectHITLRequest(requestId, review), asyncExecutor);
  }

  /**
   * Gets HITL dashboard statistics.
   *
   * <p>Returns aggregate statistics about the HITL queue including total pending requests, priority
   * breakdowns, and age metrics.
   *
   * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
   *
   * @return the dashboard statistics
   * @throws AxonFlowException if the request fails
   */
  public HITLStats getHITLStats() {
    return retryExecutor.execute(
        () -> {
          Request httpRequest = buildOrchestratorRequest("GET", "/api/v1/hitl/stats", null);
          try (Response response = executeHttp(httpClient, httpRequest)) {
            JsonNode node = parseResponseNode(response);

            // Server wraps response: {"success": true, "data": {...}}
            if (node.has("data") && node.get("data").isObject()) {
              return objectMapper.treeToValue(node.get("data"), HITLStats.class);
            }
            return objectMapper.treeToValue(node, HITLStats.class);
          }
        },
        "getHITLStats");
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
   * MAS FEAT (Monetary Authority of Singapore - Fairness, Ethics, Accountability, Transparency)
   * compliance namespace.
   *
   * <p>Provides methods for AI system registry, FEAT assessments, and kill switch management for
   * Singapore financial services compliance.
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

      return retryExecutor.execute(
          () -> {
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
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseSystemResponse(response);
            }
          },
          "masfeat.registerSystem");
    }

    /**
     * Activates an AI system (changes status to 'active').
     *
     * @param systemId the system UUID (not the systemId string)
     * @return the activated system
     */
    public AISystemRegistry activateSystem(String systemId) {
      Objects.requireNonNull(systemId, "systemId cannot be null");

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "active");

            Request httpRequest =
                buildOrchestratorRequest("PUT", BASE_PATH + "/registry/" + systemId, body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseSystemResponse(response);
            }
          },
          "masfeat.activateSystem");
    }

    /**
     * Gets an AI system by its UUID.
     *
     * @param systemId the system UUID
     * @return the system
     */
    public AISystemRegistry getSystem(String systemId) {
      Objects.requireNonNull(systemId, "systemId cannot be null");

      return retryExecutor.execute(
          () -> {
            Request httpRequest =
                buildOrchestratorRequest("GET", BASE_PATH + "/registry/" + systemId, null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseSystemResponse(response);
            }
          },
          "masfeat.getSystem");
    }

    /**
     * Gets the registry summary statistics.
     *
     * @return the registry summary
     */
    public RegistrySummary getRegistrySummary() {
      return retryExecutor.execute(
          () -> {
            Request httpRequest =
                buildOrchestratorRequest("GET", BASE_PATH + "/registry/summary", null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseSummaryResponse(response);
            }
          },
          "masfeat.getRegistrySummary");
    }

    /**
     * Creates a new FEAT assessment.
     *
     * @param request the assessment creation request
     * @return the created assessment
     */
    public FEATAssessment createAssessment(CreateAssessmentRequest request) {
      Objects.requireNonNull(request, "request cannot be null");

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("system_id", request.getSystemId());
            body.put("assessment_type", request.getAssessmentType());
            if (request.getAssessors() != null) {
              body.put("assessors", request.getAssessors());
            }

            Request httpRequest =
                buildOrchestratorRequest("POST", BASE_PATH + "/assessments", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.createAssessment");
    }

    /**
     * Gets a FEAT assessment by its ID.
     *
     * @param assessmentId the assessment ID
     * @return the assessment
     */
    public FEATAssessment getAssessment(String assessmentId) {
      Objects.requireNonNull(assessmentId, "assessmentId cannot be null");

      return retryExecutor.execute(
          () -> {
            Request httpRequest =
                buildOrchestratorRequest("GET", BASE_PATH + "/assessments/" + assessmentId, null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.getAssessment");
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

      return retryExecutor.execute(
          () -> {
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

            Request httpRequest =
                buildOrchestratorRequest("PUT", BASE_PATH + "/assessments/" + assessmentId, body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.updateAssessment");
    }

    /**
     * Submits a FEAT assessment for review.
     *
     * @param assessmentId the assessment ID
     * @return the submitted assessment
     */
    public FEATAssessment submitAssessment(String assessmentId) {
      Objects.requireNonNull(assessmentId, "assessmentId cannot be null");

      return retryExecutor.execute(
          () -> {
            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/assessments/" + assessmentId + "/submit", null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.submitAssessment");
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

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("approved_by", request.getApprovedBy());
            if (request.getComments() != null) {
              body.put("comments", request.getComments());
            }

            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/assessments/" + assessmentId + "/approve", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.approveAssessment");
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

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("rejected_by", request.getRejectedBy());
            body.put("reason", request.getReason());

            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/assessments/" + assessmentId + "/reject", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseAssessmentResponse(response);
            }
          },
          "masfeat.rejectAssessment");
    }

    /**
     * Gets the kill switch configuration for an AI system.
     *
     * @param systemId the system ID (string ID, not UUID)
     * @return the kill switch configuration
     */
    public KillSwitch getKillSwitch(String systemId) {
      Objects.requireNonNull(systemId, "systemId cannot be null");

      return retryExecutor.execute(
          () -> {
            Request httpRequest =
                buildOrchestratorRequest("GET", BASE_PATH + "/killswitch/" + systemId, null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseKillSwitchResponse(response);
            }
          },
          "masfeat.getKillSwitch");
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

      return retryExecutor.execute(
          () -> {
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
            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/killswitch/" + systemId + "/configure", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseKillSwitchResponse(response);
            }
          },
          "masfeat.configureKillSwitch");
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

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("reason", request.getReason());
            if (request.getTriggeredBy() != null) {
              body.put("triggered_by", request.getTriggeredBy());
            }

            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/killswitch/" + systemId + "/trigger", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseKillSwitchResponse(response);
            }
          },
          "masfeat.triggerKillSwitch");
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

      return retryExecutor.execute(
          () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("reason", request.getReason());
            if (request.getRestoredBy() != null) {
              body.put("restored_by", request.getRestoredBy());
            }

            Request httpRequest =
                buildOrchestratorRequest(
                    "POST", BASE_PATH + "/killswitch/" + systemId + "/restore", body);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseKillSwitchResponse(response);
            }
          },
          "masfeat.restoreKillSwitch");
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

      return retryExecutor.execute(
          () -> {
            String path = BASE_PATH + "/killswitch/" + systemId + "/history";
            if (limit > 0) {
              path += "?limit=" + limit;
            }

            Request httpRequest = buildOrchestratorRequest("GET", path, null);
            try (Response response = executeHttp(httpClient, httpRequest)) {
              return parseKillSwitchHistoryResponse(response);
            }
          },
          "masfeat.getKillSwitchHistory");
    }

    // ========================================================================
    // Response Parsing Helpers
    // ========================================================================

    @SuppressWarnings("deprecation") // populates deprecated fiction fields for tolerance (#3254)
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
      // #3254: owner_email is the real wire key. ownerEmail carries it under
      // its true name; businessOwner keeps receiving it as the historic
      // compatibility alias.
      system.setOwnerEmail(getTextOrNull(node, "owner_email"));
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

      // #3254: materiality_classification is the real wire key; read it
      // first, keep the legacy "materiality" spelling as a fallback.
      String materiality = getTextOrNull(node, "materiality_classification");
      if (materiality == null) {
        materiality = getTextOrNull(node, "materiality");
      }
      if (materiality != null) {
        try {
          system.setMaterialityClassification(MaterialityClassification.fromValue(materiality));
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
        system.setMetadata(
            objectMapper.convertValue(
                node.get("metadata"), new TypeReference<Map<String, Object>>() {}));
      }

      return system;
    }

    @SuppressWarnings("deprecation") // populates deprecated fiction fields for tolerance (#3254)
    private RegistrySummary parseSummaryResponse(Response response) throws IOException {
      handleErrorResponse(response);

      ResponseBody body = response.body();
      if (body == null) {
        throw new AxonFlowException("Empty response body", response.code(), null);
      }

      String json = body.string();
      JsonNode node = objectMapper.readTree(json);

      RegistrySummary summary = new RegistrySummary();
      summary.setOrgId(getTextOrNull(node, "org_id"));
      summary.setTotalSystems(getIntOrZero(node, "total_systems"));
      summary.setActiveSystems(getIntOrZero(node, "active_systems"));

      // #3254: the server's RegistrySummary serves high_materiality /
      // medium_materiality / low_materiality (no _count suffix). Read the
      // real key first; fall back to the legacy _count spelling so a
      // hypothetical old payload still parses. The pre-#3254 parser read
      // medium/low ONLY under the _count fiction, so both were always 0
      // against a real server.
      summary.setHighMaterialityCount(
          intWithFallback(node, "high_materiality", "high_materiality_count"));
      summary.setMediumMaterialityCount(
          intWithFallback(node, "medium_materiality", "medium_materiality_count"));
      summary.setLowMaterialityCount(
          intWithFallback(node, "low_materiality", "low_materiality_count"));
      summary.setAssessmentsDue(getIntOrZero(node, "assessments_due"));
      summary.setKillSwitchesTriggered(getIntOrZero(node, "kill_switches_triggered"));

      if (node.has("by_use_case") && !node.get("by_use_case").isNull()) {
        summary.setByUseCase(
            objectMapper.convertValue(
                node.get("by_use_case"), new TypeReference<Map<String, Integer>>() {}));
      }

      if (node.has("by_status") && !node.get("by_status").isNull()) {
        summary.setByStatus(
            objectMapper.convertValue(
                node.get("by_status"), new TypeReference<Map<String, Integer>>() {}));
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
        assessment.setFairnessDetails(
            objectMapper.convertValue(
                node.get("fairness_details"), new TypeReference<Map<String, Object>>() {}));
      }
      if (node.has("ethics_details") && !node.get("ethics_details").isNull()) {
        assessment.setEthicsDetails(
            objectMapper.convertValue(
                node.get("ethics_details"), new TypeReference<Map<String, Object>>() {}));
      }
      if (node.has("accountability_details") && !node.get("accountability_details").isNull()) {
        assessment.setAccountabilityDetails(
            objectMapper.convertValue(
                node.get("accountability_details"), new TypeReference<Map<String, Object>>() {}));
      }
      if (node.has("transparency_details") && !node.get("transparency_details").isNull()) {
        assessment.setTransparencyDetails(
            objectMapper.convertValue(
                node.get("transparency_details"), new TypeReference<Map<String, Object>>() {}));
      }

      // Handle assessors
      if (node.has("assessors") && node.get("assessors").isArray()) {
        assessment.setAssessors(
            objectMapper.convertValue(node.get("assessors"), new TypeReference<List<String>>() {}));
      }

      // Handle recommendations
      if (node.has("recommendations") && node.get("recommendations").isArray()) {
        assessment.setRecommendations(
            objectMapper.convertValue(
                node.get("recommendations"), new TypeReference<List<String>>() {}));
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

      // #3254: the server serves trigger_reason; triggered_reason has never
      // been sent. Read the real key first, keep the legacy spelling as a
      // fallback for tolerance.
      String triggeredReason = getTextOrNull(node, "trigger_reason");
      if (triggeredReason == null) {
        triggeredReason = getTextOrNull(node, "triggered_reason");
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

    private List<KillSwitchEvent> parseKillSwitchHistoryResponse(Response response)
        throws IOException {
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
          event.setEventData(
              objectMapper.convertValue(
                  eventNode.get("event_data"), new TypeReference<Map<String, Object>>() {}));
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

    /**
     * Reads {@code primary} if present (even when 0), otherwise {@code fallback}, otherwise 0. Used
     * for #3254 real-key-first reads with legacy-spelling tolerance: a PRESENT primary key always
     * wins so a genuine 0 is never overridden by a stale fallback value.
     */
    private int intWithFallback(JsonNode node, String primary, String fallback) {
      if (node.has(primary) && !node.get(primary).isNull()) {
        return node.get(primary).asInt();
      }
      return getIntOrZero(node, fallback);
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

  /**
   * Releases this client's transport: shuts the dispatcher's executor, evicts the connection pool
   * and clears the cache.
   *
   * <p><b>Call this only on a client you constructed, never on one from {@link
   * #asUser(String)}.</b> A derived client SHARES the parent's dispatcher and pool by design, so
   * those two lines act on the parent's transport, not on a copy — closing a derived client
   * silently disables the parent and every sibling derived from it. A derived client holds nothing
   * of its own to release.
   */
  @Override
  public void close() {
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
    cache.clear();
    logger.info("AxonFlow client closed");
  }
}
