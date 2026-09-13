// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.types.policies.PolicyTypes.EffectivePoliciesOptions;
import com.getaxonflow.sdk.types.policies.PolicyTypes.ListStaticPoliciesOptions;
import com.getaxonflow.sdk.types.policies.PolicyTypes.PolicyCategory;
import com.getaxonflow.sdk.types.policies.PolicyTypes.StaticPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Real-stack proof that a static-policy read no longer fails on the platform's categories.
 *
 * <p>A v11 platform answers {@code GET /api/v1/static-policies/effective} with policies whose
 * category this SDK's enum did not name ({@code security-dangerous}), and the strict enum failed
 * the whole read. This drives the built SDK against a real agent and asserts:
 *
 * <ol>
 *   <li>{@code getEffectiveStaticPolicies()} returns, and every policy's {@code
 *       getCategoryValue()} is exactly the {@code category} string the same agent sends on the
 *       wire, read a second time without the SDK and compared by policy id.
 *   <li>Every category the platform returns is one the SDK names, and its {@code
 *       security-dangerous} policies come back as {@code PolicyCategory.SECURITY_DANGEROUS}. This
 *       leg is the drift detector: a category the platform ships that the SDK lacks fails it, though
 *       at runtime such a category is kept as its string and callers are unaffected.
 *   <li>A list filter by that category, as its string and as the constant, returns only those
 *       policies; a filter by a category no platform names returns none, or a 400 or 422 refusal the SDK raises.
 *   <li>With credentials unset, and with a wrong secret, the read is served and matches the wire, or
 *       is refused with {@link AuthenticationException} (401): never a parse failure.
 * </ol>
 *
 * <p>Nothing is mocked, and nothing is written to the stack. Run with {@code run.sh}.
 */
public class StaticPolicyCategoriesTest {

  static final String AGENT =
      System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final String EFFECTIVE = "/api/v1/static-policies/effective";
  static final String LATER = "a-category-from-a-later-platform";
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

  static AxonFlow client(String clientId, String clientSecret) {
    AxonFlowConfig.Builder config =
        AxonFlowConfig.builder()
            .endpoint(AGENT)
            // The routes are deprecated and stamped; their report is not this proof's subject.
            .onRouteDeprecation(deprecation -> {});
    if (clientId != null) {
      config.clientId(clientId).clientSecret(clientSecret);
    }
    return AxonFlow.create(config.build());
  }

