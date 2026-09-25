// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.PlatformRouteDeprecation;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.LegacyPolicyWriteFrozenException;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import com.getaxonflow.sdk.types.PolicyApprovalRequest;
import com.getaxonflow.sdk.types.PolicyApprovalResult;
import com.getaxonflow.sdk.types.PolicyIdentity;
import com.getaxonflow.sdk.types.policies.PolicyTypes.CreateDynamicPolicyRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.CreateStaticPolicyRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.DynamicPolicy;
import com.getaxonflow.sdk.types.policies.PolicyTypes.DynamicPolicyAction;
import com.getaxonflow.sdk.types.policies.PolicyTypes.DynamicPolicyCondition;
import com.getaxonflow.sdk.types.policies.PolicyTypes.PolicyCategory;
import com.getaxonflow.sdk.types.policies.PolicyTypes.PolicySeverity;
import com.getaxonflow.sdk.types.policies.PolicyTypes.StaticPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Real-stack proof that the SDK surfaces the v11.0.0 platform wire, through its public surface
 * against a real v11 agent and orchestrator. Nothing is mocked.
 *
 * <ol>
 *   <li>{@code decide} carries engine {@code anchored}, a policy-bundle digest and a subject type,
 *       and its policy identities name the evaluated policies one for one, in order, at least one
 *       of them a shipped control.
 *   <li>The gateway pre-check carries a decision id, a verdict of allow or deny, and the same
 *       provenance.
 *   <li>MCP check-output carries the provenance.
 *   <li>A legacy static-policy read is reported once through {@code onRouteDeprecation}, naming
 *       {@code /api/v1/typed-policies} as the successor and {@code v12.0} as the removal release.
 *   <li>A valid legacy static-policy write and a valid dynamic-policy write each throw {@code
 *       LegacyPolicyWriteFrozenException}.
 * </ol>
 *
 * <p>Leg 5 needs the agent and the orchestrator on the application database role, as a deployment
 * runs them: the freeze is a revoke on that role, and a stack connected as the database owner is
 * not bound by it. See README.md. Run with {@code run.sh}.
 */
public class V11DecisionProvenanceTest {

  static final String AGENT =
      System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final List<String> FAILURES = new ArrayList<>();

  static void check(boolean ok, String description) {
    System.out.println((ok ? "PASS: " : "FAIL: ") + description);
    if (!ok) {
      FAILURES.add(description);
    }
  }

