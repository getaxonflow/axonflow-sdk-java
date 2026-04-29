/*
 * Cross-platform real-stack smoke for the Java SDK.
 *
 * Reads:
 *   AXONFLOW_AGENT_URL       — fake agent base URL
 *   AXONFLOW_CHECKPOINT_URL  — fake checkpoint URL
 *
 * Constructs AxonFlow.create(...) and verifies the constructor's
 * heartbeat fires plus the stamp lands at the OS-native cache path.
 *
 * NOTE: The Java SDK resolves the cache dir via
 * System.getProperty("user.home"), not getenv("HOME"). On macOS the JVM
 * ignores $HOME for user.home — call this with -Duser.home=$HOME or
 * the path will leak to the real cache.
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SmokeJava {
  public static void main(String[] args) throws Exception {
    String agent = System.getenv("AXONFLOW_AGENT_URL");
    if (agent == null || agent.isEmpty()) {
      System.err.println("FAIL: AXONFLOW_AGENT_URL not set");
      System.exit(1);
    }

    Path expected = stampPath();

    try (AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(agent)
                .clientId("smoke-test")
                .clientSecret("smoke-secret")
                .build())) {
      try {
        client.healthCheck();
      } catch (Exception ignored) {
        // best-effort
      }
    }
    // Constructor's invokeHeartbeat is synchronous; small grace window
    // for the executor's request-path heartbeat to flush.
    Thread.sleep(500);

    if (!Files.exists(expected)) {
      System.err.println("FAIL: stamp not at " + expected);
      System.exit(1);
    }
    System.out.println("OK: stamp at " + expected);
  }

  private static Path stampPath() {
    String osName = System.getProperty("os.name", "").toLowerCase();
    String userHome = System.getProperty("user.home");
    if (osName.contains("mac")) {
      return Paths.get(userHome, "Library", "Caches", "axonflow", "java-telemetry-last-sent");
    }
    if (osName.contains("win")) {
      String localAppData = System.getenv("LOCALAPPDATA");
      return Paths.get(localAppData, "axonflow", "java-telemetry-last-sent");
    }
    String xdg = System.getenv("XDG_CACHE_HOME");
    if (xdg != null && !xdg.isEmpty()) {
      return Paths.get(xdg, "axonflow", "java-telemetry-last-sent");
    }
    return Paths.get(userHome, ".cache", "axonflow", "java-telemetry-last-sent");
  }
}
