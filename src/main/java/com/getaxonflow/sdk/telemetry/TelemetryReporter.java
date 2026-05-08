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
 * suppression of sandbox-mode pings has also been removed. Sandbox-mode pings now fire on the
 * same heartbeat schedule as production-mode pings, tagged {@code stream="sandbox"} in the
 * payload so analytics can distinguish dev/test pings from production heartbeat (the wire-side
 * allowlist is enforced by the checkpoint service — see {@code IsValidIncomingStream}).
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
   * Sends an anonymous telemetry ping synchronously (blocks until the round-trip completes).
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
   * <p>Returns {@code true} only when the POST received a 2xx response.
   * Network failures, timeouts, and non-2xx responses all return
   * {@code false}. Used by the heartbeat orchestrator (see
   * {@link HeartbeatState}) where the boolean drives stamp-on-DELIVERY
   * semantics: only successful POSTs advance the stamp file.
   *
   * <p>The caller is responsible for the gating decision — this method
   * does NOT consult AXONFLOW_TELEMETRY, isEnabled, the stamp file, or
   * any rate-limit state. That separation lets the heartbeat module make
   * the gating decision once and only update the stamp on success.
   */
  public static boolean sendPingNow(
      String mode, String sdkEndpoint, boolean debug, String checkpointUrl) {
    logger.info(
        "AxonFlow: anonymous telemetry enabled. Opt out: AXONFLOW_TELEMETRY=off | https://docs.getaxonflow.com/docs/telemetry");

    String endpoint =
        (checkpointUrl != null && !checkpointUrl.isEmpty()) ? checkpointUrl : DEFAULT_ENDPOINT;

    String endpointType = classifyEndpoint(sdkEndpoint);

    try {
      long deadlineMs =
          System.nanoTime() / 1_000_000L + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);

      long healthBudgetMs =
          Math.min(
              TimeUnit.SECONDS.toMillis(1),
              Math.max(0L, deadlineMs - System.nanoTime() / 1_000_000L));
      String platformVersion =
          (sdkEndpoint != null && !sdkEndpoint.isEmpty() && healthBudgetMs > MIN_BUDGET_MS)
              ? detectPlatformVersion(sdkEndpoint, healthBudgetMs)
              : null;

      String payload = buildPayload(mode, platformVersion, endpointType);

      long postBudgetMs = Math.max(0L, deadlineMs - System.nanoTime() / 1_000_000L);
      if (postBudgetMs < MIN_BUDGET_MS) {
        return false;
      }

      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .readTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .writeTimeout(postBudgetMs, TimeUnit.MILLISECONDS)
              .build();

      RequestBody body = RequestBody.create(payload, JSON);
      Request request = new Request.Builder().url(endpoint).post(body).build();

      try (Response response = client.newCall(request).execute()) {
        if (debug) {
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
   * else). Sandbox-mode pings are tagged {@code stream="sandbox"} in the payload so analytics
   * can still distinguish them — see {@link #buildPayload}.
   *
   * <p>Historical context: v7.x supported a {@code Boolean configOverride} parameter and a
   * {@code mode != "sandbox"} default-suppression rule. Both were removed in v8.0 to leave a
   * single, ops-controlled opt-out lever and avoid silent suppression that masks real adoption
   * signal. See CHANGELOG v8.0.0.
   *
   * <p>{@code DO_NOT_TRACK} is intentionally NOT honored. It is commonly inherited from host
   * tools and developer environments (CLIs like Codex and Claude Code inject it unconditionally),
   * which makes it an unreliable expression of user intent for AxonFlow telemetry.
   *
   * @param axonflowTelemetry value of {@code AXONFLOW_TELEMETRY} env var (null = unset)
   * @return true if telemetry should be sent
   */
  public static boolean isEnabled(String axonflowTelemetry) {
    // AXONFLOW_TELEMETRY=off is the SOLE opt-out path.
    return !(axonflowTelemetry != null && "off".equalsIgnoreCase(axonflowTelemetry.trim()));
  }

  /** Builds the JSON payload for the telemetry ping. */
  static String buildPayload(String mode, String platformVersion) {
    return buildPayload(mode, platformVersion, EndpointType.UNKNOWN);
  }

  /** Builds the JSON payload with an explicit endpoint_type classification. */
  static String buildPayload(String mode, String platformVersion, String endpointType) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = mapper.createObjectNode();
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
      root.put("deployment_mode", mode);
      root.put("endpoint_type", endpointType);

      ArrayNode features = mapper.createArrayNode();
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

      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      // Fallback minimal payload
      return "{\"sdk\":\"java\",\"sdk_version\":\"" + AxonFlowConfig.SDK_VERSION + "\"}";
    }
  }

  /**
   * Endpoint type classifications for telemetry. See issue #1525.
   *
   * <p>The raw URL is never sent to the checkpoint service — only the classification.
   */
  public static final class EndpointType {
    public static final String LOCALHOST = "localhost";
    public static final String PRIVATE_NETWORK = "private_network";
    public static final String REMOTE = "remote";
    public static final String COMMUNITY_SAAS = "community-saas";
    public static final String UNKNOWN = "unknown";

    private EndpointType() {}
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
    if ("1".equals(System.getenv("AXONFLOW_TRY"))) return EndpointType.COMMUNITY_SAAS;
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

    if ("localhost".equals(host)
        || "0.0.0.0".equals(host)
        || host.endsWith(".localhost")) {
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
   * Expand an IPv6 address to its full 8-hextet form with every hextet
   * zero-padded to 4 hex digits. Returns the input unchanged on parse failure.
   *
   * <p>Examples:
   *
   * <pre>
   *   ::1      → 0000:0000:0000:0000:0000:0000:0000:0001
   *   fd00::1  → fd00:0000:0000:0000:0000:0000:0000:0001
   *   fe80::a  → fe80:0000:0000:0000:0000:0000:0000:000a
   * </pre>
   *
   * <p>This is NOT a general-purpose IPv6 parser — it assumes the input came
   * from URI.getHost() after brackets are stripped.
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
    if (sdkEndpoint == null || sdkEndpoint.isEmpty()) {
      return null;
    }
    try {
      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(budgetMs, TimeUnit.MILLISECONDS)
              .readTimeout(budgetMs, TimeUnit.MILLISECONDS)
              .build();

      Request request = new Request.Builder().url(sdkEndpoint + "/health").get().build();

      try (Response response = client.newCall(request).execute()) {
        if (response.isSuccessful() && response.body() != null) {
          ObjectMapper mapper = new ObjectMapper();
          JsonNode root = mapper.readTree(response.body().string());
          JsonNode versionNode = root.get("version");
          if (versionNode != null && !versionNode.isNull() && !versionNode.asText().isEmpty()) {
            return versionNode.asText();
          }
        }
      }
    } catch (Exception ignored) {
      // Silent failure
    }
    return null;
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