  static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? fallback : value;
  }

  public static void main(String[] args) {
    System.out.println("agent: " + AGENT);
    List<PlatformRouteDeprecation> reported = Collections.synchronizedList(new ArrayList<>());
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(AGENT)
                .clientId(env("AXONFLOW_CLIENT_ID", "runtime-e2e"))
                .clientSecret(env("AXONFLOW_CLIENT_SECRET", "runtime-e2e-secret"))
                .onRouteDeprecation(reported::add)
                .build());

    run("decide", () -> decideLeg(client));
    run("pre-check", () -> preCheckLeg(client));
    run("MCP check-output", () -> checkOutputLeg(client));
    run("deprecated read", () -> deprecatedReadLeg(client, reported));
    run("frozen writes", () -> frozenWriteLeg(client));

    if (!FAILURES.isEmpty()) {
      System.out.println(
          "\nFAIL: v11_decision_provenance (" + FAILURES.size() + " assertion(s))");
      System.exit(1);
    }
    System.out.println("\nPASS: v11_decision_provenance");
    System.exit(0);
  }

  static void run(String leg, Runnable body) {
    System.out.println("== " + leg);
    try {
      body.run();
    } catch (AxonFlowException e) {
      check(false, leg + " raised " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  static void decideLeg(AxonFlow client) {
    DecideResponse resp =
        client.decide(
            DecideRequest.builder("tool", "look up the weather")
                .target(new DecisionTarget("tool", null, null, "search"))
                .build());
    System.out.println(
        "  decide: verdict="
            + resp.getVerdict()
            + " engine="
            + resp.getEngine()
            + " subject_type="
            + resp.getSubjectType()
            + " policy_bundle="
            + resp.getPolicyBundle()
            + " evaluated="
            + resp.getEvaluatedPolicies());
    System.out.println(
        "  decide: identities="
            + resp.getPolicyIdentities()
            + " packs="
            + resp.getPolicyPacks()
            + " document_version="
            + resp.getDocumentVersion());
    check("anchored".equals(resp.getEngine()), "decide names the anchored engine");
    check(resp.getPolicyBundle() != null, "decide carries the policy bundle digest");
    check(resp.getSubjectType() != null, "decide carries the subject type");
    List<String> ids = new ArrayList<>();
    boolean shipped = false;
    for (PolicyIdentity p : resp.getPolicyIdentities()) {
      ids.add(p.getId());
      shipped |= "shipped".equals(p.getSource());
    }
    check(
        !ids.isEmpty() && ids.equals(resp.getEvaluatedPolicies()),
        "decide's policy identities name the evaluated policies one for one, in order");
    check(shipped, "decide's policy identities name at least one shipped control");
  }

  static void preCheckLeg(AxonFlow client) {
    // An enterprise agent validates the user token as a JWT and refuses a malformed one with 401;
    // the setup script writes a real one as AXONFLOW_USER_TOKEN.
    PolicyApprovalResult result =
        client.getPolicyApprovedContext(
            PolicyApprovalRequest.builder()
                .userToken(env("AXONFLOW_USER_TOKEN", "tok"))
                .query("hello")
                .build());
    System.out.println(
        "  pre-check: approved="
            + result.isApproved()
            + " decision_id="
            + result.getDecisionId()
            + " verdict="
            + result.getVerdict()
            + " engine="
            + result.getEngine()
            + " subject_type="
            + result.getSubjectType()
            + " policy_bundle="
            + result.getPolicyBundle());
    check(result.getDecisionId() != null, "pre-check carries the decision id");
    check(
        "allow".equals(result.getVerdict()) || "deny".equals(result.getVerdict()),
        "pre-check carries the canonical verdict");
    check("anchored".equals(result.getEngine()), "pre-check names the anchored engine");
    check(result.getPolicyBundle() != null, "pre-check carries the policy bundle digest");
  }

  static void checkOutputLeg(AxonFlow client) {
    MCPCheckOutputResponse resp =
        client.mcpCheckOutput("postgres", null, Map.of("message", "hello"));
    System.out.println(
        "  mcp check-output: allowed="
            + resp.isAllowed()
            + " engine="
            + resp.getEngine()
            + " subject_type="
            + resp.getSubjectType()
            + " policy_bundle="
            + resp.getPolicyBundle());
    check("anchored".equals(resp.getEngine()), "MCP check-output names the anchored engine");
    check(resp.getPolicyBundle() != null, "MCP check-output carries the policy bundle digest");
  }

  static void deprecatedReadLeg(AxonFlow client, List<PlatformRouteDeprecation> reported) {
    int before = reported.size();
    client.listStaticPolicies();
    List<PlatformRouteDeprecation> staticReports = new ArrayList<>();
    for (PlatformRouteDeprecation d : new ArrayList<>(reported).subList(before, reported.size())) {
      System.out.println("  reported: " + d);
      if ("GET /api/v1/static-policies".equals(d.getRoute())) {
        staticReports.add(d);
      }
    }
    check(
        staticReports.size() == 1,
        "a legacy static-policy read is reported once through onRouteDeprecation");
    if (staticReports.size() == 1) {
      check(
          "/api/v1/typed-policies".equals(staticReports.get(0).getSuccessor()),
          "the report names the typed route as the successor");
      check(
          "v12.0".equals(staticReports.get(0).getRemovedIn()),
          "the report names v12.0 as the removal release");
    }
  }

  static void frozenWriteLeg(AxonFlow client) {
    String probe = "w3p-runtime-probe-" + Long.toHexString(System.nanoTime() & 0xffffffffL);
    try {
      StaticPolicy created =
          client.createStaticPolicy(
              CreateStaticPolicyRequest.builder()
                  .name(probe)
                  .category(PolicyCategory.SECURITY_SQLI)
                  .pattern("(?i)" + probe.replace('-', '_'))
                  .severity(PolicySeverity.LOW)
                  .build());
      check(false, "a legacy static-policy write throws the frozen exception (it succeeded)");
      // The freeze did not bind: remove the probe rather than leave it behind.
      client.deleteStaticPolicy(created.getId());
    } catch (LegacyPolicyWriteFrozenException e) {
      System.out.println("  static write refused: " + e.getMessage());
      check(true, "a legacy static-policy write throws LegacyPolicyWriteFrozenException");
    } catch (AxonFlowException e) {
      check(
          false,
          "a legacy static-policy write throws LegacyPolicyWriteFrozenException (got "
              + e.getClass().getSimpleName()
              + ": "
              + e.getMessage()
              + ")");
    }
    try {
      DynamicPolicy created =
          client.createDynamicPolicy(
              CreateDynamicPolicyRequest.builder()
                  .name(probe)
                  .type("risk")
                  .category("dynamic-risk")
                  .conditions(
                      List.of(new DynamicPolicyCondition("risk_score", "greater_than", 0.99)))
                  .actions(List.of(new DynamicPolicyAction("log", Map.of())))
                  .build());
      check(false, "a legacy dynamic-policy write throws the frozen exception (it succeeded)");
      client.deleteDynamicPolicy(created.getId());
    } catch (LegacyPolicyWriteFrozenException e) {
      System.out.println("  dynamic write refused: " + e.getMessage());
      check(true, "a legacy dynamic-policy write throws LegacyPolicyWriteFrozenException");
    } catch (AxonFlowException e) {
      check(
          false,
          "a legacy dynamic-policy write throws LegacyPolicyWriteFrozenException (got "
              + e.getClass().getSimpleName()
              + ": "
              + e.getMessage()
              + ")");
    }
  }
}
