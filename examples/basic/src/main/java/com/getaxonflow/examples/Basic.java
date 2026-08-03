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
import com.getaxonflow.sdk.exceptions.AxonFlowException;
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
            // Enterprise stacks (DEPLOYMENT_MODE=enterprise) validate user
            // tokens as JWTs — export AXONFLOW_USER_TOKEN (see
            // scripts/generate-jwt.sh in the platform repo). Community
            // stacks skip JWT validation, so omitting it is fine there.
            String userToken = System.getenv("AXONFLOW_USER_TOKEN");
            ClientRequest.Builder requestBuilder =
                    ClientRequest.builder()
                            .query("What is the capital of France?")
                            .clientId(clientId)
                            .requestType(RequestType.CHAT);
            if (userToken != null && !userToken.isEmpty()) {
                requestBuilder.userToken(userToken);
            }
            ClientRequest request = requestBuilder.build();

            ClientResponse response = client.proxyLLMCall(request);
            System.out.printf("  Success: %s%n", response.isSuccess());
            System.out.printf("  Blocked: %s%n", response.isBlocked());
        } catch (PolicyViolationException e) {
            // SDK <= 9.0.0 misclassifies 403 auth rejections as policy
            // violations: every agent error body carries a literal
            // "blocked":false key, which trips handleErrorResponse's
            // body.contains("blocked") heuristic. Until the library fix
            // ships, treat tenant mismatch as the auth failure it is —
            // otherwise a wrong AXONFLOW_CLIENT_ID (it must match the
            // user token's tenant) sails through the smoke with exit 0.
            if (e.getMessage() != null && e.getMessage().contains("Tenant mismatch")) {
                System.err.println("proxyLLMCall failed (auth): " + e.getMessage()
                        + " — AXONFLOW_CLIENT_ID must match the user token's tenant");
                System.exit(1);
            }
            // Genuine policy block is a valid outcome — community policies
            // can match the demo query depending on configuration.
            System.out.printf("  Blocked by policy: %s%n", e.getMessage());
        } catch (AuthenticationException | ConnectionException e) {
            // These are real failures: bad creds or stack down. Fail loud.
            System.err.println("proxyLLMCall failed: " + e.getMessage());
            System.exit(1);
        } catch (AxonFlowException e) {
            // Auth/token rejections are real failures (export AXONFLOW_USER_TOKEN
            // on JWT-validating stacks). Match on any auth-rejection phrasing
            // (case-insensitive) rather than one exact string, so a
            // differently-worded rejection isn't silently swallowed. Non-auth
            // SDK failures (e.g. "no LLM provider configured") — log and
            // continue; fully distinguishing them needs /health capability
            // detection, tracked in axonflow-sdk-java#146.
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("token") || msg.contains("unauthorized")
                    || msg.contains("authentication") || msg.contains("jwt")
                    || msg.contains("credential")) {
                System.err.println("proxyLLMCall failed (auth): " + e.getMessage());
                System.exit(1);
            }
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
        } catch (AxonFlowException e) {
            System.out.println("  listConnectors non-success: " + e.getMessage());
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
