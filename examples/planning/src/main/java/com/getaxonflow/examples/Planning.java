// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// Minimal AxonFlow Java SDK planning example.
//
// This example requires a local AxonFlow stack with an LLM provider configured.
//
// Run from this directory:
//
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.PlanRequest;
import com.getaxonflow.sdk.types.PlanResponse;

public class Planning {

    public static void main(String[] args) {
        String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String clientId = envOrDefault("AXONFLOW_CLIENT_ID", "community");
        String clientSecret = envOrDefault("AXONFLOW_CLIENT_SECRET", "");

        try (AxonFlow client =
                AxonFlow.create(
                        AxonFlowConfig.builder()
                                .endpoint(endpoint)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .build())) {
            PlanRequest request =
                    PlanRequest.builder()
                            .objective("Research a topic and summarize the key findings")
                            .build();

            System.out.println("Generating plan...");
            PlanResponse plan = client.generatePlan(request);
            System.out.println("Plan generated: " + plan.getPlanId());

            System.out.println("Executing plan...");
            PlanResponse result = client.executePlan(plan.getPlanId());
            System.out.println("Plan execution status: " + result.getStatus());
            if (result.getResult() != null) {
                System.out.println("Result: " + result.getResult());
            }
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
