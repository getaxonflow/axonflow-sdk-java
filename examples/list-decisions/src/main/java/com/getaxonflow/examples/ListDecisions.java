// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// Operator example for AxonFlow.listDecisions (Session γ / #1982).
// Implements the GET /api/v1/decisions contract — companion to
// explainDecision. Surfaces the V1 upgrade envelope on RateLimitException
// so callers can branch on tier-cap upgrade context.
//
// Run from this directory after `mvn install -DskipTests` at the SDK root:
//
//   export AXONFLOW_AGENT_URL=http://localhost:8080
//   export AXONFLOW_CLIENT_ID=...
//   export AXONFLOW_CLIENT_SECRET=...
//   mvn -q compile exec:java
//
// Optional filters via env:
//   AXONFLOW_LIST_DECISION       allow|deny|require_approval
//   AXONFLOW_LIST_POLICY_ID      e.g. sys_sqli_stacked_drop
//   AXONFLOW_LIST_LIMIT          integer (server-capped per tier)
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.RateLimitException;
import com.getaxonflow.sdk.types.DecisionSummary;
import com.getaxonflow.sdk.types.ListDecisionsOptions;

import java.util.List;

public class ListDecisions {

  public static void main(String[] args) {
    String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
    if (clientId == null || clientSecret == null) {
      System.err.println("AXONFLOW_CLIENT_ID and AXONFLOW_CLIENT_SECRET must be set");
      System.exit(1);
      return;
    }

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    ListDecisionsOptions.Builder b = ListDecisionsOptions.builder();
    String d = System.getenv("AXONFLOW_LIST_DECISION");
    if (d != null) b.decision(d);
    String p = System.getenv("AXONFLOW_LIST_POLICY_ID");
    if (p != null) b.policyId(p);
    String l = System.getenv("AXONFLOW_LIST_LIMIT");
    if (l != null) {
      try {
        b.limit(Integer.parseInt(l));
      } catch (NumberFormatException ignored) {
        // ignore — leave unset
      }
    }

    try {
      List<DecisionSummary> decisions = client.listDecisions(b.build());
      System.out.printf("=== Recent decisions (%d) ===%n", decisions.size());
      for (DecisionSummary ds : decisions) {
        String policy = ds.getPolicyId() != null ? ds.getPolicyId() : "-";
        String tool = ds.getToolSignature() != null ? ds.getToolSignature() : "-";
        System.out.printf(
            "  %s %-18s %s policy=%s tool=%s%n",
            ds.getTimestamp(), ds.getDecision(), ds.getDecisionId(), policy, tool);
      }
    } catch (RateLimitException rle) {
      System.err.printf("=== Tier limit reached (%s) ===%n", rle.getLimitType());
      System.err.printf("  current tier: %s%n", rle.getTier());
      System.err.printf("  limit:        %d%n", rle.getLimit());
      System.err.printf("  reason:       %s%n", rle.getMessage());
      if (rle.getUpgrade() != null) {
        System.err.println();
        System.err.printf(
            "  upgrade to %s: %s%n", rle.getUpgrade().getTier(), rle.getUpgrade().getWording());
        System.err.printf("    compare:    %s%n", rle.getUpgrade().getCompareUrl());
        System.err.printf("    buy:        %s%n", rle.getUpgrade().getBuyUrl());
      }
      System.exit(2);
    }
  }

  private static String envOrDefault(String name, String fallback) {
    String v = System.getenv(name);
    return v != null ? v : fallback;
  }
}
