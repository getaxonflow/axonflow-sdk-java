// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// Basic AxonFlow Java SDK smoke example.
//
// Demonstrates:
//   - Client initialization from environment
//   - Health check against the agent
//   - A protected proxyLLMCall round-trip
//   - Listing connectors (read-only sanity check)
//
// Run from this directory after `mvn install -DskipTests` at the SDK root:
//
//   export AXONFLOW_AGENT_URL=http://localhost:8080
//   export AXONFLOW_CLIENT_ID=your-client-id
//   export AXONFLOW_CLIENT_SECRET=your-client-secret
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.ConnectionException;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.types.ConnectorInfo;
import com.getaxonflow.sdk.types.HealthStatus;
import com.getaxonflow.sdk.types.RequestType;

import java.util.List;

public class Basic {

    public static void main(String[] args) {
        String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String clientId = System.getenv("AXONFLOW_CLIENT_ID");
        String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");

        if (clientId == null || clientId.isEmpty()
                || clientSecret == null || clientSecret.isEmpty()) {
            System.err.println(
                    "AXONFLOW_CLIENT_ID and AXONFLOW_CLIENT_SECRET must be set");
            System.exit(1);
        }

        System.out.println("Initializing AxonFlow client...");
        // try-with-resources so OkHttp's dispatcher + connection pool are
        // cleaned up promptly. Without this, non-daemon threads keep the JVM
        // alive ~60s after main() returns and the smoke timeout starts to
        // bite.
        try (AxonFlow client =
                AxonFlow.create(
                        AxonFlowConfig.builder()
                                .agentUrl(endpoint)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .debug(true)
                                .build())) {

            healthCheck(client);
            proxyLLMCallStep(client, clientId);
            listConnectorsStep(client);
        }

        System.out.println("\nBasic example complete.");
    }

    private static void healthCheck(AxonFlow client) {
        System.out.println("\n============================================================");
        System.out.println("Step 1: Health Check");
        System.out.println("============================================================");
        try {
            HealthStatus health = client.healthCheck();
            System.out.printf("  Status:  %s%n", health.getStatus());
            if (health.getVersion() != null) {
                System.out.printf("  Version: %s%n", health.getVersion());
            }
        } catch (Exception e) {
            System.err.println("Health check failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void proxyLLMCallStep(AxonFlow client, String clientId) {
        System.out.println("\n============================================================");
        System.out.println("Step 2: Protected proxyLLMCall");
        System.out.println("============================================================");
        try {
            // Don't set userToken — the SDK auto-populates it from clientId
            // when omitted. Sending a literal "demo-user" string is rejected
            // by the agent's JWT middleware on stacks with token validation.
            ClientRequest request =
                    ClientRequest.builder()
                            .query("What is the capital of France?")
                            .clientId(clientId)
                            .requestType(RequestType.CHAT)
                            .build();

            ClientResponse response = client.proxyLLMCall(request);
            System.out.printf("  Success: %s%n", response.isSuccess());
            System.out.printf("  Blocked: %s%n", response.isBlocked());
        } catch (PolicyViolationException e) {
            // Policy block is a valid outcome — community policies can match
            // the demo query depending on configuration.
            System.out.printf("  Blocked by policy: %s%n", e.getMessage());
        } catch (AuthenticationException | ConnectionException e) {
            // These are real failures: bad creds or stack down. Fail loud.
            System.err.println("proxyLLMCall failed: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // Other runtime failures (e.g. agent returns non-2xx because no
            // LLM provider is configured) — log and continue. Tightening
            // this further requires capability detection from /health,
            // tracked in axonflow-sdk-java#146.
            System.out.println("  proxyLLMCall non-success: " + e.getMessage());
        }
    }

    private static void listConnectorsStep(AxonFlow client) {
        System.out.println("\n============================================================");
        System.out.println("Step 3: List Connectors");
        System.out.println("============================================================");
        try {
            List<ConnectorInfo> connectors = client.listConnectors();
            System.out.printf("  Found %d connectors%n", connectors.size());
            for (ConnectorInfo c : connectors) {
                System.out.printf("    - %s (%s) installed=%s%n",
                        c.getName(), c.getType(), c.isInstalled());
            }
        } catch (AuthenticationException | ConnectionException e) {
            System.err.println("listConnectors failed: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.out.println("  listConnectors non-success: " + e.getMessage());
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