  /** The effective read without the SDK: policy id to the category member as the wire carries it. */
  static Map<String, String> wireCategories(String clientId, String clientSecret) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(AGENT + EFFECTIVE)).GET();
    if (clientId != null) {
      String basic = clientId + ":" + clientSecret;
      request.header(
          "Authorization",
          "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
    }
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("raw read answered " + response.statusCode());
    }
    Map<String, String> categories = new HashMap<>();
    for (JsonNode policy : new ObjectMapper().readTree(response.body()).path("static")) {
      JsonNode category = policy.get("category");
      categories.put(
          policy.path("id").asText(),
          category == null || category.isNull() ? null : category.asText());
    }
    return categories;
  }

  /** Every policy's category string is exactly the wire's; returns the ones the SDK does not name. */
  static Set<String> checkMatchesTheWire(
      String label, List<StaticPolicy> policies, Map<String, String> wire) {
    Set<String> unknown = new TreeSet<>();
    List<String> mismatched = new ArrayList<>();
    for (StaticPolicy policy : policies) {
      String sent = wire.get(policy.getId());
      if (!wire.containsKey(policy.getId())
          || !java.util.Objects.equals(sent, policy.getCategoryValue())) {
        mismatched.add(policy.getId() + ": wire " + sent + ", SDK " + policy.getCategoryValue());
      }
      if (policy.getCategory() == null) {
        unknown.add(String.valueOf(policy.getCategoryValue()));
      }
    }
    check(
        mismatched.isEmpty() && policies.size() == wire.size(),
        label
            + ": "
            + policies.size()
            + " policies, each category exactly as the wire sent it"
            + (mismatched.isEmpty() ? "" : " " + mismatched));
    return unknown;
  }

  public static void main(String[] args) {
    System.out.println("agent: " + AGENT);
    String clientId = env("AXONFLOW_CLIENT_ID", "runtime-e2e");
    String clientSecret = env("AXONFLOW_CLIENT_SECRET", "runtime-e2e-secret");
    AxonFlow client = client(clientId, clientSecret);

    try {
      // 1. The read that failed before this change, against the wire it decoded.
      List<StaticPolicy> policies = client.getEffectiveStaticPolicies();
      check(
          !policies.isEmpty(),
          "getEffectiveStaticPolicies() returned " + policies.size() + " policies");
      Map<String, Integer> counts = new TreeMap<>();
      for (StaticPolicy policy : policies) {
        String kind = policy.getCategory() == null ? "unknown" : "PolicyCategory";
        counts.merge(String.format("%-15s %s", kind, policy.getCategoryValue()), 1, Integer::sum);
      }
      counts.forEach((key, n) -> System.out.printf("  %3d  %s%n", n, key));
      Set<String> unknown =
          checkMatchesTheWire("effective", policies, wireCategories(clientId, clientSecret));
      check(
          unknown.isEmpty(),
          "every category this platform returns is one the SDK names (unknown: "
              + unknown
              + "); a red here is drift: regenerate tests/fixtures/shipped_posture_categories.json"
              + " from shipped_posture.json at this platform's sha and add the constant"
              + " (getaxonflow/axonflow-enterprise#4224). Runtime callers are unaffected: an unknown"
              + " category is kept as its string.");

      // 2. security-dangerous, the category the strict enum failed on.
      long dangerous =
          policies.stream().filter(p -> "security-dangerous".equals(p.getCategoryValue())).count();
      check(dangerous > 0, "the platform returns security-dangerous policies (" + dangerous + ")");
      check(
          policies.stream()
              .filter(p -> "security-dangerous".equals(p.getCategoryValue()))
              .allMatch(p -> p.getCategory() == PolicyCategory.SECURITY_DANGEROUS),
          "each comes back as PolicyCategory.SECURITY_DANGEROUS");

      // 3. The list route applies ?category=: by the string and by the constant, only those
      // policies; a category no platform names returns none, or a 400 or 422 refusal the SDK raises.
      List<StaticPolicy> byValue =
          client.listStaticPolicies(
              ListStaticPoliciesOptions.builder().categoryValue("security-dangerous").build());
      check(
          !byValue.isEmpty()
              && byValue.stream().allMatch(p -> p.getCategory() == PolicyCategory.SECURITY_DANGEROUS),
          "listStaticPolicies(categoryValue(\"security-dangerous\")) returned only"
              + " security-dangerous policies ("
              + byValue.size()
              + ")");
      List<StaticPolicy> byConstant = client.listStaticPolicies(PolicyCategory.SECURITY_DANGEROUS);
      check(
          byConstant.size() == byValue.size(),
          "listStaticPolicies(PolicyCategory.SECURITY_DANGEROUS) returned the same "
              + byConstant.size());
      try {
        List<StaticPolicy> none =
            client.listStaticPolicies(ListStaticPoliciesOptions.builder().categoryValue(LATER).build());
        check(
            none.isEmpty(),
            "listStaticPolicies(categoryValue(\"" + LATER + "\")) returned none (" + none.size() + ")");
      } catch (AxonFlowException e) {
        check(
            e.getStatusCode() == 400 || e.getStatusCode() == 422,
            "listStaticPolicies(categoryValue(\""
                + LATER
                + "\")) was refused with "
                + e.getStatusCode()
                + ", raised as "
                + e.getClass().getSimpleName());
      }

      // 4. The effective route declares no category parameter (agent-api.yaml), so the platform
      // does not filter by it: the filtered call completes and still matches the wire.
      List<StaticPolicy> effectiveFiltered =
          client.getEffectiveStaticPolicies(
              EffectivePoliciesOptions.builder().categoryValue("security-dangerous").build());
      checkMatchesTheWire(
          "getEffectiveStaticPolicies(categoryValue(\"security-dangerous\"))",
          effectiveFiltered,
          wireCategories(clientId, clientSecret));

      // 5. The unhappy path: credentials unset, and a wrong secret. Served and matching the wire,
      // or refused with the SDK's AuthenticationException carrying 401; never a parse failure.
      String[][] credentials = {{null, null}, {clientId, "not-the-secret"}};
      for (String[] credential : credentials) {
        String label = credential[0] == null ? "credentials unset" : "a wrong secret";
        try {
          List<StaticPolicy> read =
              client(credential[0], credential[1]).getEffectiveStaticPolicies();
          checkMatchesTheWire(
              "effective with " + label + " (served)",
              read,
              wireCategories(credential[0], credential[1]));
        } catch (AuthenticationException e) {
          check(
              e.getStatusCode() == 401,
              label + ": refused with AuthenticationException " + e.getStatusCode());
        }
      }
    } catch (Exception e) {
      check(false, "the run threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    if (!FAILURES.isEmpty()) {
      System.out.println(
          "\nFAIL: static_policy_categories (" + FAILURES.size() + " assertion(s))");
      System.exit(1);
    }
    System.out.println("\nPASS: static_policy_categories");
  }
}
