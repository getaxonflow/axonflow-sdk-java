// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A static policy's category: a known value reads as {@link PolicyCategory}, and an unknown one is
 * kept as the platform's string.
 *
 * <p>The platform ships and extends its categories as data, and a v11 platform returns categories
 * this SDK's enum did not name ({@code security-dangerous} on {@code GET
 * /api/v1/static-policies/effective}), which failed the whole read. The SDK's known set is pinned
 * to the categories the platform's shipped posture uses, vendored in {@code
 * tests/fixtures/shipped_posture_categories.json} with the platform commit and the source's sha256.
 * The spec's own enum is stale and is being made a free string
 * (getaxonflow/axonflow-enterprise#4224); until then the posture is the source this pin follows.
 * Once the fixture is regenerated from a newer platform commit, a category the platform added fails
 * a test here instead of failing a read. Nothing here watches the platform's file between
 * regenerations.
 */
@WireMockTest
@DisplayName("Static policy categories")
class StaticPolicyCategoryTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /** The categories the platform's shipped posture uses that this enum lacked before. */
  private static final List<String> ADDED =
      List.of(
          "security-dangerous",
          "compliance-euaiact",
          "dangerous_queries",
          "pii_detection",
          "sql_injection");

  private static final String LATER = "a-category-from-a-later-platform";

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(AxonFlowConfig.builder().endpoint(wmRuntimeInfo.getHttpBaseUrl()).build());
  }

  private static String policy(String id, String category) {
    return policyWith(id, "\"category\": \"" + category + "\", ");
  }

  /** A policy whose category member is {@code member} verbatim; an empty member leaves it out. */
  private static String policyWith(String id, String member) {
    return "{\"id\": \""
        + id
        + "\", \"name\": \"a policy\", "
        + member
        + "\"tier\": \"system\", \"pattern\": \"x\", \"severity\": \"high\","
        + " \"enabled\": true, \"action\": \"block\","
        + " \"created_at\": \"2026-09-13T00:00:00Z\", \"updated_at\": \"2026-09-13T00:00:00Z\"}";
  }

  private static void stubPolicy(String id, String category) {
    stubBody(id, policy(id, category));
  }

  private static void stubBody(String id, String body) {
    stubFor(
        get(urlEqualTo("/api/v1/static-policies/" + id))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private static JsonNode fixture() throws Exception {
    Path root = Paths.get(System.getProperty("basedir", System.getProperty("user.dir")));
    return MAPPER.readTree(
        Files.readAllBytes(root.resolve("tests/fixtures/shipped_posture_categories.json")));
  }

  @Test
  @DisplayName("a known category reads as the enum")
  void aKnownCategoryReadsAsTheEnum() {
    stubPolicy("pol_1", "security-sqli");

    StaticPolicy policy = axonflow.getStaticPolicy("pol_1");

    assertThat(policy.getCategory()).isEqualTo(PolicyCategory.SECURITY_SQLI);
    assertThat(policy.getCategoryValue()).isEqualTo("security-sqli");
  }

  @Test
  @DisplayName("each category the platform added reads as the enum")
  void eachAddedCategoryReadsAsTheEnum() {
    for (String value : ADDED) {
      stubPolicy("pol_" + value, value);

      StaticPolicy policy = axonflow.getStaticPolicy("pol_" + value);

      assertThat(policy.getCategory()).as(value).isNotNull();
      assertThat(policy.getCategory().getValue()).as(value).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("an unknown category keeps the platform's string and the read succeeds")
  void anUnknownCategoryKeepsThePlatformsString() {
    stubPolicy("pol_later", LATER);

    StaticPolicy policy = axonflow.getStaticPolicy("pol_later");

    assertThat(policy.getId()).isEqualTo("pol_later");
    assertThat(policy.getCategory()).isNull();
    assertThat(policy.getCategoryValue()).isEqualTo(LATER);
  }

  @Test
  @DisplayName("the effective read returns every policy, whatever its category")
  void theEffectiveReadReturnsEveryPolicy() {
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies/effective"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"static\": ["
                            + policy("pol_a", "security-dangerous")
                            + ", "
                            + policy("pol_b", LATER)
                            + ", "
                            + policy("pol_c", "pii-india")
                            + "], \"dynamic\": []}")));

    List<StaticPolicy> policies = axonflow.getEffectiveStaticPolicies();

    assertThat(policies)
        .extracting(StaticPolicy::getCategory)
        .containsExactly(PolicyCategory.SECURITY_DANGEROUS, null, PolicyCategory.PII_INDIA);
    assertThat(policies)
        .extracting(StaticPolicy::getCategoryValue)
        .containsExactly("security-dangerous", LATER, "pii-india");
  }

  @Test
  @DisplayName("every category the shipped posture uses is known")
  void everyCategoryTheShippedPostureUsesIsKnown() throws Exception {
    JsonNode fixture = fixture();
    Set<String> known =
        Arrays.stream(PolicyCategory.values())
            .map(PolicyCategory::getValue)
            .collect(Collectors.toSet());
    List<String> missing = new ArrayList<>();
    fixture
        .get("categories")
        .forEach(
            c -> {
              if (!known.contains(c.asText())) {
                missing.add(c.asText());
              }
            });

    assertThat(missing)
        .as(
            "the platform's shipped posture at %s uses categories PolicyCategory lacks: add the"
                + " constants and regenerate the fixture (getaxonflow/axonflow-enterprise#4224)",
            fixture.get("platform_commit").asText().substring(0, 9))
        .isEmpty();
  }

  @Test
  @DisplayName("the fixture names its source")
  void theFixtureNamesItsSource() throws Exception {
    JsonNode fixture = fixture();
    List<String> categories = new ArrayList<>();
    fixture.get("categories").forEach(c -> categories.add(c.asText()));

    assertThat(fixture.get("source").asText())
        .isEqualTo("platform/decision/pdp/shipped_posture.json");
    assertThat(fixture.get("platform_commit").asText()).matches("[0-9a-f]{40}");
    assertThat(fixture.get("source_sha256").asText()).matches("[0-9a-f]{64}");
    assertThat(categories).isNotEmpty().containsExactlyElementsOf(new TreeSet<>(categories));
  }

  @Test
  @DisplayName("lookup finds a known wire value and nothing else")
  void lookup() {
    assertThat(PolicyCategory.lookup("security-dangerous"))
        .contains(PolicyCategory.SECURITY_DANGEROUS);
    assertThat(PolicyCategory.lookup(LATER)).isEqualTo(Optional.empty());
    assertThat(PolicyCategory.lookup(null)).isEqualTo(Optional.empty());
  }

  @Test
  @DisplayName("a static policy writes its category as the wire value, known or not")
  void aStaticPolicyWritesItsCategoryAsTheWireValue() throws Exception {
    StaticPolicy known = new StaticPolicy();
    known.setCategory(PolicyCategory.PII_EU);
    JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(known));
    assertThat(written.get("category").asText()).isEqualTo("pii-eu");
    assertThat(written.has("categoryValue")).isFalse();

    StaticPolicy unknown = MAPPER.readValue(policy("pol_later", LATER), StaticPolicy.class);
    JsonNode rewritten = MAPPER.readTree(MAPPER.writeValueAsString(unknown));
    assertThat(rewritten.get("category").asText()).isEqualTo(LATER);
    assertThat(rewritten.has("categoryValue")).isFalse();
  }

  @Test
  @DisplayName("a list filter by wire value reaches the query")
  void aListFilterByWireValueReachesTheQuery() {
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"policies\": []}")));

    axonflow.listStaticPolicies(ListStaticPoliciesOptions.builder().categoryValue(LATER).build());
    axonflow.listStaticPolicies(
        ListStaticPoliciesOptions.builder().category(PolicyCategory.SECURITY_DANGEROUS).build());

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/static-policies"))
            .withQueryParam("category", equalTo(LATER)));
    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/static-policies"))
            .withQueryParam("category", equalTo("security-dangerous")));
  }

  @Test
  @DisplayName("an effective filter by wire value reaches the query")
  void anEffectiveFilterByWireValueReachesTheQuery() {
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies/effective"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"static\": [], \"dynamic\": []}")));

    axonflow.getEffectiveStaticPolicies(
        EffectivePoliciesOptions.builder().categoryValue(LATER).build());

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/static-policies/effective"))
            .withQueryParam("category", equalTo(LATER)));
  }

  @Test
  @DisplayName("a create or update sends the category's wire value, and no other member for it")
  void writesSendTheWireValue() {
    stubFor(
        post(urlEqualTo("/api/v1/static-policies"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(policy("pol_1", LATER))));
    stubFor(
        put(urlEqualTo("/api/v1/static-policies/pol_1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(policy("pol_1", "security-dangerous"))));

    axonflow.createStaticPolicy(
        CreateStaticPolicyRequest.builder().name("a").categoryValue(LATER).pattern("x").build());
    axonflow.updateStaticPolicy(
        "pol_1",
        UpdateStaticPolicyRequest.builder().category(PolicyCategory.SECURITY_DANGEROUS).build());

    verify(
        postRequestedFor(urlEqualTo("/api/v1/static-policies"))
            .withRequestBody(matchingJsonPath("$.category", equalTo(LATER)))
            .withRequestBody(notContaining("categoryValue")));
    verify(
        putRequestedFor(urlEqualTo("/api/v1/static-policies/pol_1"))
            .withRequestBody(matchingJsonPath("$.category", equalTo("security-dangerous")))
            .withRequestBody(notContaining("categoryValue")));
  }

  @Test
  @DisplayName("a request and an option read the category back as the enum or the wire value")
  void requestsAndOptionsReadTheCategoryBack() {
    CreateStaticPolicyRequest later =
        CreateStaticPolicyRequest.builder().categoryValue(LATER).build();
    UpdateStaticPolicyRequest known =
        UpdateStaticPolicyRequest.builder().category(PolicyCategory.SQL_INJECTION).build();
    EffectivePoliciesOptions cleared =
        EffectivePoliciesOptions.builder().categoryValue(LATER).category(null).build();

    assertThat(later.getCategory()).isNull();
    assertThat(later.getCategoryValue()).isEqualTo(LATER);
    assertThat(known.getCategory()).isEqualTo(PolicyCategory.SQL_INJECTION);
    assertThat(known.getCategoryValue()).isEqualTo("sql_injection");
    assertThat(cleared.getCategoryValue()).isNull();
  }

  @Test
  @DisplayName("the categories this SDK added are the shipped posture's")
  void theAddedCategoriesAreThePosturesCategories() throws Exception {
    List<String> categories = new ArrayList<>();
    fixture().get("categories").forEach(c -> categories.add(c.asText()));

    assertThat(categories).containsAll(ADDED);
  }

  @Test
  @DisplayName("an absent, null or empty category reads as no enum and as the string sent")
  void anAbsentNullOrEmptyCategoryReadsAsSent() throws Exception {
    stubBody("pol_absent", policyWith("pol_absent", ""));
    stubBody("pol_null", policyWith("pol_null", "\"category\": null, "));
    stubBody("pol_empty", policyWith("pol_empty", "\"category\": \"\", "));

    StaticPolicy absent = axonflow.getStaticPolicy("pol_absent");
    StaticPolicy nulled = axonflow.getStaticPolicy("pol_null");
    StaticPolicy empty = axonflow.getStaticPolicy("pol_empty");

    assertThat(absent.getCategory()).isNull();
    assertThat(absent.getCategoryValue()).isNull();
    assertThat(nulled.getCategory()).isNull();
    assertThat(nulled.getCategoryValue()).isNull();
    assertThat(empty.getCategory()).isNull();
    assertThat(empty.getCategoryValue()).isEmpty();

    // Written back, a missing category is a null member, as the enum field wrote it; an empty
    // string stays an empty string.
    assertThat(MAPPER.readTree(MAPPER.writeValueAsString(absent)).get("category").isNull())
        .isTrue();
    assertThat(MAPPER.readTree(MAPPER.writeValueAsString(nulled)).get("category").isNull())
        .isTrue();
    JsonNode writtenEmpty = MAPPER.readTree(MAPPER.writeValueAsString(empty)).get("category");
    assertThat(writtenEmpty.isTextual()).isTrue();
    assertThat(writtenEmpty.asText()).isEmpty();
  }

  @Test
  @DisplayName("an unknown category is written back unchanged, whatever its characters")
  void anUnknownCategoryIsWrittenBackUnchanged() throws Exception {
    // A case or whitespace variant of a known value is not that value: the platform's strings are
    // exact, and folding one onto a constant would change what is written back.
    List<String> values =
        List.of(
            LATER, "Security-Dangerous", "security-dangerous ", "cat\u00e9gorie-\u00fc", "a\"q\\v");
    for (String value : values) {
      String token = MAPPER.writeValueAsString(value);
      stubBody("pol_x", policyWith("pol_x", "\"category\": " + token + ", "));

      StaticPolicy read = axonflow.getStaticPolicy("pol_x");
      String written = MAPPER.writeValueAsString(read);

      assertThat(read.getCategory()).as(value).isNull();
      assertThat(read.getCategoryValue()).as(value).isEqualTo(value);
      assertThat(written).as(value).contains("\"category\":" + token);
      assertThat(written).as(value).doesNotContain("categoryValue");
    }
  }

  @Test
  @DisplayName("setting a static policy's category to null clears it")
  void settingTheCategoryToNullClearsIt() {
    StaticPolicy policy = new StaticPolicy();
    policy.setCategory(PolicyCategory.SQL_INJECTION);
    assertThat(policy.getCategoryValue()).isEqualTo("sql_injection");

    policy.setCategory(null);

    assertThat(policy.getCategory()).isNull();
    assertThat(policy.getCategoryValue()).isNull();
  }

  @Test
  @DisplayName("every builder's category(null) compiles and clears a category set by value")
  void everyBuildersCategoryNullClearsIt() {
    assertThat(
            ListStaticPoliciesOptions.builder()
                .categoryValue(LATER)
                .category(null)
                .build()
                .getCategoryValue())
        .isNull();
    assertThat(
            CreateStaticPolicyRequest.builder()
                .categoryValue(LATER)
                .category(null)
                .build()
                .getCategoryValue())
        .isNull();
    assertThat(
            UpdateStaticPolicyRequest.builder()
                .categoryValue(LATER)
                .category(null)
                .build()
                .getCategoryValue())
        .isNull();
    assertThat(
            EffectivePoliciesOptions.builder()
                .categoryValue(LATER)
                .category(null)
                .build()
                .getCategoryValue())
        .isNull();
  }

  @Test
  @DisplayName("every builder's categoryValue keeps the string, and getCategory maps it or not")
  void everyBuildersCategoryValueKeepsTheString() {
    ListStaticPoliciesOptions list =
        ListStaticPoliciesOptions.builder().categoryValue(LATER).build();
    CreateStaticPolicyRequest create =
        CreateStaticPolicyRequest.builder().categoryValue("pii_detection").build();
    UpdateStaticPolicyRequest update =
        UpdateStaticPolicyRequest.builder().categoryValue(LATER).build();
    EffectivePoliciesOptions effective =
        EffectivePoliciesOptions.builder().categoryValue("security-dangerous").build();

    assertThat(list.getCategoryValue()).isEqualTo(LATER);
    assertThat(list.getCategory()).isNull();
    assertThat(create.getCategoryValue()).isEqualTo("pii_detection");
    assertThat(create.getCategory()).isEqualTo(PolicyCategory.PII_DETECTION);
    assertThat(update.getCategoryValue()).isEqualTo(LATER);
    assertThat(update.getCategory()).isNull();
    assertThat(effective.getCategoryValue()).isEqualTo("security-dangerous");
    assertThat(effective.getCategory()).isEqualTo(PolicyCategory.SECURITY_DANGEROUS);
    assertThat(
            ListStaticPoliciesOptions.builder()
                .categoryValue("sql_injection")
                .build()
                .getCategory())
        .isEqualTo(PolicyCategory.SQL_INJECTION);
    assertThat(
            UpdateStaticPolicyRequest.builder()
                .categoryValue("dangerous_queries")
                .build()
                .getCategory())
        .isEqualTo(PolicyCategory.DANGEROUS_QUERIES);
  }

  @Test
  @DisplayName("no category sends a null member on a write and no parameter on a filter")
  void noCategorySendsANullMemberAndNoParameter() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/static-policies"))
            .willReturn(okJson(policy("pol_1", LATER)).withStatus(201)));
    stubFor(
        put(urlEqualTo("/api/v1/static-policies/pol_1"))
            .willReturn(okJson(policy("pol_1", LATER))));
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies")).willReturn(okJson("{\"policies\": []}")));
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies/effective"))
            .willReturn(okJson("{\"static\": [], \"dynamic\": []}")));

    axonflow.createStaticPolicy(CreateStaticPolicyRequest.builder().name("a").pattern("x").build());
    axonflow.updateStaticPolicy("pol_1", UpdateStaticPolicyRequest.builder().name("b").build());
    axonflow.listStaticPolicies(ListStaticPoliciesOptions.builder().build());
    axonflow.getEffectiveStaticPolicies(EffectivePoliciesOptions.builder().build());

    JsonNode created =
        MAPPER.readTree(
            findAll(postRequestedFor(urlEqualTo("/api/v1/static-policies")))
                .get(0)
                .getBodyAsString());
    JsonNode updated =
        MAPPER.readTree(
            findAll(putRequestedFor(urlEqualTo("/api/v1/static-policies/pol_1")))
                .get(0)
                .getBodyAsString());
    assertThat(created.has("category") && created.get("category").isNull()).isTrue();
    assertThat(updated.has("category") && updated.get("category").isNull()).isTrue();
    assertThat(findAll(getRequestedFor(urlPathEqualTo("/api/v1/static-policies"))).get(0).getUrl())
        .doesNotContain("category=");
    assertThat(
            findAll(getRequestedFor(urlPathEqualTo("/api/v1/static-policies/effective")))
                .get(0)
                .getUrl())
        .doesNotContain("category=");
  }

  @Test
  @DisplayName("an update sends a category the enum does not name as its string")
  void anUpdateSendsAnUnknownCategoryAsItsString() {
    stubFor(
        put(urlEqualTo("/api/v1/static-policies/pol_2"))
            .willReturn(okJson(policy("pol_2", LATER))));

    axonflow.updateStaticPolicy(
        "pol_2", UpdateStaticPolicyRequest.builder().categoryValue(LATER).build());

    verify(
        putRequestedFor(urlEqualTo("/api/v1/static-policies/pol_2"))
            .withRequestBody(matchingJsonPath("$.category", equalTo(LATER)))
            .withRequestBody(notContaining("categoryValue")));
  }

  @Test
  @DisplayName("a static policy built by hand can carry a category the enum does not name")
  void aStaticPolicyCanBeGivenACategoryByValue() throws Exception {
    StaticPolicy policy = new StaticPolicy();
    policy.setCategoryValue(LATER);

    assertThat(policy.getCategory()).isNull();
    assertThat(policy.getCategoryValue()).isEqualTo(LATER);
    JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(policy));
    assertThat(written.get("category").asText()).isEqualTo(LATER);
    assertThat(written.has("categoryValue")).isFalse();

    policy.setCategoryValue("security-dangerous");
    assertThat(policy.getCategory()).isEqualTo(PolicyCategory.SECURITY_DANGEROUS);
  }
}
