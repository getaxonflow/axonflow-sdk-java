// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// Basic AxonFlow Java SDK smoke example.
//
// Demonstrates:
//   - Client initialization from environment
//   - Health check against the agent
//   - A protected proxyLLMCall round-trip
//
// Run from this directory after `mvn install -DskipTests` at the SDK root:
//
//   export AXONFLOW_AGENT_URL=http://localhost:8080
//   export AXONFLOW_CLIENT_ID=demo-client
//   export AXONFLOW_CLIENT_SECRET=demo-secret
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.types.HealthStatus;
import com.getaxonflow.sdk.types.RequestType;

public class Basic {

    public static void main(String[] args) {
        String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String clientId = envOrDefault("AXONFLOW_CLIENT_ID", "demo-client");
        String clientSecret = envOrDefault("AXONFLOW_CLIENT_SECRET", "demo-secret");

        System.out.println("Initializing AxonFlow client...");
        AxonFlow client =
                AxonFlow.create(
                        AxonFlowConfig.builder()
                                .agentUrl(endpoint)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .debug(true)
                                .build());

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

        System.out.println("\n============================================================");
        System.out.println("Step 2: Protected proxyLLMCall");
        System.out.println("============================================================");
        try {
            ClientRequest request =
                    ClientRequest.builder()
                            .query("What is the capital of France?")
                            .userToken("demo-user")
                            .clientId(clientId)
                            .requestType(RequestType.CHAT)
                            .build();

            ClientResponse response = client.proxyLLMCall(request);
            System.out.printf("  Success: %s%n", response.isSuccess());
            System.out.printf("  Blocked: %s%n", response.isBlocked());
        } catch (Exception e) {
            // Community stack often runs without an LLM provider configured;
            // a fail-open or 503 is normal here. Don't fail the smoke for it.
            System.out.println("  (proxyLLMCall returned non-success — expected on community without LLM): "
                    + e.getMessage());
        }

        System.out.println("\nBasic example complete.");
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
