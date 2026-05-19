/*
 * runtime-e2e/x-client-id/SdkXClientIdTest.java
 *
 * Per CLAUDE.md HARD RULE #0: real-wire test of the SDK's v9 X-Client-ID
 * header emission to a real running AxonFlow agent.
 *
 * Approach: the SDK does not expose its internal OkHttpClient, so we
 * run a tiny in-process forwarding proxy (Java's HttpServer + the JDK's
 * built-in HttpClient) that inspects every request, captures the
 * X-Client-ID header, and forwards to the real agent. The SDK is
 * pointed at the proxy. Bytes flow real → real.
 *
 * Run:
 *   AXONFLOW_AGENT_URL=http://localhost:8080 \
 *     AXONFLOW_TENANT_ID=cs_... AXONFLOW_TENANT_SECRET=... \
 *     java -cp "<sdk-jar>:<deps>" runtime-e2e/x-client-id/SdkXClientIdTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

public class SdkXClientIdTest {
  public static void main(String[] args) throws Exception {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    String tenant = System.getenv("AXONFLOW_TENANT_ID");
    String secret = System.getenv("AXONFLOW_TENANT_SECRET");
    if (tenant == null || secret == null) {
      System.err.println(
          "AXONFLOW_TENANT_ID + AXONFLOW_TENANT_SECRET must be set; see ../README.md");
      System.exit(2);
    }

    final URI target = URI.create(endpoint);
    final HttpClient forwarder = HttpClient.newHttpClient();
    final AtomicReference<String> sawClientId = new AtomicReference<>("");

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          sawClientId.set(ex.getRequestHeaders().getFirst("X-Client-ID"));
          byte[] body = ex.getRequestBody().readAllBytes();
          HttpRequest.Builder b =
              HttpRequest.newBuilder(target.resolve(ex.getRequestURI()))
                  .method(ex.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(body));
          ex.getRequestHeaders()
              .forEach(
                  (k, vs) -> {
                    if (!k.equalsIgnoreCase("host") && !k.equalsIgnoreCase("content-length")) {
                      vs.forEach(v -> b.header(k, v));
                    }
                  });
          try {
            HttpResponse<byte[]> resp =
                forwarder.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] respBody = resp.body();
            ex.sendResponseHeaders(resp.statusCode(), respBody.length);
            ex.getResponseBody().write(respBody);
            ex.getResponseBody().close();
          } catch (Exception e) {
            ex.sendResponseHeaders(502, 0);
            ex.close();
          }
        });
    server.start();
    String proxyUrl = "http://127.0.0.1:" + server.getAddress().getPort();

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(proxyUrl)
                .clientId(tenant)
                .clientSecret(secret)
                .build());

    System.out.println("Asserting wire X-Client-ID = " + tenant);
    try {
      MCPCheckInputResponse r = client.mcpCheckInput("postgres", "SELECT 1");
      // outcome doesn't matter; only the captured header
    } catch (Exception ignored) {
      // outcome doesn't matter; only the captured header
    }
    server.stop(0);

    String got = sawClientId.get();
    if (!tenant.equals(got)) {
      System.err.println("FAIL: wire X-Client-ID = \"" + got + "\", want \"" + tenant + "\"");
      System.exit(1);
    }
    System.out.println("PASS: wire X-Client-ID = \"" + got + "\"");
  }
}
