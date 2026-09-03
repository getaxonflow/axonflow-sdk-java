// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
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
//   AXONFLOW_USER_TOKEN      the PER-USER identity this read is scoped to
//                            (required on an enterprise stack — see below)
//
// Optional:
//   AXONFLOW_DECISION_ID     the decision to explain. When unset this example
//                            asks the platform for the most recent decision
//                            THIS identity can see.
//
// # Why AXONFLOW_USER_TOKEN is not optional here (platform #2922)
//
// clientId/clientSecret say which ORGANIZATION is asking. Explain answers from
// WHO is asking. On an enterprise stack a developer or viewer explains only
// their own decisions, a tenant-wide role (admin/owner/policy_admin) explains
// the whole tenant, and a caller presenting NO identity explains NOTHING — the
// endpoint answers not-found for every id, including ids that plainly exist.
// That is why this example failed on every enterprise stack until the SDK grew
// a read-path identity: it was asking anonymously.
//
// Mint one the way the E2E workflow does:
//
//   export AXONFLOW_USER_TOKEN=$(./scripts/generate-jwt.sh --kind user \
//       --email dev@acme.com --org-id "$AXONFLOW_CLIENT_ID" --role developer --quiet)
//
// (./scripts/setup-e2e-testing.sh already exports exactly this variable.)
// Community deployments are single-operator and need none of it.
//
// Run from this directory after `mvn install -DskipTests` at the SDK root:
//
//   mvn -q compile exec:java
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.DecisionExplanation;
import com.getaxonflow.sdk.types.ExplainPolicy;
import com.getaxonflow.sdk.exceptions.ReadScopeException;
import com.getaxonflow.sdk.types.ExplainRule;

public class ExplainDecision {

    /**
     * The sentence a reader of this example actually needs. Without it the
     * distinct causes behind "not found" arrive looking identical.
     */
    private static String scopeHint(ReadScopeException e) {
        if (e.isIdentityMissing()) {
            return "\n  -> This read presented no per-user identity the platform could resolve, so"
                    + " it returned nothing by construction. Set AXONFLOW_USER_TOKEN (see the file"
                    + " header) - and check the address is not in a reserved domain.";
        }
        return "\n  -> The identity in AXONFLOW_USER_TOKEN is scoped to its own rows and this"
                + " decision is not among them. Use an admin, owner or policy_admin token to read"
                + " the whole tenant.";
    }

    public static void main(String[] args) {
        String endpoint = envOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String clientId = envOrDefault("AXONFLOW_CLIENT_ID", "community");
        String clientSecret = envOrDefault("AXONFLOW_CLIENT_SECRET", "");
        String decisionId = System.getenv("AXONFLOW_DECISION_ID");
        String userToken = System.getenv("AXONFLOW_USER_TOKEN");

        System.out.println("Initializing AxonFlow client at " + endpoint + "...");
        if (userToken == null || userToken.isEmpty()) {
            System.out.println(
                    "note: AXONFLOW_USER_TOKEN is unset - this read is unscoped. On an "
                            + "enterprise stack it will explain nothing; see the file header.");
        }
        try (AxonFlow client =
                AxonFlow.create(
                        AxonFlowConfig.builder()
                                .agentUrl(endpoint)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                // The read-path identity. Empty is legal and means "ask
                                // anonymously", which on an enterprise stack explains nothing.
                                .userToken(userToken)
                                .build())) {

            // No id given: ask for one this identity can actually see, so the
            // example explains a real decision rather than failing on a placeholder.
            if (decisionId == null || decisionId.isEmpty()) {
                System.out.println(
                        "AXONFLOW_DECISION_ID is unset - looking up the most recent visible"
                                + " decision...");
                java.util.List<com.getaxonflow.sdk.types.DecisionSummary> recent;
                try {
                    recent =
                            client.listDecisions(
                                    com.getaxonflow.sdk.types.ListDecisionsOptions.builder()
                                            .limit(1)
                                            .build());
                } catch (ReadScopeException e) {
                    System.err.println(
                            "could not find a decision to explain: " + e.getMessage() + scopeHint(e));
                    System.exit(1);
                    return;
                }
                if (recent.isEmpty()) {
                    System.err.println(
                            "no decisions are visible to this identity yet - make a governed call"
                                    + " first, then re-run");
                    System.exit(1);
                    return;
                }
                decisionId = recent.get(0).getDecisionId();
                System.out.println("  using decision_id=" + decisionId);
            }

            System.out.println("Explaining decision " + decisionId + "...\n");
            DecisionExplanation exp;
            try {
                exp = client.explainDecision(decisionId);
            } catch (ReadScopeException e) {
                System.err.println("explainDecision failed: " + e.getMessage() + scopeHint(e));
                System.exit(1);
                return;
            }

            // An explanation that came back without the id it was asked about is
            // not an explanation - fail loudly rather than print an empty report.
            if (exp.getDecisionId() == null || exp.getDecisionId().isEmpty()) {
                System.err.println(
                        "the platform returned an explanation with no decision_id for "
                                + decisionId);
                System.exit(1);
                return;
            }

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
