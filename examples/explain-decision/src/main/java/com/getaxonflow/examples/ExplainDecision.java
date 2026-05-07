// Copyright 2026 AxonFlow
// SPDX-License-Identifier: Apache-2.0
//
// AxonFlow Java SDK — explain a previously-made policy decision.
//
// Implements the ADR-043 explainability flow. Given a decision_id (typically
// surfaced on the response of a blocked governed call, an audit_logs row, or
// the `explain_decision` MCP tool), this example fetches the structured
// explanation and renders the matched policies, risk level, and override
// availability.
//
// Required env vars:
//   AXONFLOW_AGENT_URL       (default: http://localhost:8080)
//   AXONFLOW_CLIENT_ID       (default: community)
//   AXONFLOW_CLIENT_SECRET   (default: empty)
//   AXONFLOW_DECISION_ID     the decision to explain
//
// Run from this directory after `mvn install -DskipTests` at the SDK root:
//
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.DecisionExplanation;
import com.getaxonflow.sdk.types.ExplainPolicy;
import com.getaxonflow.sdk.types.ExplainRule;

public class ExplainDecision {

    public static void main(String[] args) {
        String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String clientId = envOrDefault("AXONFLOW_CLIENT_ID", "community");
        String clientSecret = envOrDefault("AXONFLOW_CLIENT_SECRET", "");
        String decisionId = System.getenv("AXONFLOW_DECISION_ID");

        if (decisionId == null || decisionId.isEmpty()) {
            System.err.println(
                    "AXONFLOW_DECISION_ID must be set "
                            + "(a decision_id from a recent blocked call)");
            System.exit(2);
        }

        System.out.println("Initializing AxonFlow client at " + endpoint + "...");
        try (AxonFlow client =
                AxonFlow.create(
                        AxonFlowConfig.builder()
                                .agentUrl(endpoint)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .build())) {

            System.out.println("Explaining decision " + decisionId + "...\n");
            DecisionExplanation exp = client.explainDecision(decisionId);

            System.out.println("=== Decision Explanation ===");
            System.out.println("  decision_id: " + exp.getDecisionId());
            System.out.println("  timestamp:   " + exp.getTimestamp());
            System.out.println("  decision:    " + exp.getDecision());
            System.out.println("  reason:      " + exp.getReason());
            if (exp.getRiskLevel() != null && !exp.getRiskLevel().isEmpty()) {
                System.out.println("  risk_level:  " + exp.getRiskLevel());
            }
            if (exp.getToolSignature() != null && !exp.getToolSignature().isEmpty()) {
                System.out.println("  tool:        " + exp.getToolSignature());
            }

            System.out.printf("%n  policy_matches (%d):%n", exp.getPolicyMatches().size());
            int i = 0;
            for (ExplainPolicy m : exp.getPolicyMatches()) {
                String name = m.getPolicyName() != null ? m.getPolicyName() : "(unnamed)";
                String action = m.getAction() != null ? m.getAction() : "-";
                String risk = m.getRiskLevel() != null ? m.getRiskLevel() : "-";
                System.out.printf(
                        "    [%d] %s (%s) — action=%s risk=%s allow_override=%b%n",
                        i, m.getPolicyId(), name, action, risk, m.isAllowOverride());
                i++;
            }

            if (exp.getMatchedRules() != null && !exp.getMatchedRules().isEmpty()) {
                System.out.printf("%n  matched_rules (%d):%n", exp.getMatchedRules().size());
                for (ExplainRule r : exp.getMatchedRules()) {
                    String ruleId = r.getRuleId() != null ? r.getRuleId() : "(no rule id)";
                    String matchedOn = r.getMatchedOn() != null ? r.getMatchedOn() : "-";
                    System.out.printf(
                            "    %s on %s: matched=%s%n", r.getPolicyId(), ruleId, matchedOn);
                }
            }

            System.out.printf("%n  override_available:           %b%n",
                    exp.isOverrideAvailable());
            if (exp.getOverrideExistingId() != null && !exp.getOverrideExistingId().isEmpty()) {
                System.out.println("  override_existing_id:         "
                        + exp.getOverrideExistingId());
            }
            System.out.println("  historical_hit_count_session: "
                    + exp.getHistoricalHitCountSession());
            if (exp.getPolicySourceLink() != null && !exp.getPolicySourceLink().isEmpty()) {
                System.out.println("  policy_source_link:           "
                        + exp.getPolicySourceLink());
            }
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
