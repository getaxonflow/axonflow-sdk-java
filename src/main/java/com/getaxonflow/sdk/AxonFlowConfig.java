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

import com.getaxonflow.sdk.exceptions.ConfigurationException;
import com.getaxonflow.sdk.types.Mode;
import com.getaxonflow.sdk.util.CacheConfig;
import com.getaxonflow.sdk.util.RetryConfig;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuration for the AxonFlow client.
 *
 * <p>Use the builder to create a configuration:
 *
 * <pre>{@code
 * AxonFlowConfig config = AxonFlowConfig.builder()
 *     .endpoint("http://localhost:8080")
 *     .clientId("my-client")
 *     .clientSecret("my-secret")
 *     .build();
 * }</pre>
 *
 * <p>Configuration can also be loaded from environment variables:
 *
 * <pre>{@code
 * AxonFlowConfig config = AxonFlowConfig.fromEnvironment();
 * }</pre>
 */
public final class AxonFlowConfig {

  /** SDK version string, read from Maven pom.properties at runtime. */
  public static final String SDK_VERSION = detectSdkVersion();

  private static String detectSdkVersion() {
    // Try Maven-generated pom.properties (available in packaged JAR)
    try (InputStream is =
        AxonFlowConfig.class.getResourceAsStream(
            "/META-INF/maven/com.getaxonflow/axonflow-sdk/pom.properties")) {
      if (is != null) {
        Properties props = new Properties();
        props.load(is);
        String version = props.getProperty("version");
        if (version != null && !version.isEmpty()) {
          return version;
        }
      }
    } catch (Exception ignored) {
      // Fall through to manifest check
    }
    // Try JAR manifest Implementation-Version
    Package pkg = AxonFlowConfig.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null) {
      return pkg.getImplementationVersion();
    }
    // Fallback — "unknown" avoids hardcoded version drift
    return "unknown";
  }

  /** Default timeout for HTTP requests. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  /**
   * Default timeout for MAP (Multi-Agent Planning) requests.
   *
   * <p>MAP plans chain several LLM calls end-to-end and routinely run longer than a single request
   * — a 5-step plan at ~15s/step takes 60-75s by itself. 120s matches the TypeScript / Python / Go
   * SDK defaults. Override per-client via {@link Builder#mapTimeout(Duration)} or {@code
   * AXONFLOW_MAP_TIMEOUT_SECONDS}. The orchestrator's plan budget caps at {@code
   * AXONFLOW_MAP_MAX_TIMEOUT_SECONDS} (default 300s); the front-door ALB's {@code
   * idle_timeout.timeout_seconds} must be {@code >=} that cap or the connection is killed
   * mid-stream. Keep these three knobs moving together.
   */
  public static final Duration DEFAULT_MAP_TIMEOUT = Duration.ofSeconds(120);

  /** Default endpoint URL. */
  public static final String DEFAULT_ENDPOINT = "http://localhost:8080";

  /** Try mode endpoint URL. */
  public static final String TRY_ENDPOINT = "https://try.getaxonflow.com";

  private final String endpoint;
  private final String clientId;
  private final String clientSecret;

  /**
   * The per-user identity this client presents on the READ path, sent as the {@code X-User-Token}
   * header on every request bound for the configured endpoint.
   *
   * <p>{@code clientId}/{@code clientSecret} authenticate the ORGANIZATION; this authenticates the
   * PERSON. Since platform #2922 the role-scoped reads ({@code explainDecision}, {@code
   * listDecisions}, the audit reads) are answered from this identity: an enterprise stack scopes a
   * developer or viewer to their own rows, gives a tenant-wide role (admin / owner / policy_admin)
   * the whole tenant, and returns ZERO rows to a caller that presents no identity at all.
   *
   * <p>SETTING THIS AFFECTS MORE THAN READS. The header rides every request and the agent VALIDATES
   * it on every route it proxies, so a stale or rotated token turns {@code listConnectors}, {@code
   * installConnector} and policy CRUD into 401s rather than merely unscoping a read.
   */
  private final String userToken;

  private final Mode mode;
  private final Duration timeout;
  private final Duration mapTimeout;
  private final boolean debug;
  private final boolean insecureSkipVerify;
  private final RetryConfig retryConfig;
  private final CacheConfig cacheConfig;
  private final String userAgent;
  private final boolean tryMode;

  private AxonFlowConfig(Builder builder) {
    this.tryMode = "1".equals(System.getenv("AXONFLOW_TRY"));
    this.endpoint =
        this.tryMode
            ? normalizeUrl(TRY_ENDPOINT)
            : normalizeUrl(builder.endpoint != null ? builder.endpoint : DEFAULT_ENDPOINT);
    this.clientId = builder.clientId;
    this.clientSecret = builder.clientSecret;
    this.userToken = builder.userToken;
    this.mode = builder.mode != null ? builder.mode : Mode.PRODUCTION;
    this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
    this.mapTimeout = builder.mapTimeout != null ? builder.mapTimeout : DEFAULT_MAP_TIMEOUT;
    this.debug = builder.debug;
    this.insecureSkipVerify = builder.insecureSkipVerify;
    this.retryConfig = builder.retryConfig != null ? builder.retryConfig : RetryConfig.defaults();
    this.cacheConfig = builder.cacheConfig != null ? builder.cacheConfig : CacheConfig.defaults();
    this.userAgent =
        builder.userAgent != null ? builder.userAgent : "axonflow-sdk-java/" + SDK_VERSION;

    validate();
  }

  private void validate() {
    if (endpoint == null || endpoint.isEmpty()) {
      throw new ConfigurationException("endpoint is required", "endpoint");
    }
    if (tryMode && (clientId == null || clientId.isEmpty())) {
      throw new ConfigurationException(
          "clientId is required in try mode (AXONFLOW_TRY=1)", "clientId");
    }
    // Credentials are optional for community/self-hosted deployments
    // Enterprise features require credentials (validated at method call time)
  }

  /**
   * Checks if credentials are configured.
   *
   * <p>Returns true if clientId is set. clientSecret is optional for community mode but required
   * for enterprise.
   *
   * @return true if clientId is available
   */
  public boolean hasCredentials() {
    return clientId != null && !clientId.isEmpty();
  }

  private String normalizeUrl(String url) {
    if (url == null) return null;
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * Checks if the configured endpoint is localhost.
   *
   * @return true if connecting to localhost
   */
  public boolean isLocalhost() {
    return endpoint != null
        && (endpoint.contains("localhost")
            || endpoint.contains("127.0.0.1")
            || endpoint.contains("[::1]"));
  }

  /**
   * Creates a configuration from environment variables.
   *
   * <p>Supported environment variables:
   *
   * <ul>
   *   <li>AXONFLOW_AGENT_URL - The endpoint URL (kept for backwards compatibility)
   *   <li>AXONFLOW_CLIENT_ID - The client ID
   *   <li>AXONFLOW_CLIENT_SECRET - The client secret
   *   <li>AXONFLOW_MODE - Operating mode (production/sandbox)
   *   <li>AXONFLOW_TIMEOUT_SECONDS - Request timeout in seconds
   *   <li>AXONFLOW_MAP_TIMEOUT_SECONDS - MAP plan timeout in seconds (default 120)
   *   <li>AXONFLOW_DEBUG - Enable debug mode (true/false)
   * </ul>
   *
   * @return a new configuration based on environment variables
   */
  public static AxonFlowConfig fromEnvironment() {
    Builder builder = builder();

    // Keep AXONFLOW_AGENT_URL for backwards compatibility, map to endpoint
    String endpoint = System.getenv("AXONFLOW_AGENT_URL");
    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpoint(endpoint);
    }

    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    if (clientId != null && !clientId.isEmpty()) {
      builder.clientId(clientId);
    }

    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
    if (clientSecret != null && !clientSecret.isEmpty()) {
      builder.clientSecret(clientSecret);
    }

    String modeStr = System.getenv("AXONFLOW_MODE");
    if (modeStr != null && !modeStr.isEmpty()) {
      builder.mode(Mode.fromValue(modeStr));
    }

    String timeoutStr = System.getenv("AXONFLOW_TIMEOUT_SECONDS");
    if (timeoutStr != null && !timeoutStr.isEmpty()) {
      try {
        builder.timeout(Duration.ofSeconds(Long.parseLong(timeoutStr)));
      } catch (NumberFormatException e) {
        // Ignore invalid timeout, use default
      }
    }

    String mapTimeoutStr = System.getenv("AXONFLOW_MAP_TIMEOUT_SECONDS");
    if (mapTimeoutStr != null && !mapTimeoutStr.isEmpty()) {
      try {
        builder.mapTimeout(Duration.ofSeconds(Long.parseLong(mapTimeoutStr)));
      } catch (NumberFormatException e) {
        // Ignore invalid timeout, use default
      }
    }

    String debugStr = System.getenv("AXONFLOW_DEBUG");
    if ("true".equalsIgnoreCase(debugStr)) {
      builder.debug(true);
    }

    return builder.build();
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getClientId() {
    return clientId;
  }

  /**
   * The per-user identity for role-scoped reads, or {@code null}.
   *
   * @return the read-path identity token
   */
  public String getUserToken() {
    return userToken;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public Mode getMode() {
    return mode;
  }

  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Returns the timeout for MAP (Multi-Agent Planning) requests.
   *
   * <p>Applied per-call to plan-lifecycle methods: {@code generatePlan}, {@code executePlan},
   * {@code getPlan}, {@code updatePlan}, {@code cancelPlan}, and {@code resumePlan}. Defaults to
   * {@link #DEFAULT_MAP_TIMEOUT} (120s).
   *
   * @return the MAP request timeout
   */
  public Duration getMapTimeout() {
    return mapTimeout;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isInsecureSkipVerify() {
    return insecureSkipVerify;
  }

  public RetryConfig getRetryConfig() {
    return retryConfig;
  }

  public CacheConfig getCacheConfig() {
    return cacheConfig;
  }

  public String getUserAgent() {
    return userAgent;
  }

  /**
   * Returns the X-Axonflow-Client header value identifying this SDK + version.
   *
   * <p>Per ADR-050 §4, every governed request to the agent carries this header so the agent can
   * derive request scope (sdk) and validate it against the token's aud.scope via HasScope().
   * Sourced from the bundled {@link #SDK_VERSION}; there is intentionally no env / config override
   * (the consumer doesn't get to spoof its own client identity to the agent).
   *
   * @return the agent-parseable {@code "sdk-java/<semver>"} client header value
   */
  public String getClientHeader() {
    return "sdk-java/" + SDK_VERSION;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AxonFlowConfig that = (AxonFlowConfig) o;
    return debug == that.debug
        && insecureSkipVerify == that.insecureSkipVerify
        && Objects.equals(endpoint, that.endpoint)
        && Objects.equals(clientId, that.clientId)
        && mode == that.mode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(endpoint, clientId, mode, debug, insecureSkipVerify);
  }

  /**
   * A builder pre-populated with this config's values.
   *
   * <p>Every field is carried across, so a derivation cannot quietly lose a timeout, a proxy-facing
   * TLS setting or the retry policy. {@code AxonFlow.asUser} uses it to change exactly one thing —
   * the read-path identity — and nothing else.
   *
   * @return a builder seeded from this config
   */
  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.endpoint = this.endpoint;
    builder.clientId = this.clientId;
    builder.clientSecret = this.clientSecret;
    builder.userToken = this.userToken;
    builder.mode = this.mode;
    builder.timeout = this.timeout;
    builder.mapTimeout = this.mapTimeout;
    builder.debug = this.debug;
    builder.insecureSkipVerify = this.insecureSkipVerify;
    builder.retryConfig = this.retryConfig;
    builder.cacheConfig = this.cacheConfig;
    builder.userAgent = this.userAgent;
    return builder;
  }

  @Override
  public String toString() {
    return "AxonFlowConfig{"
        + "endpoint='"
        + endpoint
        + '\''
        + ", clientId='"
        + clientId
        + '\''
        + ", mode="
        + mode
        + ", timeout="
        + timeout
        + ", debug="
        + debug
        + '}';
  }

  /** Builder for AxonFlowConfig. */
  public static final class Builder {
    private String endpoint;
    private String clientId;
    private String clientSecret;
    private String userToken;
    private Mode mode;
    private Duration timeout;
    private Duration mapTimeout;
    private boolean debug;
    private boolean insecureSkipVerify;
    private RetryConfig retryConfig;
    private CacheConfig cacheConfig;
    private String userAgent;

    private Builder() {}

    /**
     * Sets the AxonFlow endpoint URL. All routes now go through a single endpoint (ADR-026 Single
     * Entry Point).
     *
     * @param endpoint the endpoint URL
     * @return this builder
     */
    public Builder endpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    /**
     * Sets the AxonFlow Agent URL.
     *
     * @deprecated Use {@link #endpoint(String)} instead. This method is kept for backwards
     *     compatibility.
     * @param agentUrl the Agent URL
     * @return this builder
     */
    @Deprecated
    public Builder agentUrl(String agentUrl) {
      this.endpoint = agentUrl;
      return this;
    }

    // Note: portalUrl() and orchestratorUrl() methods were removed in v2.0.0
    // All routes now go through a single endpoint (ADR-026 Single Entry Point)

    /**
     * Sets the client ID for authentication.
     *
     * @param clientId the client ID
     * @return this builder
     */
    public Builder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    /**
     * Sets the client secret for authentication.
     *
     * @param clientSecret the client secret
     * @return this builder
     */
    public Builder clientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    /**
     * Sets the per-user identity for the READ path, sent as {@code X-User-Token}.
     *
     * <p>{@code clientId}/{@code clientSecret} say which ORGANIZATION is asking; this says WHO.
     * Since platform #2922 {@code explainDecision}, {@code listDecisions} and the audit reads are
     * scoped to it — an enterprise stack returns ZERO rows to a caller that presents none, which
     * the SDK now reports as a {@code ReadScopeException} rather than as an empty result.
     *
     * <p>The value is a per-user JWT: minted by the customer portal's user-token API, or for local
     * testing by {@code scripts/generate-jwt.sh --kind user}. It is NOT the tenant JWT and not the
     * client secret. Community deployments are single-operator and ignore it.
     *
     * <p>Override per call with the {@code userToken} overload on a read, or derive a client bound
     * to one person with {@code client.asUser(token)}.
     *
     * @param userToken the per-user identity token
     * @return this builder
     */
    public Builder userToken(String userToken) {
      this.userToken = userToken;
      return this;
    }

    /**
     * Sets the operating mode.
     *
     * @param mode the mode (PRODUCTION or SANDBOX)
     * @return this builder
     */
    public Builder mode(Mode mode) {
      this.mode = mode;
      return this;
    }

    /**
     * Sets the request timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Sets the MAP (Multi-Agent Planning) request timeout.
     *
     * <p>Applied per-call to plan-lifecycle methods (generatePlan, executePlan, getPlan,
     * updatePlan, cancelPlan, resumePlan). Defaults to 120s, matching the TS / Python / Go SDKs.
     * Raise for long-running plans but keep the orchestrator ({@code
     * AXONFLOW_MAP_MAX_TIMEOUT_SECONDS}, default 300s) and front-door ALB ({@code
     * idle_timeout.timeout_seconds}, default 300s) tuned consistently or the connection is cut
     * mid-stream.
     *
     * @param mapTimeout the MAP timeout duration
     * @return this builder
     */
    public Builder mapTimeout(Duration mapTimeout) {
      this.mapTimeout = mapTimeout;
      return this;
    }

    /**
     * Enables debug mode for verbose logging.
     *
     * @param debug true to enable debug mode
     * @return this builder
     */
    public Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    /**
     * Requests that TLS certificate verification be skipped for outbound HTTPS calls.
     *
     * <p><strong>Security:</strong> Setting this flag is <em>not by itself</em> sufficient to
     * disable TLS verification. To actually disable verification, the environment variable {@code
     * AXONFLOW_INSECURE_TLS} must <strong>also</strong> be set to {@code "true"} or {@code "1"} in
     * the runtime environment. This double-gate is intentional defense-in-depth: a stray builder
     * call in application code cannot silently bypass certificate validation in production.
     *
     * <p>If the builder flag is set but the environment variable is not, the SDK will log a warning
     * at client construction time and keep TLS verification enabled.
     *
     * <p><strong>Use cases:</strong> local development against self-signed certificates only.
     * <strong>Never</strong> enable in production.
     *
     * @param insecureSkipVerify true to request that verification be skipped (also requires {@code
     *     AXONFLOW_INSECURE_TLS=true} environment variable)
     * @return this builder
     */
    public Builder insecureSkipVerify(boolean insecureSkipVerify) {
      this.insecureSkipVerify = insecureSkipVerify;
      return this;
    }

    /**
     * Sets the retry configuration.
     *
     * @param retryConfig the retry configuration
     * @return this builder
     */
    public Builder retryConfig(RetryConfig retryConfig) {
      this.retryConfig = retryConfig;
      return this;
    }

    /**
     * Sets the cache configuration.
     *
     * @param cacheConfig the cache configuration
     * @return this builder
     */
    public Builder cacheConfig(CacheConfig cacheConfig) {
      this.cacheConfig = cacheConfig;
      return this;
    }

    /**
     * Sets a custom user agent string.
     *
     * @param userAgent the user agent string
     * @return this builder
     */
    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Builds the configuration.
     *
     * @return a new AxonFlowConfig instance
     * @throws ConfigurationException if the configuration is invalid
     */
    public AxonFlowConfig build() {
      return new AxonFlowConfig(this);
    }
  }
}
