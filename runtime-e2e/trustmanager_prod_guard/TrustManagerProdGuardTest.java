/*
 * runtime-e2e/trustmanager_prod_guard/TrustManagerProdGuardTest.java
 *
 * Real-wire test of the insecure-TLS production guard (CodeQL
 * java/insecure-trustmanager hardening, epic getaxonflow/axonflow-enterprise#2711)
 * against a running AxonFlow enterprise agent over its real (valid) TLS cert.
 *
 * The SDK's trust-all TLS path is an intentional, double-gated development
 * escape hatch for self-signed certificates: it activates only when BOTH
 * insecureSkipVerify(true) AND the AXONFLOW_INSECURE_TLS env var are set. The
 * hardening adds a HARD PRODUCTION GUARD: when a production-like deployment
 * environment is detected, the insecure path is refused outright and TLS
 * certificate verification stays ON.
 *
 * This test MUST be run with the guard tripped (ENVIRONMENT=production +
 * AXONFLOW_INSECURE_TLS=true) and proves, with NO mocks:
 *
 *   1. Even though both insecure gates are set, HttpClientFactory.create() with
 *      insecureSkipVerify(true) leaves OkHttp's default verifying hostname
 *      verifier in place (the permissive trust-all lambda is NOT installed) ->
 *      the guard PREVENTS the insecure path, it does not merely log.
 *   2. A real governed decide() call against the live HTTPS agent SUCCEEDS over
 *      the agent's valid certificate, proving the guard kept verification ON
 *      without breaking legitimate connectivity.
 *   3. Parity: a plain config (no insecureSkipVerify) yields the same verifier
 *      type, confirming the guarded client is indistinguishable from the safe
 *      default.
 *
 * Run (the two guard env vars are REQUIRED for this test to be meaningful):
 *   source /tmp/axonflow-e2e-env.sh   # AXONFLOW_ENDPOINT (https) + creds
 *   mvn -q -DskipTests package
 *   mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   AXONFLOW_INSECURE_TLS=true ENVIRONMENT=production \
 *     java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *       runtime-e2e/trustmanager_prod_guard/TrustManagerProdGuardTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.util.HttpClientFactory;
import okhttp3.OkHttpClient;

public class TrustManagerProdGuardTest {

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  static void check(boolean cond, String msg) {
    if (!cond) {
      fail(msg);
    }
  }

  public static void main(String[] args) {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_ENDPOINT", "https://localhost:8443");
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
    String userToken = System.getenv("AXONFLOW_USER_TOKEN");

    // Both insecure gates MUST be set for this test to exercise the guard; the
    // guard is only interesting when it has something to refuse. We read the
    // process env directly here (the SDK's gating helpers are package-private).
    String insecureEnv = System.getenv("AXONFLOW_INSECURE_TLS");
    check(
        insecureEnv != null && (insecureEnv.equalsIgnoreCase("true") || insecureEnv.equals("1")),
        "AXONFLOW_INSECURE_TLS must be set to 'true'/'1' to exercise the guard "
            + "(run: AXONFLOW_INSECURE_TLS=true ENVIRONMENT=production java ...)");
    String envName = System.getenv("ENVIRONMENT");
    check(
        envName != null && envName.toLowerCase().contains("prod"),
        "a production-like env var (e.g. ENVIRONMENT=production) must be set so the guard trips");
    System.out.println(
        "guard precondition: AXONFLOW_INSECURE_TLS set AND ENVIRONMENT=" + envName);

    // 1. The guard must PREVENT the trust-all path: with insecureSkipVerify(true)
    //    AND AXONFLOW_INSECURE_TLS=true AND a production signal, the built client
    //    must still carry OkHttp's default verifying hostname verifier (the
    //    permissive (hostname, session) -> true lambda is NOT installed).
    AxonFlowConfig insecureRequested =
        AxonFlowConfig.builder().endpoint(endpoint).insecureSkipVerify(true).build();
    OkHttpClient guardedClient = HttpClientFactory.create(insecureRequested);
    String guardedVerifier = guardedClient.hostnameVerifier().getClass().getName();
    check(
        guardedVerifier.contains("OkHostnameVerifier"),
        "production guard FAILED: trust-all verifier was installed despite a production env ("
            + guardedVerifier
            + ")");
    System.out.println("PASS step 1: guard refused the trust-all path; verifier=" + guardedVerifier);

    // 3. Parity with a plain config (no insecureSkipVerify): same verifier type.
    AxonFlowConfig plain = AxonFlowConfig.builder().endpoint(endpoint).build();
    OkHttpClient plainClient = HttpClientFactory.create(plain);
    check(
        guardedClient.hostnameVerifier().getClass().equals(plainClient.hostnameVerifier().getClass()),
        "guarded client verifier type differs from the safe-default client verifier type");
    System.out.println("PASS step 3: guarded client is verifier-identical to the safe default");

    // 2. A real governed call must SUCCEED over the agent's valid TLS cert,
    //    proving the kept-on verification did not break legitimate connectivity.
    if (clientId == null || clientSecret == null) {
      fail("AXONFLOW_CLIENT_ID / AXONFLOW_CLIENT_SECRET unset -- source /tmp/axonflow-e2e-env.sh");
    }
    if (!endpoint.startsWith("https://")) {
      fail("AXONFLOW_ENDPOINT must be https:// for the TLS-verification assertion to be meaningful");
    }
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .insecureSkipVerify(true) // requested, but the guard keeps verification ON
                .build());
    DecideResponse decision =
        client.decide(DecideRequest.builder("tool", "ping").userToken(userToken).build());
    check(
        decision != null && decision.getVerdict() != null,
        "expected a non-null verdict from the live agent over verified TLS");
    System.out.println(
        "PASS step 2: governed decide() succeeded over verified TLS, verdict="
            + decision.getVerdict());

    System.out.println(
        "ALL PASS: insecure-TLS production guard verified end-to-end against the live agent");
  }
}
