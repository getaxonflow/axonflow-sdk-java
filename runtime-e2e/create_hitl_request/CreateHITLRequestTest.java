/*
 * runtime-e2e/create_hitl_request/CreateHITLRequestTest.java
 *
 * Real-wire test of the SDK's createHITLRequest method
 * (getaxonflow/axonflow-enterprise#2421). Spins up a tiny in-process
 * HttpServer that mimics the platform handler at
 * platform/agent/hitl/handler.go:177, drives axonflow.createHITLRequest
 * against it through the real OkHttp transport, then asserts the
 * captured request body carries every required field plus the new
 * notify_url surface added in
 * getaxonflow/axonflow-enterprise#2419.
 *
 * No WireMock, no JUnit, no test doubles — real HttpServer +
 * OkHttpClient on both sides. Satisfies the runtime-e2e/ DoD gate
 * that the WireMock-based HITLTest unit suite under src/test/java/
 * does not.
 *
 * Run:
 *   ./mvnw -q package -DskipTests
 *   java -cp "target/axonflow-sdk-8.2.0.jar:$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stderr 2>&1 | tail -1)" \
 *     runtime-e2e/create_hitl_request/CreateHITLRequestTest.java
 *
 * Sister coverage runs in CI via HITLTest's WireMock-driven 8-case
 * createHITLRequest scenario. This proof additionally exercises the
 * real JDK HttpServer rather than WireMock's Jetty harness.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.hitl.HITLTypes.HITLApprovalRequest;
import com.getaxonflow.sdk.types.hitl.HITLTypes.HITLCreateInput;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class CreateHITLRequestTest {

  private static final String NOTIFY_URL = "https://workflows.example.com/hooks/runtime-e2e";

  public static void main(String[] args) throws Exception {
    AtomicReference<String> captured = new AtomicReference<>("");

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/hitl/queue",
        exchange -> {
          try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            exchange.getRequestBody().transferTo(bos);
            captured.set(bos.toString(StandardCharsets.UTF_8));
          }
          if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
          }
          ObjectMapper mapper = new ObjectMapper();
          JsonNode in = mapper.readTree(captured.get());
          String resp =
              "{\"success\":true,\"data\":{"
                  + "\"request_id\":\"hitl-req-runtime-e2e-001\","
                  + "\"org_id\":\"org-runtime-e2e\","
                  + "\"tenant_id\":\"tenant-runtime-e2e\","
                  + "\"client_id\":"
                  + mapper.writeValueAsString(in.path("client_id").asText())
                  + ","
                  + "\"user_id\":"
                  + mapper.writeValueAsString(in.path("user_id").asText())
                  + ","
                  + "\"original_query\":"
                  + mapper.writeValueAsString(in.path("original_query").asText())
                  + ","
                  + "\"request_type\":"
                  + mapper.writeValueAsString(in.path("request_type").asText())
                  + ","
                  + "\"triggered_policy_id\":"
                  + mapper.writeValueAsString(in.path("triggered_policy_id").asText())
                  + ","
                  + "\"triggered_policy_name\":"
                  + mapper.writeValueAsString(in.path("triggered_policy_name").asText())
                  + ","
                  + "\"trigger_reason\":"
                  + mapper.writeValueAsString(in.path("trigger_reason").asText())
                  + ","
                  + "\"severity\":"
                  + mapper.writeValueAsString(in.path("severity").asText())
                  + ","
                  + "\"notify_url\":"
                  + mapper.writeValueAsString(in.path("notify_url").asText())
                  + ","
                  + "\"status\":\"pending\","
                  + "\"expires_at\":\"2026-05-23T11:00:00Z\","
                  + "\"created_at\":\"2026-05-23T10:00:00Z\","
                  + "\"updated_at\":\"2026-05-23T10:00:00Z\""
                  + "}}";
          byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(201, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    int port = server.getAddress().getPort();
    String endpoint = "http://127.0.0.1:" + port;

    int exitCode = 0;
    try {
      AxonFlow axonflow =
          AxonFlow.create(AxonFlowConfig.builder().endpoint(endpoint).build());

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("runtime-e2e-client")
              .userId("runtime-e2e-user")
              .originalQuery("disburse $50000 to cust-runtime-e2e")
              .requestType("adk-tool")
              .triggeredPolicyId("loan-amount-cap")
              .triggeredPolicyName("Loan amount cap")
              .triggerReason("Disbursement above $10k requires manager approval")
              .severity("high")
              .notifyUrl(NOTIFY_URL)
              .build();

      HITLApprovalRequest req = axonflow.createHITLRequest(input);

      String body = captured.get();
      if (body == null || body.isEmpty()) {
        System.err.println("FAIL: server captured no body");
        exitCode = 1;
      } else {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode wire = mapper.readTree(body);
        String[][] expected = {
          {"client_id", "runtime-e2e-client"},
          {"user_id", "runtime-e2e-user"},
          {"original_query", "disburse $50000 to cust-runtime-e2e"},
          {"request_type", "adk-tool"},
          {"triggered_policy_id", "loan-amount-cap"},
          {"triggered_policy_name", "Loan amount cap"},
          {"trigger_reason", "Disbursement above $10k requires manager approval"},
          {"severity", "high"},
          {"notify_url", NOTIFY_URL},
        };
        for (String[] kv : expected) {
          String got = wire.path(kv[0]).asText();
          if (!kv[1].equals(got)) {
            System.err.printf(
                "FAIL: wire body field %s = %s, want %s%nFull body: %s%n",
                kv[0], got, kv[1], body);
            exitCode = 1;
          }
        }
        if (!"hitl-req-runtime-e2e-001".equals(req.getRequestId())) {
          System.err.printf("FAIL: parsed request_id = %s%n", req.getRequestId());
          exitCode = 1;
        }
        if (!NOTIFY_URL.equals(req.getNotifyUrl())) {
          System.err.printf(
              "FAIL: parsed notify_url = %s, want %s%n", req.getNotifyUrl(), NOTIFY_URL);
          exitCode = 1;
        }
      }

      if (exitCode == 0) {
        System.out.println(
            "PASS: createHITLRequest wire payload + response parsing round-trip OK");
        System.out.println("Wire body: " + body);
        System.out.printf(
            "Parsed requestId=%s notifyUrl=%s%n", req.getRequestId(), req.getNotifyUrl());
      }
    } catch (Exception e) {
      System.err.println("FAIL: unexpected exception: " + e);
      e.printStackTrace();
      exitCode = 1;
    } finally {
      server.stop(0);
    }
    System.exit(exitCode);
  }
}
