// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getaxonflow.sdk.AxonFlowConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fire-and-forget telemetry reporter that sends anonymous usage pings to the AxonFlow checkpoint
 * endpoint.
 *
 * <p>Telemetry is completely anonymous and contains no user data, only SDK version, runtime
 * environment, and deployment mode information.
 *
 * <p>{@code AXONFLOW_TELEMETRY=off} in the environment is the SOLE opt-out path as of v8.0. The
 * v7.x {@code telemetry(Boolean)} config-builder override has been removed; the previous silent
 * suppression of sandbox-mode pings has also been removed. Sandbox-mode pings now fire on the same
 * heartbeat schedule as production-mode pings, tagged {@code stream="sandbox"} in the payload so
 * analytics can distinguish dev/test pings from production heartbeat (the wire-side allowlist is
 * enforced by the checkpoint service — see {@code IsValidIncomingStream}).
 */
public class TelemetryReporter {

  private static final Logger logger = LoggerFactory.getLogger(TelemetryReporter.class);

  static final String DEFAULT_ENDPOINT = "https://checkpoint.getaxonflow.com/v1/ping";
  private static final int TIMEOUT_SECONDS = 3;

  /**
   * Minimum remaining HTTP budget (milliseconds). Below this, skip the operation rather than issue
   * a request that is almost guaranteed to time out before any useful work completes. Keeps the
   * telemetry path from making "essentially zero budget" calls when the shared deadline is nearly
   * spent.
   */
  private static final long MIN_BUDGET_MS = 100L;

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  /**
   * Bounds every value this SDK puts on the telemetry wire that it did not author itself — every
   * string promoted out of a {@code /health} response, and every adapter name handed to {@link
   * #registerAdapter}.
   *
   * <p>WHY A DROP AND NOT A TRUNCATION. The checkpoint refuses a request body over 64 KiB. A single
   * 70 KB value from a hostile or broken {@code /health} therefore produces a ping rejected WHOLE —
   * the version, the tier, the org id, every dimension lost, not just the oversized one — and
   * because the stamp is only written on a 2xx, the SDK retries that same doomed request at every
   * gate run for as long as {@code /health} keeps answering that way. Dropping the offending value
   * alone keeps the ping under the limit and preserves every other dimension. It is dropped rather
   * than truncated because a truncated value is a value nobody reported.
   *
   * <p>BYTES, NOT CHARACTERS — and in Java that has to be written out, because {@code
   * String.length()} counts UTF-16 CODE UNITS. Every check against this bound uses {@link
   * #byteLength}. The bound is bytes because the thing being bounded, the serialized request body,
   * is bytes.
   */
  static final int MAX_RELAYED_VALUE_BYTES = 64;

  /**
   * Bounds on the {@code features} array itself, mirroring the receiver's own {@code MaxFeatures} /
   * {@code MaxFeatureBytes}.
   *
   * <p>The entry cap is live: register 33 adapters and the 33rd does not reach the wire. The byte
   * cap is a BACKSTOP today's only producer cannot trigger — {@link #registerAdapter} already
   * refuses a name over {@link #MAX_RELAYED_VALUE_BYTES}, so the longest entry it can emit is
   * {@code "adapter:".length() + 64 == 72}. It is tested directly on {@link #boundFeatures}.
   */
  static final int MAX_FEATURES = 32;

  static final int MAX_FEATURE_BYTES = 128;

  /**
   * Marks a {@code features[]} entry as an adapter identifier. The vocabulary is SERVER-DEFINED
   * (checkpoint-service {@code FeatureAdapterPrefix}) and is not this SDK's to extend.
   */
  static final String FEATURE_ADAPTER_PREFIX = "adapter:";

  /**
   * Adapter names declared by {@link #registerAdapter}.
   *
   * <p>A set, so a framework that registers on every wrapper construction — the ordinary case for
   * an adapter whose constructor runs per request — declares itself once on the wire rather than N
   * times. Concurrent because registration can race a heartbeat thread reading it.
   */
  private static final java.util.Set<String> ADAPTER_REGISTRY =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  /** Length of {@code value} in UTF-8 BYTES, never in UTF-16 code units. */
  static int byteLength(String value) {
    return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  /**
   * Declares that a framework adapter is driving this SDK, so the next telemetry heartbeat carries
   * {@code adapter:<name>} in its {@code features} array.
   *
   * <p>A framework adapter (LangChain, LangGraph, …) wrapping this SDK is indistinguishable from
   * bare SDK use on every other telemetry dimension — same sdk, same sdk_version, same endpoint.
   * This is the one call that makes the difference visible, and it is adoption signal only.
   *
   * <p>IT ADDS NO REQUEST. The name rides the {@code features} array of the heartbeat that already
   * fires; there is no second ping and no new configuration surface. Calling it sends nothing.
   *
   * <p>Call it before your first API call for day-one attribution: the heartbeat fires on the
   * client's FIRST OUTBOUND REQUEST, not at construction. The SDK's own {@code LangGraphAdapter}
   * registers from its constructor, so simply using it is enough.
   *
   * <p>Idempotent and thread-safe.
   *
   * <p>THE NAME IS NOT VALIDATED AGAINST A LIST, DELIBERATELY. The canonical vocabulary lives on
   * the receiver ({@code NormalizeAdapterFeature}), which folds an unrecognised name into {@code
   * adapter:unknown} at READ time while keeping the raw name on the row. An allowlist here would be
   * a second vocabulary that drifts: a name this SDK build predates would be dropped at the client
   * instead of arriving and rendering as "someone is using an adapter we do not know about".
   *
   * <p>So the only transformations are the two the receiver also applies: trim, and lowercase. A
   * name empty after trimming, a null, and a name over {@link #MAX_RELAYED_VALUE_BYTES} are refused
   * SILENTLY — this is a fire-and-forget telemetry declaration, and throwing would invite a caller
   * to fail their own startup over an analytics detail.
   *
   * @param name the adapter's own name, e.g. {@code "langgraph"}
   */
  public static void registerAdapter(String name) {
    if (name == null) {
      return;
    }
    String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.isEmpty() || byteLength(normalized) > MAX_RELAYED_VALUE_BYTES) {
      return;
    }
    ADAPTER_REGISTRY.add(normalized);
  }

