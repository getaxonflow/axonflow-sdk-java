// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.PlatformRouteDeprecation;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.LegacyPolicyWriteFrozenException;
import com.getaxonflow.sdk.simulation.ImpactReportInput;
import com.getaxonflow.sdk.simulation.ImpactReportRequest;
import com.getaxonflow.sdk.simulation.PolicyConflictResponse;
import com.getaxonflow.sdk.simulation.SimulatePoliciesRequest;
import com.getaxonflow.sdk.simulation.SimulatePoliciesResponse;
import com.getaxonflow.sdk.types.policies.PolicyTypes.CreatePolicyOverrideRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.OverrideAction;
import com.getaxonflow.sdk.types.policies.PolicyTypes.StaticPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Real-stack proof of the v11.0.0 deprecations through the SDK.
 *
 * <p>Drives the built SDK against a live v11 agent and orchestrator on the Evaluation licence or
 * above and asserts:
 *
 * <ol>
 *   <li>{@code simulatePolicies} and {@code detectPolicyConflicts} keep answering, and {@code
 *       getPolicyImpactReport} is refused with a non-2xx on a fresh v11 organization (never pinned
 *       to one status: getaxonflow/axonflow-enterprise#4223).
 *   <li>Each of the three routes is reported exactly once through {@code onRouteDeprecation}, with
 *       {@code X-AxonFlow-Removed-In: v11.1}, the successor exactly {@code /api/v1/typed-policies},
 *       and a {@code Deprecation} header absent or {@code @<unix seconds>}.
 *   <li>A second call of each, and a client derived with {@code asUser}, report nothing new.
 *   <li>{@code createPolicyOverride} and {@code deletePolicyOverride} throw {@code
 *       LegacyPolicyWriteFrozenException} (409) naming the typed route; the platform's message is
 *       printed.
 *   <li>Two static policies read by id are reported once, as {@code GET
 *       /api/v1/static-policies/{id}}, and no report names a concrete id.
 *   <li>A wrong secret is refused with the SDK's {@code AuthenticationException} (401).
 * </ol>
 *
 * <p>Nothing is mocked, and nothing the platform keeps is written: both override writes are
 * refused. Run with {@code run.sh}.
 */
@SuppressWarnings("deprecation")
public class V11DeprecationsTest {

  static final String AGENT =
      System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final String SUCCESSOR = "/api/v1/typed-policies";
  static final Pattern DEPRECATION = Pattern.compile("@[0-9]+");
  static final String[] ROUTES = {"simulate", "conflicts", "impact-report"};
  static final List<String> FAILURES = new ArrayList<>();

  static void check(boolean ok, String description) {
    System.out.println((ok ? "PASS: " : "FAIL: ") + description);
    if (!ok) {
      FAILURES.add(description);
    }
  }

  static String env(String name) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? null : value;
  }

  static AxonFlow client(
      String clientId, String clientSecret, Consumer<PlatformRouteDeprecation> listener) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(AGENT)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .onRouteDeprecation(listener)
            .build());
  }

  static SimulatePoliciesRequest simulate() {
    return SimulatePoliciesRequest.builder()
        .query("Transfer $50,000 to external account")
        .requestType("execute")
        .build();
  }

  /** The impact report a fresh v11 organization refuses; returns the answer's status. */
  static int impactStatus(AxonFlow client) {
    try {
      client.getPolicyImpactReport(
          ImpactReportRequest.builder()
              .policyId("sys_sqli_drop_table")
              .inputs(List.of(ImpactReportInput.builder().query("DROP TABLE customers").build()))
              .build());
      return 200;
    } catch (AxonFlowException e) {
      return e.getStatusCode();
    }
  }

  static void expectFrozen(String label, Runnable write) {
    try {
      write.run();
      check(false, label + " was accepted; a v11.0.0 platform refuses it");
    } catch (LegacyPolicyWriteFrozenException e) {
      System.out.println("  platform message: " + e.getMessage());
      check(
          e.getStatusCode() == 409
              && LegacyPolicyWriteFrozenException.CODE.equals(e.getErrorCode())
              && e.getMessage().contains(SUCCESSOR),
          label
              + " throws LegacyPolicyWriteFrozenException ("
              + e.getStatusCode()
              + ", "
              + e.getErrorCode()
              + ") naming "
              + SUCCESSOR);
    } catch (AxonFlowException e) {
      check(
          false,
          label
              + " threw "
              + e.getClass().getSimpleName()
              + " ("
              + e.getStatusCode()
              + ", "
              + e.getErrorCode()
              + "): "
              + e.getMessage());
    }
  }

  public static void main(String[] args) {
    String clientId = env("AXONFLOW_CLIENT_ID");
    String clientSecret = env("AXONFLOW_CLIENT_SECRET");
    String userToken = env("AXONFLOW_USER_TOKEN");
    System.out.println("agent: " + AGENT);
    if (clientId == null || clientSecret == null || userToken == null) {
      System.out.println(
          "FAIL: set AXONFLOW_CLIENT_ID, AXONFLOW_CLIENT_SECRET and AXONFLOW_USER_TOKEN"
              + " (source the stack's env file)");
      System.exit(2);
    }
    List<PlatformRouteDeprecation> reported = Collections.synchronizedList(new ArrayList<>());
    AxonFlow client = client(clientId, clientSecret, reported::add);

    try {
      // 1. The two routes that still answer, and the one a fresh organization refuses.
      SimulatePoliciesResponse simulated = client.simulatePolicies(simulate());
      // Reaching this check means the call answered 2xx: a refusal throws, and the outer catch
      // records it as a FAIL.
      check(
          true,
          "simulatePolicies() returned (allowed="
              + simulated.isAllowed()
              + ", "
              + simulated.getTotalPolicies()
              + " policies evaluated)");
      PolicyConflictResponse conflicts = client.detectPolicyConflicts();
      check(
          true,
          "detectPolicyConflicts() returned ("
              + conflicts.getConflictCount()
              + " conflicts in "
              + conflicts.getTotalPolicies()
              + " policies)");
      int status = impactStatus(client);
      check(
          status >= 400,
          "getPolicyImpactReport() is refused with a non-2xx on a fresh v11 organization ("
              + status
              + ")");

      // 2. Each route reported exactly once, with the platform's own signal.
      for (String route : ROUTES) {
        String key = "POST /api/v1/policies/" + route;
        List<PlatformRouteDeprecation> matches =
            reported.stream().filter(d -> key.equals(d.getRoute())).collect(Collectors.toList());
        check(matches.size() == 1, key + " is reported exactly once (" + matches.size() + ")");
        if (matches.isEmpty()) {
          continue;
        }
        PlatformRouteDeprecation d = matches.get(0);
        System.out.println("  reported: " + d);
        check(
            SUCCESSOR.equals(d.getSuccessor()),
            key + ": the successor is exactly " + SUCCESSOR + " (" + d.getSuccessor() + ")");
        check("v11.1".equals(d.getRemovedIn()), key + ": removed in v11.1 (" + d.getRemovedIn() + ")");
        check(
            d.getDeprecation() == null || DEPRECATION.matcher(d.getDeprecation()).matches(),
            key + ": Deprecation absent or @<unix seconds> (" + d.getDeprecation() + ")");
      }
      check(reported.size() == ROUTES.length, "nothing else is reported (" + reported.size() + ")");

      // 3. Once per route per client family: a second call, and a derived client, add nothing.
      int before = reported.size();
      client.simulatePolicies(simulate());
      client.detectPolicyConflicts();
      impactStatus(client);
      AxonFlow derived = client.asUser(userToken);
      derived.simulatePolicies(simulate());
      derived.detectPolicyConflicts();
      impactStatus(derived);
      check(
          reported.size() == before,
          "a second call of each, and a client derived with asUser, report nothing new ("
              + (reported.size() - before)
              + " new)");

      // 4. The retired per-policy override writes.
      expectFrozen(
          "createPolicyOverride",
          () ->
              client.createPolicyOverride(
                  "sys_sqli_drop_table",
                  CreatePolicyOverrideRequest.builder()
                      .actionOverride(OverrideAction.WARN)
                      .overrideReason("runtime-e2e v11_deprecations")
                      .build()));
      expectFrozen(
          "deletePolicyOverride", () -> client.deletePolicyOverride("sys_sqli_drop_table"));

      // 6. A stamped route that carries an id is reported once, as its template: two real static
      // policies read by id add one report, and no report names a concrete id.
      List<StaticPolicy> listed = client.listStaticPolicies();
      check(listed.size() >= 2, "listStaticPolicies() returned policies to read by id (" + listed.size() + ")");
      if (listed.size() >= 2) {
        List<String> ids = List.of(listed.get(0).getId(), listed.get(1).getId());
        for (String id : ids) {
          try {
            client.getStaticPolicy(id);
          } catch (AxonFlowException e) {
            System.out.println("  getStaticPolicy(" + id + ") answered " + e.getStatusCode());
          }
        }
        long byTemplate =
            reported.stream().filter(d -> "GET /api/v1/static-policies/{id}".equals(d.getRoute())).count();
        check(
            byTemplate == 1,
            "two static policies read by id are reported once, as GET /api/v1/static-policies/{id} ("
                + byTemplate
                + ")");
        check(
            reported.stream().noneMatch(d -> ids.stream().anyMatch(id -> d.getRoute().contains(id))),
            "no report names a concrete id");
      }

      // 5. An auth failure stays observable: a wrong secret is the SDK's AuthenticationException.
      try {
        client(clientId, "not-the-licence", d -> {}).simulatePolicies(simulate());
        check(false, "a wrong secret was accepted");
      } catch (AuthenticationException e) {
        check(
            e.getStatusCode() == 401,
            "a wrong secret is refused with AuthenticationException " + e.getStatusCode());
      } catch (AxonFlowException e) {
        check(
            false,
            "a wrong secret threw "
                + e.getClass().getSimpleName()
                + " ("
                + e.getStatusCode()
                + "), not AuthenticationException");
      }
    } catch (Exception e) {
      check(false, "the run threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    if (!FAILURES.isEmpty()) {
      System.out.println("\nFAIL: v11_deprecations (" + FAILURES.size() + " assertion(s))");
      System.exit(1);
    }
    System.out.println("\nPASS: v11_deprecations");
  }
}
