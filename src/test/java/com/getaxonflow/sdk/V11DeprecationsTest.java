// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.LegacyPolicyWriteFrozenException;
import com.getaxonflow.sdk.simulation.ImpactReportInput;
import com.getaxonflow.sdk.simulation.ImpactReportRequest;
import com.getaxonflow.sdk.simulation.SimulatePoliciesRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.CreatePolicyOverrideRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.OverrideAction;
import com.getaxonflow.sdk.types.policies.PolicyTypes.UpdateDynamicPolicyRequest;
import com.getaxonflow.sdk.types.policies.PolicyTypes.UpdateStaticPolicyRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The v11.0.0 deprecations: the three policy simulation routes keep answering until v12.0 and are
 * reported once per route with the platform's own signal, and the retired per-policy override
 * writes throw the typed frozen exception.
 *
 * <p>The stubs carry the headers a v11 platform stamps on these routes today, as sdk-go #233's live
 * run read them from enterprise {@code 857455033}: {@code X-AxonFlow-Removed-In: v12.0} and {@code
 * Link: </api/v1/typed-policies>; rel="successor-version"}, and no {@code Deprecation} header,
 * because the platform's {@code policypath.DeprecatedSince} stays empty until v11.0.0 is tagged.
 */
@WireMockTest
@SuppressWarnings("deprecation")
@DisplayName("v11.0.0 deprecations: the simulation routes and the retired override writes")
class V11DeprecationsTest {

  private static final String SUCCESSOR = "/api/v1/typed-policies";

  /**
   * The platform's refusal of a per-policy override write: agent-api's {@code
   * PerPolicyOverrideRetired} response, whose message is {@code legacyfreeze.OverrideMessage}.
   */
  private static final String OVERRIDE_RETIRED =
      "{\"error\":{\"code\":\"LEGACY_POLICY_WRITE_FROZEN\",\"message\":\"Per-policy overrides of"
          + " system controls are retired in v11: a system control is enabled, disabled or"
          + " re-actioned in the organization's typed document, in its system_controls section,"
          + " through the typed authoring route at /api/v1/typed-policies. Session break-glass"
          + " overrides (ADR-044) are unaffected, and reads on this endpoint are unaffected.\"}}";

  private static final List<String> ROUTES = List.of("simulate", "impact-report", "conflicts");