  /**
   * Applies the receiver's array bounds: at most {@link #MAX_FEATURES} entries, none over {@link
   * #MAX_FEATURE_BYTES} bytes.
   *
   * <p>An over-long entry is DROPPED rather than truncated, deliberately differing from the
   * receiver's own {@code BoundFeatures}. The receiver truncates because it is defending storage
   * against arbitrary clients; here the entry is something this process declared about itself, and
   * a truncated adapter name is a name nothing is running.
   */
  static java.util.List<String> boundFeatures(java.util.List<String> features) {
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String f : features) {
      if (byteLength(f) > MAX_FEATURE_BYTES) {
        continue;
      }
      out.add(f);
      if (out.size() == MAX_FEATURES) {
        break;
      }
    }
    return out;
  }

  /**
   * Renders the registry as the {@code features} array for one ping.
   *
   * <p>Sorted so the wire is deterministic: two processes that registered the same adapters in a
   * different order produce the same array, which is what makes "which 32 survive" a defined answer
   * rather than a set-iteration accident.
   */
  static java.util.List<String> registeredFeatures() {
    java.util.List<String> names = new java.util.ArrayList<>(ADAPTER_REGISTRY);
    java.util.Collections.sort(names);
    java.util.List<String> entries = new java.util.ArrayList<>(names.size());
    for (String n : names) {
      entries.add(FEATURE_ADAPTER_PREFIX + n);
    }
    return boundFeatures(entries);
  }

  /** Test-only: empty the registry and return what was there, so a caller can restore it. */
  static java.util.List<String> resetAdapterRegistryForTest() {
    java.util.List<String> previous = new java.util.ArrayList<>(ADAPTER_REGISTRY);
    ADAPTER_REGISTRY.clear();
    return previous;
  }

  /** Test-only: restore a registry saved by {@link #resetAdapterRegistryForTest}. */
  static void restoreAdapterRegistryForTest(java.util.List<String> previous) {
    ADAPTER_REGISTRY.clear();
    ADAPTER_REGISTRY.addAll(previous);
  }

  /**
   * Sends a telemetry ping synchronously (blocks until the round-trip completes).
   *
   * @param mode the deployment mode (e.g. "production", "sandbox")
   * @param sdkEndpoint the configured SDK endpoint, used to detect platform version via /health
   * @param debug whether debug logging is enabled
   */
  public static void sendPing(String mode, String sdkEndpoint, boolean debug) {
    sendPing(
        mode,
        sdkEndpoint,
        debug,
        System.getenv("AXONFLOW_TELEMETRY"),
        System.getenv("AXONFLOW_CHECKPOINT_URL"));
  }

  /** Package-private overload for testability, accepting env var values as parameters. */
  static void sendPing(
      String mode,
      String sdkEndpoint,
      boolean debug,
      String axonflowTelemetry,
      String checkpointUrl) {
    if (!isEnabled(axonflowTelemetry)) {
      if (debug) {
        logger.debug("Telemetry is disabled, skipping ping");
      }
      return;
    }
    sendPingNow(mode, sdkEndpoint, debug, checkpointUrl);
  }

  /**
   * Send a single telemetry ping and return whether it landed.
   *
   * <p>Returns {@code true} only when the POST received a 2xx response. Network failures, timeouts,
   * and non-2xx responses all return {@code false}. Used by the heartbeat orchestrator (see {@link
   * HeartbeatState}) where the boolean drives stamp-on-DELIVERY semantics: only successful POSTs
   * advance the stamp file.
   *
   * <p>The caller is responsible for the gating decision — this method does NOT consult
   * AXONFLOW_TELEMETRY, isEnabled, the stamp file, or any rate-limit state. That separation lets
   * the heartbeat module make the gating decision once and only update the stamp on success.
   */
  public static boolean sendPingNow(
      String mode, String sdkEndpoint, boolean debug, String checkpointUrl) {
    logger.info(
        "AxonFlow: telemetry enabled. Opt out: AXONFLOW_TELEMETRY=off | https://docs.getaxonflow.com/docs/telemetry");

    String endpoint =
        (checkpointUrl != null && !checkpointUrl.isEmpty()) ? checkpointUrl : DEFAULT_ENDPOINT;

    String endpointType = classifyEndpoint(sdkEndpoint);
    // v1 telemetry-schema: deployment_mode now derives from endpoint host
    // (axonflow-enterprise#2008). config.Mode no longer drives this dimension.
    String deploymentMode = classifyDeploymentMode(sdkEndpoint);

    try {
      long deadlineMs = System.nanoTime() / 1_000_000L + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);

      long healthBudgetMs =
          Math.min(
              TimeUnit.SECONDS.toMillis(1),
              Math.max(0L, deadlineMs - System.nanoTime() / 1_000_000L));
      // One /health fetch supplies both platform_version and license_tier.
      // Re-read on every heartbeat rather than cached for the process
      // lifetime: a licence can be applied to, or expire on, a running
      // platform, and a cached tier would keep reporting the pre-change
      // tier for as long as the client lives.
      PlatformHealthProbe probe =
          (sdkEndpoint != null && !sdkEndpoint.isEmpty() && healthBudgetMs > MIN_BUDGET_MS)
              ? probePlatformHealth(sdkEndpoint, healthBudgetMs)
              : EMPTY_HEALTH_PROBE;

      String payload =
          buildPayload(
              mode,
              probe.platformVersion,
              endpointType,
              deploymentMode,
              probe.licenseTier,
              probe.edition,
              probe.platformDeploymentMode);

      long postBudgetMs = Math.max(0L, deadlineMs - System.nanoTime() / 1_000_000L);
      if (postBudgetMs < MIN_BUDGET_MS) {
        return false;
      }

      // NO REDIRECTS, AND ON THIS LEG IT IS A CORRECTNESS BUG RATHER THAN A PRIVACY ONE.
      //
      // OkHttp follows redirects by DEFAULT, and it does not re-POST across a 301/302/303:
      // it converts the request to a bodyless GET. So a redirect on the checkpoint POST
      // produces a 200 for a request that carried NO PAYLOAD, isSuccessful() reads that as
      // delivery, and the caller advances the 7-day stamp — leaving the installation silent
      // for a week on a ping that was never sent. A 200 meaning "we delivered nothing" is
      // indistinguishable from success at every layer above.
      //
      // followSslRedirects is set too: it governs http<->https hops specifically, and
      // leaving it at its default would let exactly the scheme-crossing redirect through.
      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .readTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .writeTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .followRedirects(false)
              .followSslRedirects(false)
              .build();

      RequestBody body = RequestBody.create(payload, JSON);
      Request request = new Request.Builder().url(endpoint).post(body).build();

      try (Response response = client.newCall(request).execute()) {
        if (isRedirect(response.code())) {
          // Named separately from an ordinary non-2xx so a refused redirect is
          // OBSERVABLE. It is the one failure on this path that would otherwise look
          // like success. The Location value is deliberately NOT logged: it is
          // remote-controlled text.
          logger.debug(
              "Telemetry: checkpoint answered {} (a redirect); refused, ping NOT delivered"
                  + " and the stamp will not advance",
              response.code());
        } else if (debug) {
          logger.debug("Telemetry ping sent, status={}", response.code());
        }
        return response.isSuccessful();
      }
    } catch (Exception e) {
      // Silent failure - telemetry must never disrupt SDK operation
      if (debug) {
        logger.debug("Telemetry ping failed (silent): {}", e.getMessage());
      }
      return false;
    }
  }

  /**
   * Determines whether telemetry is enabled.
   *
   * <p>{@code AXONFLOW_TELEMETRY=off} in the environment is the SOLE opt-out path as of v8.0.
   * Telemetry is otherwise ON by default, regardless of mode (sandbox / production / anything
   * else). Sandbox-mode pings are tagged {@code stream="sandbox"} in the payload so analytics can
   * still distinguish them — see {@link #buildPayload}.
   *
   * <p>Historical context: v7.x supported a {@code Boolean configOverride} parameter and a {@code
   * mode != "sandbox"} default-suppression rule. Both were removed in v8.0 to leave a single,
   * ops-controlled opt-out lever and avoid silent suppression that masks real adoption signal. See
   * CHANGELOG v8.0.0.
   *
   * <p>{@code DO_NOT_TRACK} is intentionally NOT honored. It is commonly inherited from host tools
   * and developer environments (CLIs like Codex and Claude Code inject it unconditionally), which
   * makes it an unreliable expression of user intent for AxonFlow telemetry.
   *
   * @param axonflowTelemetry value of {@code AXONFLOW_TELEMETRY} env var (null = unset)
   * @return true if telemetry should be sent
   */
  /**
   * True for any 3xx.
   *
   * <p>Distinguished from an ordinary non-2xx so a REFUSED REDIRECT is observable: it is the one
   * failure on the telemetry path that would otherwise look like success.
   */
  static boolean isRedirect(int code) {
    return code >= 300 && code < 400;
  }

  public static boolean isEnabled(String axonflowTelemetry) {
    // AXONFLOW_TELEMETRY=off is the SOLE opt-out path.
    return !(axonflowTelemetry != null && "off".equalsIgnoreCase(axonflowTelemetry.trim()));
  }

  /**
   * The single definition of "the platform told us this".
   *
   * <p>A value counts as learned only when it is a NON-EMPTY string. Used by both {@link
   * #probePlatformHealth} (deciding what to promote out of the {@code /health} body) and {@link
   * #buildPayload(String, String, String, String, String)} (deciding what reaches the wire), so the
   * omit-vs-populate rule cannot drift between the two levels.
   */
  static boolean isLearned(String value) {
    return value != null && !value.isEmpty();
  }

  /** Builds the JSON payload for the telemetry ping. */
  static String buildPayload(String mode, String platformVersion) {
    return buildPayload(mode, platformVersion, EndpointType.UNKNOWN, DeploymentMode.UNKNOWN);
  }

  /** Builds the JSON payload with an explicit endpoint_type classification. */
  static String buildPayload(String mode, String platformVersion, String endpointType) {
    return buildPayload(mode, platformVersion, endpointType, DeploymentMode.UNKNOWN);
  }

  /**
   * Builds the JSON payload with explicit endpoint_type + deployment_mode classifications (v1
   * telemetry-schema, axonflow-enterprise#2008).
   */
  static String buildPayload(
      String mode, String platformVersion, String endpointType, String deploymentMode) {
    return buildPayload(mode, platformVersion, endpointType, deploymentMode, null);
  }

  /**
   * Builds the JSON payload including the platform's licence tier (#3619).
   *
   * <p>{@code licenseTier} is the tier the connected platform reported on its own {@code /health}
   * response — {@code "community"}, {@code "evaluation"}, {@code "Enterprise"}, the csaas {@code
   * "Plus"} alias for EnterprisePlus, or the transient {@code "starting"}. Coarse adoption signal
   * only: no licence key, no expiry, no seat count, no customer name.
   *
   * <p>THREE SIMILARLY-NAMED CONCEPTS LIVE NEARBY. Do not merge them:
   *
   * <ol>
   *   <li>{@code deployment_mode} — SDK-derived TOPOLOGY: {@code self_hosted | community_saas |
   *       unknown}, classified from the endpoint URL. Says WHERE the platform runs.
   *   <li>The platform's own {@code DEPLOYMENT_MODE} env var — a server-side setting deciding which
   *       schema/tables the binary uses. Never read by this SDK and never sent here.
   *   <li>{@code license_tier} — what the platform REPORTED about its own licensing, for adoption
   *       analytics.
   * </ol>
   *
   * <p>ITEM 3 IS NOT AN ENTITLEMENT FACT. This SDK relays whatever {@code /health} returned, and
   * the receiver cannot verify the relay: whoever operates the endpoint the client was pointed at
   * controls the value completely. It must never gate entitlement, unlock a feature, or enter any
   * authorization or billing decision. See axonflow-enterprise#3619.
   *
   * <p>A community-mode binary can run on any topology and vice versa, so neither field is
   * derivable from the other.
   *
   * <p>The tier is sent verbatim. Casing and alias folding is the receiver's job
   * (checkpoint-service {@code NormalizeLicenseTier}) and is deliberately NOT duplicated here — a
   * client that folded locally would silently mask a tier this SDK build predates.
   *
   * <p>A {@code null} tier OMITS the key entirely, which is what "not learned" means on this wire.
   * Absent must never become a known value: emitting {@code "community"} for a platform we could
   * not reach would be a false claim about a customer's deployment. Note this differs from {@code
   * platform_version}, which is written as an explicit JSON null — that is its long-standing wire
   * shape and is left unchanged.
   */
  static String buildPayload(
      String mode,
      String platformVersion,
      String endpointType,
      String deploymentMode,
      String licenseTier) {
    return buildPayload(
        mode, platformVersion, endpointType, deploymentMode, licenseTier, null, null);
  }

  /** Builds the payload including the platform-identity members (#3660). */
  static String buildPayload(
      String mode,
      String platformVersion,
      String endpointType,
      String deploymentMode,
      String licenseTier,
      String edition,
      String platformDeploymentMode) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = mapper.createObjectNode();
      // v1 schema discriminator. Always "sdk" for this package.
      root.put("telemetry_type", "sdk");
      root.put("sdk", "java");
      root.put("sdk_version", AxonFlowConfig.SDK_VERSION);
      if (platformVersion != null) {
        root.put("platform_version", platformVersion);
      } else {
        root.putNull("platform_version");
      }
      root.put("os", normalizeOS(System.getProperty("os.name")));
      root.put("arch", normalizeArch(System.getProperty("os.arch")));
      root.put("runtime_version", System.getProperty("java.version"));
      // v1 schema deployment_mode allowlist: self_hosted | community_saas | unknown.
      // The prior config.Mode-based dimension is removed — deployment_mode now
      // reflects deployment topology only (see classifyDeploymentMode).
      root.put("deployment_mode", deploymentMode);
      root.put("endpoint_type", endpointType);

      // The adapter registry is the ONLY producer of this array. Read here rather than
      // snapshotted at class-init so an adapter that registers after the first client is
      // built still reaches the next heartbeat.
      ArrayNode features = mapper.createArrayNode();
      for (String feature : registeredFeatures()) {
        features.add(feature);
      }
      root.set("features", features);

      root.put("instance_id", UUID.randomUUID().toString());

      // Stream classifier: sandbox-mode clients self-tag so analytics can distinguish dev/test
      // pings from production. Production-mode (and other modes) omit the field entirely so the
      // server defaults to "heartbeat" — preserving byte-identical wire shape relative to v7.x
      // for the production-mode case. See CHANGELOG v8.0.0 and checkpoint-service
      // IsValidIncomingStream.
      if ("sandbox".equals(mode)) {
        root.put("stream", "sandbox");
      }

      // v9.1 deployment-organization identifier (#2277). Two sources, precedence order:
      // ORG_ID env (operator-supplied on self-hosted, or cs_<uuid> on Community SaaS) or
      // the "local-dev-org" sentinel. Always emitted. See axonflow-landing/content/privacy.html
      // for the customer-facing commitment that covers this field.
      root.put("org_id", telemetryOrgId());

      // Key written ONLY when the tier was learned. putNull would serialize as
      // JSON null, which is a claim ("the tier is nothing") rather than an
      // omission ("we do not know the tier"). See this method's javadoc.
      // isLearned, not a bare null check: this method is package-visible, so a
      // caller can hand it "" directly. A null-only check would write a
      // meaningless "license_tier":"" — the same rule the probe applies must
      // apply here, which is why both call one predicate.
      if (isLearned(licenseTier)) {
        root.put("license_tier", licenseTier);
      }
      // Relayed verbatim, omitted when not learned. NOTE that /health's
      // `deployment_mode` member lands on `platform_deployment_mode`, NOT on
      // `deployment_mode` above, which is the topology this SDK derived from its own
      // endpoint URL.
      if (isLearned(edition)) {
        root.put("edition", edition);
      }
      if (isLearned(platformDeploymentMode)) {
        root.put("platform_deployment_mode", platformDeploymentMode);
      }

      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      // Fallback minimal payload
      return "{\"sdk\":\"java\",\"sdk_version\":\"" + AxonFlowConfig.SDK_VERSION + "\"}";
    }
  }

  /**
   * Sentinel emitted on the telemetry wire when {@code ORG_ID} is unset — the default-config
   * Community-mode developer case. See #2277.
   */
  public static final String ORG_ID_LOCAL_DEV_SENTINEL = "local-dev-org";

  /**
   * Returns the {@code org_id} value to emit on the next telemetry ping. Reads {@code ORG_ID} from
   * the environment (the operator's explicit configuration for self-hosted deployments, or the
   * {@code cs_<uuid>} tenant identifier on Community SaaS) and falls back to {@link
   * #ORG_ID_LOCAL_DEV_SENTINEL} when unset. Always returns a non-empty string. See #2277.
   */
  static String telemetryOrgId() {
    String value = System.getenv("ORG_ID");
    if (value == null || value.isEmpty()) {
      return ORG_ID_LOCAL_DEV_SENTINEL;
    }
    return value;
  }

  /**
   * Endpoint type classifications for telemetry. See issue #1525.
   *
   * <p>The raw URL is never sent to the checkpoint service — only the classification.
   *
   * <p>As of v8.0 the legacy {@code COMMUNITY_SAAS} value is removed — deployment topology lives on
   * {@link DeploymentMode} per the v1 schema (axonflow-enterprise#2008).
   */
  public static final class EndpointType {
    public static final String LOCALHOST = "localhost";
    public static final String PRIVATE_NETWORK = "private_network";
    public static final String REMOTE = "remote";
    public static final String UNKNOWN = "unknown";

    private EndpointType() {}
  }

  /**
   * Deployment-mode classifications for telemetry (v1 schema, axonflow-enterprise#2008). Reflects
   * deployment topology — distinct from the endpoint reachability classification on {@link
   * EndpointType}.
   */
  public static final class DeploymentMode {
    public static final String SELF_HOSTED = "self_hosted";
    public static final String COMMUNITY_SAAS = "community_saas";
    public static final String UNKNOWN = "unknown";

    private DeploymentMode() {}
  }

  /**
   * Classifies the configured AxonFlow endpoint into the v1 deployment-mode allowlist ({@code
   * self_hosted | community_saas | unknown}). Community-SaaS detection fires on either an {@code
   * *.try.getaxonflow.com} host or {@code AXONFLOW_TRY=1} (the explicit override path for tenants
   * behind a custom hostname proxying try.getaxonflow.com). Empty/unparseable endpoint resolves to
   * {@code unknown}.
   */
  public static String classifyDeploymentMode(String url) {
    if ("1".equals(System.getenv("AXONFLOW_TRY"))) return DeploymentMode.COMMUNITY_SAAS;
    if (url == null || url.isEmpty()) return DeploymentMode.UNKNOWN;
    String host;
    try {
      URI u = new URI(url);
      host = u.getHost();
      if (host == null || host.isEmpty()) return DeploymentMode.UNKNOWN;
    } catch (URISyntaxException e) {
      return DeploymentMode.UNKNOWN;
    }
    host = host.toLowerCase();
    if ("try.getaxonflow.com".equals(host) || host.endsWith(".try.getaxonflow.com")) {
      return DeploymentMode.COMMUNITY_SAAS;
    }
    return DeploymentMode.SELF_HOSTED;
  }

  private static final Pattern IPV4_PATTERN =
      Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

  /**
   * Classifies the configured AxonFlow endpoint URL for analytics (#1525).
   *
   * <p>Returns one of {@link EndpointType#LOCALHOST}, {@link EndpointType#PRIVATE_NETWORK}, {@link
   * EndpointType#REMOTE}, or {@link EndpointType#UNKNOWN}.
   *
   * <p>The raw URL is never sent — only the classification.
   */
  public static String classifyEndpoint(String url) {
    if (url == null || url.isEmpty()) {
      return EndpointType.UNKNOWN;
    }
    String host;
    try {
      URI u = new URI(url);
      host = u.getHost();
      if (host == null || host.isEmpty()) {
        return EndpointType.UNKNOWN;
      }
    } catch (URISyntaxException e) {
      return EndpointType.UNKNOWN;
    }
    host = host.toLowerCase();

    // Strip IPv6 brackets if present.
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }

    if ("localhost".equals(host) || "0.0.0.0".equals(host) || host.endsWith(".localhost")) {
      return EndpointType.LOCALHOST;
    }

    if (host.endsWith(".local")
        || host.endsWith(".internal")
        || host.endsWith(".lan")
        || host.endsWith(".intranet")) {
      return EndpointType.PRIVATE_NETWORK;
    }

    // IPv4 classification.
    Matcher m = IPV4_PATTERN.matcher(host);
    if (m.matches()) {
      int a = Integer.parseInt(m.group(1));
      int b = Integer.parseInt(m.group(2));
      if (a == 127) return EndpointType.LOCALHOST;
      if (a == 10) return EndpointType.PRIVATE_NETWORK;
      if (a == 192 && b == 168) return EndpointType.PRIVATE_NETWORK;
      if (a == 172 && b >= 16 && b <= 31) return EndpointType.PRIVATE_NETWORK;
      if (a == 169 && b == 254) return EndpointType.PRIVATE_NETWORK;
      return EndpointType.REMOTE;
    }

    // IPv6 classification.
    //
    // v5.3.0 fix (review finding P3): previously only the literal "::1"
    // was recognized; ULA, link-local, and expanded loopback forms fell
    // through to REMOTE. Python and Go SDKs classify them correctly via
    // stdlib helpers — this hand-rolled version matches that behavior.
    if (host.indexOf(':') >= 0) {
      String expanded = expandIPv6(host);
      if ("0000:0000:0000:0000:0000:0000:0000:0001".equals(expanded)) {
        return EndpointType.LOCALHOST; // ::1 and all equivalent forms
      }
      if ("0000:0000:0000:0000:0000:0000:0000:0000".equals(expanded)) {
        return EndpointType.LOCALHOST; // :: listen-all (symmetric with 0.0.0.0)
      }
      if (expanded.length() >= 4) {
        String firstHextet = expanded.substring(0, 4);
        // ULA fc00::/7 — first hex pair is fc or fd
        if (firstHextet.startsWith("fc") || firstHextet.startsWith("fd")) {
          return EndpointType.PRIVATE_NETWORK;
        }
        // Link-local fe80::/10 — first hextet in [fe80..febf]
        if (firstHextet.compareTo("fe80") >= 0 && firstHextet.compareTo("febf") <= 0) {
          return EndpointType.PRIVATE_NETWORK;
        }
      }
      return EndpointType.REMOTE;
    }

    // Public hostname (not an IP, not a known private suffix).
    return EndpointType.REMOTE;
  }

  /**
   * Expand an IPv6 address to its full 8-hextet form with every hextet zero-padded to 4 hex digits.
   * Returns the input unchanged on parse failure.
   *
   * <p>Examples:
   *
   * <pre>
   *   ::1      → 0000:0000:0000:0000:0000:0000:0000:0001
   *   fd00::1  → fd00:0000:0000:0000:0000:0000:0000:0001
   *   fe80::a  → fe80:0000:0000:0000:0000:0000:0000:000a
   * </pre>
   *
   * <p>This is NOT a general-purpose IPv6 parser — it assumes the input came from URI.getHost()
   * after brackets are stripped.
   */
  static String expandIPv6(String addr) {
    String[] head;
    String[] tail;
    int doubleColon = addr.indexOf("::");
    if (doubleColon >= 0) {
      String headStr = addr.substring(0, doubleColon);
      String tailStr = addr.substring(doubleColon + 2);
      if (headStr.indexOf("::") >= 0 || tailStr.indexOf("::") >= 0) {
        return addr; // more than one "::" — invalid
      }
      head = headStr.isEmpty() ? new String[0] : headStr.split(":");
      tail = tailStr.isEmpty() ? new String[0] : tailStr.split(":");
    } else {
      head = addr.split(":");
      tail = new String[0];
    }
    int missing = 8 - head.length - tail.length;
    if (missing < 0) {
      return addr;
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String h : head) {
      if (!first) sb.append(':');
      sb.append(padHextet(h));
      first = false;
    }
    for (int i = 0; i < missing; i++) {
      if (!first) sb.append(':');
      sb.append("0000");
      first = false;
    }
    for (String h : tail) {
      if (!first) sb.append(':');
      sb.append(padHextet(h));
      first = false;
    }
    String result = sb.toString();
    // Must end up with exactly 8 hextets (7 colons).
    int colonCount = 0;
    for (int i = 0; i < result.length(); i++) {
      if (result.charAt(i) == ':') colonCount++;
    }
    if (colonCount != 7) {
      return addr;
    }
    return result;
  }

  private static String padHextet(String h) {
    if (h.length() >= 4) return h;
    StringBuilder sb = new StringBuilder(4);
    for (int i = h.length(); i < 4; i++) {
      sb.append('0');
    }
    sb.append(h);
    return sb.toString();
  }

  /**
   * Detect platform version by calling the agent's /health endpoint. Returns null on any failure.
   *
   * <p>The {@code budgetMs} parameter is derived from the shared telemetry deadline so the health
   * probe and the checkpoint POST don't stack into a larger combined budget. See
   * axonflow-enterprise#1706.
   */
  static String detectPlatformVersion(String sdkEndpoint, long budgetMs) {
    return probePlatformHealth(sdkEndpoint, budgetMs).platformVersion;
  }

  /**
   * What a single {@code /health} fetch established.
   *
   * <p>Each field is INDEPENDENT: a response carrying one but not the other yields a
   * partially-populated result rather than discarding both. A {@code null} field means "not
   * learned" and is omitted from the wire — it never degrades to a default. See {@link
   * #buildPayload(String, String, String, String, String)}.
   */
  static final class PlatformHealthProbe {
    final String platformVersion;
    final String licenseTier;

    /** {@code /health} -> {@code edition}: the BUILD the platform is running. */
    final String edition;

    /**
     * {@code /health} -> {@code deployment_mode}, relayed as {@code platform_deployment_mode}.
     *
     * <p>READ THE NAMES CAREFULLY. The {@code /health} member is called {@code deployment_mode}
     * because there the platform describes ITSELF. On the ping, {@code deployment_mode} already
     * means the TOPOLOGY this SDK derives from the endpoint URL. Mapping one onto the other would
     * overwrite a value every existing dashboard reads.
     */
    final String platformDeploymentMode;

    PlatformHealthProbe(
        String platformVersion, String licenseTier, String edition, String platformDeploymentMode) {
      this.platformVersion = platformVersion;
      this.licenseTier = licenseTier;
      this.edition = edition;
      this.platformDeploymentMode = platformDeploymentMode;
    }
  }

  private static final PlatformHealthProbe EMPTY_HEALTH_PROBE =
      new PlatformHealthProbe(null, null, null, null);

  /**
   * Promotes one {@code /health} member to a relayable value, or {@code null}.
   *
   * <p>Learned only when the member is present, is a JSON STRING, is non-empty, and is within
   * {@link #MAX_RELAYED_VALUE_BYTES}. A non-string is refused rather than coerced: {@code asText()}
   * would turn {@code "tier": 42} into {@code "42"} and land it in the receiver's unknown bucket as
   * though the platform had reported a tier.
   *
   * <p>One helper rather than four copies of the same conditions — a bound applied to three of four
   * fields is the shape that gets found in production by the field it was not applied to.
   */
  private static String learned(JsonNode root, String key) {
    JsonNode node = root.get(key);
    if (node == null || !node.isTextual()) {
      return null;
    }
    String value = node.asText();
    if (!isLearned(value)) {
      return null;
    }
    if (byteLength(value) > MAX_RELAYED_VALUE_BYTES) {
      logger.debug(
          "Telemetry: /health field '{}' exceeded {} bytes ({} bytes); omitted",
          key,
          MAX_RELAYED_VALUE_BYTES,
          byteLength(value));
      return null;
    }
    return value;
  }

  /**
   * Bounds the {@code /health} body the probe will read. The real response is a few kilobytes; 1
   * MiB is orders of magnitude above any legitimate body while capping how much a misbehaving or
   * hostile endpoint can make the telemetry path buffer.
   */
  private static final long MAX_HEALTH_BODY_BYTES = 1L << 20;

  /**
   * Probes the agent's {@code /health} endpoint ONCE and extracts every telemetry dimension it
   * carries.
   *
   * <p>Returns both fields {@code null} on any failure — unreachable endpoint, non-2xx, unparseable
   * body — so telemetry degrades to omitting the fields and never fails the ping or throws into the
   * caller. The body is read through a BOUNDED read, so a hostile or misbehaving endpoint cannot
   * make the telemetry path buffer without limit; exceeding the bound truncates, which fails the
   * parse and therefore fails open like every other probe failure.
   *
   * <p>This is the SDK's only {@code /health} fetch on the telemetry path; the licence tier rides
   * along on the response already being fetched for the version. Adding a second request here would
   * double the telemetry path's blocking budget and its failure surface — do not.
   *
   * @param budgetMs derived from the shared telemetry deadline so the health probe and the
   *     checkpoint POST don't stack into a larger combined budget.
   */
  static PlatformHealthProbe probePlatformHealth(String sdkEndpoint, long budgetMs) {
    if (sdkEndpoint == null || sdkEndpoint.isEmpty()) {
      return EMPTY_HEALTH_PROBE;
    }
    try {
      // NO REDIRECTS ON THE PROBE EITHER. A 30x from /health would otherwise be followed
      // silently, and every value promoted below — the version, the tier, the edition and
      // the platform's deployment mode — would describe the REDIRECT TARGET rather than the
      // endpoint the caller configured. A captive portal, a misconfigured proxy or an
      // http->https hop is enough to make the heartbeat report a platform the user never
      // pointed at. The 30x then fails isSuccessful() and yields an empty probe: "not
      // learned", the honest answer.
      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(budgetMs, TimeUnit.MILLISECONDS)
              .readTimeout(budgetMs, TimeUnit.MILLISECONDS)
              .followRedirects(false)
              .followSslRedirects(false)
              .build();

      Request request = new Request.Builder().url(sdkEndpoint + "/health").get().build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful() || response.body() == null) {
          if (isRedirect(response.code())) {
            logger.debug(
                "Telemetry: /health answered {} (a redirect); refused, relayed fields omitted",
                response.code());
          }
          return EMPTY_HEALTH_PROBE;
        }
        // Bounded read. response.body().string() buffers the WHOLE body with no
        // limit, so a hostile /health could exhaust memory on the telemetry
        // path — and an OutOfMemoryError is an Error, not an Exception, so it
        // would escape the catch below and reach the caller. peekBody(n) reads
        // AT MOST n bytes, so this bound is real, the same way Go uses
        // io.LimitReader. (The TypeScript and Python SDKs deliberately do NOT
        // do this: their HTTP clients have already buffered the body by the
        // time it is parsed, so a cap there would bound the parse, not the read.)
        String rawBody = response.peekBody(MAX_HEALTH_BODY_BYTES).string();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(rawBody);
        if (root == null || !root.isObject()) {
          return EMPTY_HEALTH_PROBE;
        }

        // Each field is promoted independently. An absent key leaves the other
        // field intact — the pre-#3619 code returned early when `version` was
        // missing, which would have discarded a learned tier.
        // `version` keeps its historical COERCING read (asText on any node type) so a
        // platform that has always answered with a non-string version does not silently
        // lose a dimension that worked before. It is byte-capped like the rest.
        String version = null;
        JsonNode versionNode = root.get("version");
        if (versionNode != null && !versionNode.isNull() && !versionNode.asText().isEmpty()) {
          String raw = versionNode.asText();
          version = byteLength(raw) > MAX_RELAYED_VALUE_BYTES ? null : raw;
        }

        // Stricter than the version read above, deliberately. asText() COERCES
        // a non-textual node, so a malformed `"tier": 42` or `"tier": true`
        // would become "42"/"true" and land in the receiver's unknown bucket
        // as though the platform had reported a tier. Absent is the honest
        // answer, so a non-string tier is treated as not learned. The version
        // read keeps its long-standing coercing behaviour unchanged.
        // Verbatim, including the transient "starting" the agent returns before its
        // licence is validated: a real signal the receiver buckets deliberately, not an
        // error to filter client-side.
        String tier = learned(root, "tier");

        return new PlatformHealthProbe(
            version, tier, learned(root, "edition"), learned(root, "deployment_mode"));
      }
    } catch (Exception ignored) {
      // Silent failure — telemetry must never disrupt SDK operation.
    }
    return EMPTY_HEALTH_PROBE;
  }

  /**
   * Normalize OS name to lowercase short form consistent across SDKs. e.g. "Mac OS X" -> "darwin",
   * "Windows 10" -> "windows", "Linux" -> "linux"
   */
  static String normalizeOS(String osName) {
    if (osName == null) return "unknown";
    String lower = osName.toLowerCase();
    if (lower.contains("mac") || lower.contains("darwin")) return "darwin";
    if (lower.contains("win")) return "windows";
    if (lower.contains("linux")) return "linux";
    return lower;
  }

  /** Normalize arch name consistent across SDKs. e.g. "aarch64" -> "arm64", "x86_64" -> "x64" */
  static String normalizeArch(String arch) {
    if (arch == null) return "unknown";
    if ("aarch64".equals(arch)) return "arm64";
    if ("x86_64".equals(arch) || "amd64".equals(arch)) return "x64";
    return arch;
  }

  private TelemetryReporter() {
    // Utility class
  }
}
