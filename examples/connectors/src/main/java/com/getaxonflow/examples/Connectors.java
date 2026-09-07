// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// Minimal read-only AxonFlow Java SDK connectors example.
//
// Run from this directory:
//
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.ConnectorInfo;

import java.util.List;

public class Connectors {

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
            List<ConnectorInfo> connectors = client.listConnectors();
            System.out.printf("Found %d connectors%n", connectors.size());
            for (ConnectorInfo connector : connectors) {
                System.out.printf(
                        "- id=%s name=%s type=%s version=%s installed=%s%n",
                        connector.getId(),
                        connector.getName(),
                        connector.getType(),
                        connector.getVersion(),
                        connector.isInstalled());
            }
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
