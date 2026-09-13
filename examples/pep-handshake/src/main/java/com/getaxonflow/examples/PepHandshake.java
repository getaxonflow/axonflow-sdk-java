// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.exceptions.PEPHandshakeException;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.PEPCapability;
import com.getaxonflow.sdk.types.PEPHandshake;
import java.util.List;

/**
 * Declaring an enforcement point's capabilities with the PEP capability handshake, which the
 * platform reads from v10.4.0.
 *
 * <p>An enforcement point (a PEP) declares, on each governed call, the exact obligation types and
 * schema versions it can discharge. On an Enterprise deployment an allow verdict carrying a
 * mandatory obligation the declared set cannot discharge becomes a deny, so the enforcement point
 * is never handed an instruction it would drop; a Community deployment records the declaration.
 * From v11.0.0, on both editions, a decide under an organization's redact override refuses a caller
 * that does not declare redaction.
 *
 * <p>This example builds a declaration once for the client, derives a client presenting another one
 * (one process can be two enforcement points), and shows that a declaration the platform would
 * refuse fails here, before anything is sent. Each decide prints its verdict and reasons.
 *
 * <p>After a document with an organization-scope constraint is activated, a decide that does not
 * supply the attribute the constraint conditions on is denied fail-closed with reasons
 * ["unknown_constraint"]; supply the attribute or run this example on a fresh stack. Run it before
 * the typed-policies example, which publishes and activates such a document.
 *
 * <p>On Enterprise the client id is the organization id and the secret its license key; a Community
 * deployment accepts any credentials.
 *
 * <pre>
 * mvn -q -DskipTests -DskipUnitTests=true install   # from the repository root: the SDK on the local classpath
 * AXONFLOW_AGENT_URL=http://localhost:8080 mvn -q -f examples/pep-handshake/pom.xml compile exec:java
 * </pre>
 *
 * <p>Exits non-zero if a step fails, so it is usable as a smoke test.
 */
public final class PepHandshake {

  private static int failures;

  private PepHandshake() {}

  @FunctionalInterface
  private interface Step {
    void run() throws Exception;
  }

  private static void step(String name, Step body) {
    System.out.println("\n=== " + name + " ===");
    try {
      body.run();
      System.out.println("ok");
    } catch (Exception e) {
      System.out.println("FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      failures++;
    }
  }

  private static void print(DecideResponse decision) {
    System.out.println(
        "verdict="
            + decision.getVerdict()
            + " reasons="
            + decision.getReasons()
            + " obligations="
            + (decision.getObligations() == null ? 0 : decision.getObligations().size()));
  }

  public static void main(String[] args) {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");

    // The request path redacts fields; it declares exactly that.
    PEPHandshake requestPath =
        PEPHandshake.of(
            "checkout-gateway",
            "https://pep.example.com",
            List.of(PEPCapability.of(AuthZENObligationType.FIELD_REDACT, 1)));
    AxonFlowConfig.Builder config =
        AxonFlowConfig.builder().endpoint(endpoint).pepHandshake(requestPath);
    if (clientId != null && !clientId.isEmpty()) {
      config.clientId(clientId).clientSecret(clientSecret);
    }
    try (AxonFlow client = AxonFlow.create(config.build())) {
      DecideRequest decide =
          DecideRequest.builder("tool", "look up the weather")
              .target(new DecisionTarget("tool", null, null, "search"))
              .build();

      step("decide with the client's declaration", () -> print(client.decide(decide)));

      // The response path masks fields instead. A derived client presents its own declaration in
      // place of the client's, and shares the client's connection pool: never close it.
      step(
          "decide with a derived client's declaration",
          () -> {
            PEPHandshake responsePath =
                PEPHandshake.of(
                    "checkout-gateway-response",
                    "https://pep.example.com",
                    List.of(PEPCapability.of(AuthZENObligationType.FIELD_MASK, 1)));
            print(client.withPEPHandshake(responsePath).decide(decide));
          });

      // A declaration the platform would refuse fails at construction, naming the member at fault,
      // instead of the first governed call answering 400.
      step(
          "a declaration the platform would refuse",
          () -> {
            try {
              PEPHandshake.of("Checkout:Gateway", "https://pep.example.com", List.of());
              throw new IllegalStateException("the declaration was accepted");
            } catch (PEPHandshakeException refusal) {
              System.out.println(
                  "refused at " + refusal.getPointer() + ": " + refusal.getMessage());
            }
          });
    }

    if (failures > 0) {
      System.out.println("\n" + failures + " step(s) failed");
      System.exit(1);
    }
  }
}