  private final List<PlatformRouteDeprecation> reported =
      Collections.synchronizedList(new ArrayList<>());

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wm.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .onRouteDeprecation(reported::add)
                .build());
    for (String route : ROUTES) {
      stubFor(
          post(urlEqualTo("/api/v1/policies/" + route))
              .willReturn(
                  okJson("{}")
                      .withHeader("X-AxonFlow-Removed-In", "v12.0")
                      .withHeader("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"")));
    }
  }

  private static SimulatePoliciesRequest simulate() {
    return SimulatePoliciesRequest.builder().query("q").requestType("execute").build();
  }

  private static ImpactReportRequest impact() {
    return ImpactReportRequest.builder()
        .policyId("p")
        .inputs(List.of(ImpactReportInput.builder().query("q").build()))
        .build();
  }

  @Test
  @DisplayName("the simulation methods work, and each route is reported once with no Deprecation")
  void theSimulationMethodsWorkAndEachRouteIsReportedOnce() throws Exception {
    for (int i = 0; i < 2; i++) {
      assertThat(axonflow.simulatePolicies(simulate())).isNotNull();
      assertThat(axonflow.simulatePoliciesAsync(simulate()).get()).isNotNull();
      assertThat(axonflow.getPolicyImpactReport(impact())).isNotNull();
      assertThat(axonflow.getPolicyImpactReportAsync(impact()).get()).isNotNull();
      assertThat(axonflow.detectPolicyConflicts()).isNotNull();
      assertThat(axonflow.detectPolicyConflicts("p")).isNotNull();
      assertThat(axonflow.detectPolicyConflictsAsync().get()).isNotNull();
      assertThat(axonflow.detectPolicyConflictsAsync("p").get()).isNotNull();
    }
    AxonFlow derived = axonflow.asUser("someone");
    derived.simulatePolicies(simulate());
    derived.getPolicyImpactReport(impact());
    derived.detectPolicyConflicts();

    // Every response carried the headers; the client's record, not a cache, kept it to one report.
    verify(5, postRequestedFor(urlEqualTo("/api/v1/policies/simulate")));
    verify(5, postRequestedFor(urlEqualTo("/api/v1/policies/impact-report")));
    verify(9, postRequestedFor(urlEqualTo("/api/v1/policies/conflicts")));
    assertThat(reported)
        .containsExactly(
            new PlatformRouteDeprecation(
                "POST /api/v1/policies/simulate", SUCCESSOR, "v12.0", null),
            new PlatformRouteDeprecation(
                "POST /api/v1/policies/impact-report", SUCCESSOR, "v12.0", null),
            new PlatformRouteDeprecation(
                "POST /api/v1/policies/conflicts", SUCCESSOR, "v12.0", null));
  }

  @Test
  @DisplayName("every public method that reaches a simulation route is @Deprecated, plainly")
  void everyMethodReachingTheSimulationRoutesIsDeprecated() throws Exception {
    List<Method> reaching =
        Arrays.stream(AxonFlow.class.getMethods())
            .filter(
                m ->
                    m.getName().startsWith("simulatePolicies")
                        || m.getName().startsWith("getPolicyImpactReport")
                        || m.getName().startsWith("detectPolicyConflicts"))
            .collect(Collectors.toList());

    // The census, so a method added under one of these names cannot slip past the loop below. A
    // method under a new name that reaches these routes is not caught here; the client still
    // reports
    // its route at runtime through onRouteDeprecation.
    assertThat(reaching.stream().map(Method::toGenericString).sorted())
        .hasSize(8)
        .contains(
            AxonFlow.class
                .getMethod("simulatePolicies", SimulatePoliciesRequest.class)
                .toGenericString(),
            AxonFlow.class.getMethod("detectPolicyConflictsAsync", String.class).toGenericString());
    for (Method method : reaching) {
      Deprecated deprecated = method.getAnnotation(Deprecated.class);
      assertThat(deprecated).as(method.toGenericString()).isNotNull();
      // The removal is the platform's (v12.0), and an SDK version is the release's to name.
      assertThat(deprecated.forRemoval()).as(method.toGenericString()).isFalse();
      assertThat(deprecated.since()).as(method.toGenericString()).isEmpty();
    }
    assertThat(
            AxonFlow.class
                .getMethod("createPolicyOverride", String.class, CreatePolicyOverrideRequest.class)
                .getAnnotation(Deprecated.class))
        .isNull();
    assertThat(
            AxonFlow.class
                .getMethod("deletePolicyOverride", String.class)
                .getAnnotation(Deprecated.class))
        .isNull();
  }

  @Test
  @DisplayName("the retired override writes throw the frozen exception naming the typed route")
  void theRetiredOverrideWritesThrowTheFrozenException() {
    String path = "/api/v1/static-policies/sys_sqli_drop_table/override";
    stubFor(
        post(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OVERRIDE_RETIRED)));
    stubFor(
        delete(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OVERRIDE_RETIRED)));

    List<ThrowingCallable> writes =
        List.of(
            () ->
                axonflow.createPolicyOverride(
                    "sys_sqli_drop_table",
                    CreatePolicyOverrideRequest.builder()
                        .actionOverride(OverrideAction.WARN)
                        .overrideReason("a reason")
                        .build()),
            () -> axonflow.deletePolicyOverride("sys_sqli_drop_table"));
    for (ThrowingCallable write : writes) {
      Throwable thrown = catchThrowable(write);
      assertThat(thrown)
          .isInstanceOf(LegacyPolicyWriteFrozenException.class)
          .hasMessageContaining(SUCCESSOR)
          .hasMessageContaining("system_controls");
      AxonFlowException refused = (AxonFlowException) thrown;
      assertThat(refused.getStatusCode()).isEqualTo(409);
      assertThat(refused.getErrorCode()).isEqualTo(LegacyPolicyWriteFrozenException.CODE);
    }
    // A refusal is an answer, not a transient: neither write is retried.
    verify(1, postRequestedFor(urlEqualTo(path)));
    verify(1, deleteRequestedFor(urlEqualTo(path)));
  }

  @Test
  @DisplayName("a stamped route that carries an id is reported once, as its template, not per id")
  void aStampedRouteWithAnIdIsReportedOnceNotOncePerId() {
    stubFor(
        post(urlMatching("/api/v1/static-policies/[^/]+/override"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-AxonFlow-Removed-In", "v12.0")
                    .withHeader("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"")
                    .withBody(OVERRIDE_RETIRED)));

    for (String id : List.of("sys_a", "sys_b")) {
      catchThrowable(
          () ->
              axonflow.createPolicyOverride(
                  id,
                  CreatePolicyOverrideRequest.builder()
                      .actionOverride(OverrideAction.WARN)
                      .overrideReason("a reason")
                      .build()));
    }

    verify(2, postRequestedFor(urlMatching("/api/v1/static-policies/[^/]+/override")));
    assertThat(reported)
        .containsExactly(
            new PlatformRouteDeprecation(
                "POST /api/v1/static-policies/{id}/override", SUCCESSOR, "v12.0", null));
  }

  @Test
  @DisplayName("every method that puts an id in a stamped route reports the route's template once")
  void everyIdBearingMethodReportsItsTemplateOnce() {
    stubFor(
        any(urlPathMatching("/api/v1/(static|dynamic)-policies/.+"))
            .willReturn(
                okJson("{}")
                    .withHeader("X-AxonFlow-Removed-In", "v12.0")
                    .withHeader("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"")));

    for (String id : List.of("pol_a", "pol_b")) {
      catchThrowable(() -> axonflow.getStaticPolicy(id));
      catchThrowable(
          () ->
              axonflow.updateStaticPolicy(
                  id, UpdateStaticPolicyRequest.builder().name("n").build()));
      catchThrowable(() -> axonflow.deleteStaticPolicy(id));
      catchThrowable(() -> axonflow.toggleStaticPolicy(id, true));
      catchThrowable(() -> axonflow.getStaticPolicyVersions(id));
      catchThrowable(
          () ->
              axonflow.createPolicyOverride(
                  id,
                  CreatePolicyOverrideRequest.builder()
                      .actionOverride(OverrideAction.WARN)
                      .build()));
      catchThrowable(() -> axonflow.deletePolicyOverride(id));
      catchThrowable(() -> axonflow.getDynamicPolicy(id));
      catchThrowable(
          () ->
              axonflow.updateDynamicPolicy(
                  id, UpdateDynamicPolicyRequest.builder().name("n").build()));
      catchThrowable(() -> axonflow.deleteDynamicPolicy(id));
      catchThrowable(() -> axonflow.toggleDynamicPolicy(id, true));
    }

    // Each call sent its own request, with its id in its path, so a method that shares its template
    // with another (updateDynamicPolicy and toggleDynamicPolicy are both PUT
    // /api/v1/dynamic-policies/{id}) is seen to send: exactly 22 requests, in call order.
    List<String> templates =
        List.of(
            "GET /api/v1/static-policies/{id}",
            "PUT /api/v1/static-policies/{id}",
            "DELETE /api/v1/static-policies/{id}",
            "PATCH /api/v1/static-policies/{id}",
            "GET /api/v1/static-policies/{id}/versions",
            "POST /api/v1/static-policies/{id}/override",
            "DELETE /api/v1/static-policies/{id}/override",
            "GET /api/v1/dynamic-policies/{id}",
            "PUT /api/v1/dynamic-policies/{id}",
            "DELETE /api/v1/dynamic-policies/{id}",
            "PUT /api/v1/dynamic-policies/{id}");
    List<String> expected = new ArrayList<>();
    for (String id : List.of("pol_a", "pol_b")) {
      for (String template : templates) {
        expected.add(template.replace("{id}", id));
      }
    }
    // WireMock lists serve events newest first.
    List<String> served =
        getAllServeEvents().stream()
            .map(e -> e.getRequest().getMethod().getName() + " " + e.getRequest().getUrl())
            .collect(Collectors.toList());
    Collections.reverse(served);
    assertThat(served).containsExactlyElementsOf(expected);

    // Eleven methods, twice each; updateDynamicPolicy and toggleDynamicPolicy share PUT.
    assertThat(reported)
        .extracting(PlatformRouteDeprecation::getRoute)
        .containsExactlyInAnyOrder(
            "GET /api/v1/static-policies/{id}",
            "PUT /api/v1/static-policies/{id}",
            "DELETE /api/v1/static-policies/{id}",
            "PATCH /api/v1/static-policies/{id}",
            "GET /api/v1/static-policies/{id}/versions",
            "POST /api/v1/static-policies/{id}/override",
            "DELETE /api/v1/static-policies/{id}/override",
            "GET /api/v1/dynamic-policies/{id}",
            "PUT /api/v1/dynamic-policies/{id}",
            "DELETE /api/v1/dynamic-policies/{id}");
  }

  @Test
  @DisplayName("every id concatenated into a stamped route's path carries the route's template")
  void everyConcatenatedStampedPathCarriesItsTemplate() throws Exception {
    java.nio.file.Path root =
        java.nio.file.Paths.get(System.getProperty("basedir", System.getProperty("user.dir")));
    String source =
        new String(
            java.nio.file.Files.readAllBytes(
                root.resolve("src/main/java/com/getaxonflow/sdk/AxonFlow.java")),
            java.nio.charset.StandardCharsets.UTF_8);
    // The v11 deprecated families (the platform's policypath.deprecatedFamilies, both spellings).
    // It sees a family path concatenated with `+` only; one built with String.format, a
    // StringBuilder or HttpUrl.Builder is not seen, and would be reported once per id.
    java.util.regex.Matcher concatenated =
        java.util.regex.Pattern.compile(
                "\"/api/v1/(static-policies|system-policies|dynamic-policies|tenant-policies"
                    + "|policies|templates|policy-overrides)/\"\\s*\\+")
            .matcher(source);
    List<String> untagged = new ArrayList<>();
    int found = 0;
    while (concatenated.find()) {
      found++;
      int statement =
          Math.max(
              source.lastIndexOf(';', concatenated.start()),
              source.lastIndexOf('{', concatenated.start()));
      if (!source.substring(statement, concatenated.start()).contains("withRoute(")) {
        int line = source.substring(0, concatenated.start()).split("\n", -1).length;
        untagged.add("AxonFlow.java:" + line);
      }
    }

    assertThat(found).isEqualTo(11);
    assertThat(untagged).as("a stamped route built with an id but no route template").isEmpty();
  }
}
