/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Policy CRUD methods.
 * Part of Unified Policy Architecture v2.0.0.
 */
@WireMockTest
@DisplayName("Policy CRUD Methods")
class PolicyTest {

    private AxonFlow axonflow;

    private static final String SAMPLE_STATIC_POLICY =
        "{" +
        "\"id\": \"pol_123\"," +
        "\"name\": \"Block SQL Injection\"," +
        "\"description\": \"Blocks SQL injection attempts\"," +
        "\"category\": \"security-sqli\"," +
        "\"tier\": \"system\"," +
        "\"pattern\": \"(?i)(union\\\\s+select|drop\\\\s+table)\"," +
        "\"severity\": 9," +
        "\"enabled\": true," +
        "\"action\": \"block\"," +
        "\"created_at\": \"2025-01-01T00:00:00Z\"," +
        "\"updated_at\": \"2025-01-01T00:00:00Z\"," +
        "\"version\": 1" +
        "}";

    private static final String SAMPLE_DYNAMIC_POLICY =
        "{" +
        "\"id\": \"dpol_456\"," +
        "\"name\": \"Rate Limit API\"," +
        "\"description\": \"Rate limit API calls\"," +
        "\"category\": \"dynamic-cost\"," +
        "\"tier\": \"organization\"," +
        "\"enabled\": true," +
        "\"config\": {" +
        "\"type\": \"rate-limit\"," +
        "\"rules\": {\"maxRequestsPerMinute\": 100}," +
        "\"action\": \"block\"" +
        "}," +
        "\"created_at\": \"2025-01-01T00:00:00Z\"," +
        "\"updated_at\": \"2025-01-01T00:00:00Z\"," +
        "\"version\": 1" +
        "}";

    private static final String SAMPLE_OVERRIDE =
        "{" +
        "\"policy_id\": \"pol_123\"," +
        "\"action\": \"warn\"," +
        "\"reason\": \"Testing override\"," +
        "\"created_at\": \"2025-01-01T00:00:00Z\"," +
        "\"active\": true" +
        "}";

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        axonflow = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
            .build());
    }

    // ========================================================================
    // Static Policy Tests
    // ========================================================================

    @Nested
    @DisplayName("Static Policies")
    class StaticPolicies {

        @Test
        @DisplayName("listStaticPolicies should return policies")
        void listStaticPoliciesShouldReturnPolicies() {
            stubFor(get(urlPathEqualTo("/api/v1/static-policies"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_STATIC_POLICY + "]")));

            List<StaticPolicy> policies = axonflow.listStaticPolicies();

            assertThat(policies).hasSize(1);
            assertThat(policies.get(0).getId()).isEqualTo("pol_123");
            assertThat(policies.get(0).getName()).isEqualTo("Block SQL Injection");
        }

        @Test
        @DisplayName("listStaticPolicies with filters should include query params")
        void listStaticPoliciesWithFiltersShouldIncludeQueryParams() {
            stubFor(get(urlPathEqualTo("/api/v1/static-policies"))
                .withQueryParam("category", equalTo("security-sqli"))
                .withQueryParam("tier", equalTo("system"))
                .withQueryParam("enabled", equalTo("true"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_STATIC_POLICY + "]")));

            ListStaticPoliciesOptions options = ListStaticPoliciesOptions.builder()
                .category(PolicyCategory.SECURITY_SQLI)
                .tier(PolicyTier.SYSTEM)
                .enabled(true)
                .build();

            List<StaticPolicy> policies = axonflow.listStaticPolicies(options);

            assertThat(policies).hasSize(1);
        }

        @Test
        @DisplayName("getStaticPolicy should return policy by ID")
        void getStaticPolicyShouldReturnPolicyById() {
            stubFor(get(urlEqualTo("/api/v1/static-policies/pol_123"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_STATIC_POLICY)));

            StaticPolicy policy = axonflow.getStaticPolicy("pol_123");

            assertThat(policy.getId()).isEqualTo("pol_123");
            assertThat(policy.getCategory()).isEqualTo(PolicyCategory.SECURITY_SQLI);
        }

        @Test
        @DisplayName("getStaticPolicy should require non-null policyId")
        void getStaticPolicyShouldRequirePolicyId() {
            assertThatThrownBy(() -> axonflow.getStaticPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("createStaticPolicy should create and return policy")
        void createStaticPolicyShouldCreateAndReturnPolicy() {
            stubFor(post(urlEqualTo("/api/v1/static-policies"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_STATIC_POLICY)));

            CreateStaticPolicyRequest request = CreateStaticPolicyRequest.builder()
                .name("Block SQL Injection")
                .category(PolicyCategory.SECURITY_SQLI)
                .pattern("(?i)(union\\\\s+select|drop\\\\s+table)")
                .severity(9)
                .build();

            StaticPolicy policy = axonflow.createStaticPolicy(request);

            assertThat(policy.getId()).isEqualTo("pol_123");

            verify(postRequestedFor(urlEqualTo("/api/v1/static-policies"))
                .withHeader("Content-Type", containing("application/json")));
        }

        @Test
        @DisplayName("createStaticPolicy should require non-null request")
        void createStaticPolicyShouldRequireRequest() {
            assertThatThrownBy(() -> axonflow.createStaticPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("updateStaticPolicy should update and return policy")
        void updateStaticPolicyShouldUpdateAndReturnPolicy() {
            String updatedPolicy = SAMPLE_STATIC_POLICY.replace("\"severity\": 9", "\"severity\": 10");
            stubFor(put(urlEqualTo("/api/v1/static-policies/pol_123"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(updatedPolicy)));

            UpdateStaticPolicyRequest request = UpdateStaticPolicyRequest.builder()
                .severity(10)
                .build();

            StaticPolicy policy = axonflow.updateStaticPolicy("pol_123", request);

            assertThat(policy.getSeverity()).isEqualTo(10);

            verify(putRequestedFor(urlEqualTo("/api/v1/static-policies/pol_123")));
        }

        @Test
        @DisplayName("deleteStaticPolicy should delete policy")
        void deleteStaticPolicyShouldDeletePolicy() {
            stubFor(delete(urlEqualTo("/api/v1/static-policies/pol_123"))
                .willReturn(aResponse()
                    .withStatus(204)));

            axonflow.deleteStaticPolicy("pol_123");

            verify(deleteRequestedFor(urlEqualTo("/api/v1/static-policies/pol_123")));
        }

        @Test
        @DisplayName("deleteStaticPolicy should require non-null policyId")
        void deleteStaticPolicyShouldRequirePolicyId() {
            assertThatThrownBy(() -> axonflow.deleteStaticPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toggleStaticPolicy should toggle enabled status")
        void toggleStaticPolicyShouldToggleEnabledStatus() {
            String toggledPolicy = SAMPLE_STATIC_POLICY.replace("\"enabled\": true", "\"enabled\": false");
            stubFor(patch(urlEqualTo("/api/v1/static-policies/pol_123"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toggledPolicy)));

            StaticPolicy policy = axonflow.toggleStaticPolicy("pol_123", false);

            assertThat(policy.isEnabled()).isFalse();

            verify(patchRequestedFor(urlEqualTo("/api/v1/static-policies/pol_123"))
                .withRequestBody(containing("\"enabled\":false")));
        }

        @Test
        @DisplayName("getEffectiveStaticPolicies should return effective policies")
        void getEffectiveStaticPoliciesShouldReturnEffectivePolicies() {
            stubFor(get(urlPathEqualTo("/api/v1/static-policies/effective"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_STATIC_POLICY + "]")));

            List<StaticPolicy> policies = axonflow.getEffectiveStaticPolicies();

            assertThat(policies).hasSize(1);
        }

        @Test
        @DisplayName("testPattern should test pattern against inputs")
        void testPatternShouldTestPatternAgainstInputs() {
            String responseBody =
                "{" +
                "\"valid\": true," +
                "\"results\": [" +
                "{\"input\": \"SELECT * FROM users\", \"matched\": true}," +
                "{\"input\": \"Hello world\", \"matched\": false}" +
                "]" +
                "}";

            stubFor(post(urlEqualTo("/api/v1/static-policies/test-pattern"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));

            TestPatternResult result = axonflow.testPattern(
                "(?i)select",
                Arrays.asList("SELECT * FROM users", "Hello world")
            );

            assertThat(result.isValid()).isTrue();
            assertThat(result.getResults()).hasSize(2);
            assertThat(result.getResults().get(0).isMatched()).isTrue();
            assertThat(result.getResults().get(1).isMatched()).isFalse();
        }

        @Test
        @DisplayName("testPattern should require non-null parameters")
        void testPatternShouldRequireParameters() {
            assertThatThrownBy(() -> axonflow.testPattern(null, Arrays.asList("test")))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> axonflow.testPattern("pattern", null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getStaticPolicyVersions should return version history")
        void getStaticPolicyVersionsShouldReturnVersionHistory() {
            String responseBody =
                "[" +
                "{\"version\": 2, \"changed_at\": \"2025-01-02T00:00:00Z\", \"change_type\": \"updated\"}," +
                "{\"version\": 1, \"changed_at\": \"2025-01-01T00:00:00Z\", \"change_type\": \"created\"}" +
                "]";

            stubFor(get(urlEqualTo("/api/v1/static-policies/pol_123/versions"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));

            List<PolicyVersion> versions = axonflow.getStaticPolicyVersions("pol_123");

            assertThat(versions).hasSize(2);
            assertThat(versions.get(0).getVersion()).isEqualTo(2);
        }
    }

    // ========================================================================
    // Policy Override Tests
    // ========================================================================

    @Nested
    @DisplayName("Policy Overrides")
    class PolicyOverrides {

        @Test
        @DisplayName("createPolicyOverride should create override")
        void createPolicyOverrideShouldCreateOverride() {
            stubFor(post(urlEqualTo("/api/v1/static-policies/pol_123/override"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_OVERRIDE)));

            CreatePolicyOverrideRequest request = CreatePolicyOverrideRequest.builder()
                .action(OverrideAction.WARN)
                .reason("Testing override")
                .build();

            PolicyOverride override = axonflow.createPolicyOverride("pol_123", request);

            assertThat(override.getAction()).isEqualTo(OverrideAction.WARN);
            assertThat(override.getReason()).isEqualTo("Testing override");

            verify(postRequestedFor(urlEqualTo("/api/v1/static-policies/pol_123/override")));
        }

        @Test
        @DisplayName("createPolicyOverride should require non-null parameters")
        void createPolicyOverrideShouldRequireParameters() {
            CreatePolicyOverrideRequest request = CreatePolicyOverrideRequest.builder()
                .action(OverrideAction.WARN)
                .build();

            assertThatThrownBy(() -> axonflow.createPolicyOverride(null, request))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> axonflow.createPolicyOverride("pol_123", null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getPolicyOverride should return override")
        void getPolicyOverrideShouldReturnOverride() {
            stubFor(get(urlEqualTo("/api/v1/static-policies/pol_123/override"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_OVERRIDE)));

            PolicyOverride override = axonflow.getPolicyOverride("pol_123");

            assertThat(override).isNotNull();
            assertThat(override.getAction()).isEqualTo(OverrideAction.WARN);
        }

        @Test
        @DisplayName("getPolicyOverride should return null when not found")
        void getPolicyOverrideShouldReturnNullWhenNotFound() {
            stubFor(get(urlEqualTo("/api/v1/static-policies/pol_123/override"))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withBody("{\"error\": \"Not found\"}")));

            PolicyOverride override = axonflow.getPolicyOverride("pol_123");

            assertThat(override).isNull();
        }

        @Test
        @DisplayName("deletePolicyOverride should delete override")
        void deletePolicyOverrideShouldDeleteOverride() {
            stubFor(delete(urlEqualTo("/api/v1/static-policies/pol_123/override"))
                .willReturn(aResponse()
                    .withStatus(204)));

            axonflow.deletePolicyOverride("pol_123");

            verify(deleteRequestedFor(urlEqualTo("/api/v1/static-policies/pol_123/override")));
        }

        @Test
        @DisplayName("deletePolicyOverride should require non-null policyId")
        void deletePolicyOverrideShouldRequirePolicyId() {
            assertThatThrownBy(() -> axonflow.deletePolicyOverride(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ========================================================================
    // Dynamic Policy Tests
    // ========================================================================

    @Nested
    @DisplayName("Dynamic Policies")
    class DynamicPolicies {

        @Test
        @DisplayName("listDynamicPolicies should return policies")
        void listDynamicPoliciesShouldReturnPolicies() {
            stubFor(get(urlPathEqualTo("/api/v1/dynamic-policies"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_DYNAMIC_POLICY + "]")));

            List<DynamicPolicy> policies = axonflow.listDynamicPolicies();

            assertThat(policies).hasSize(1);
            assertThat(policies.get(0).getId()).isEqualTo("dpol_456");
            assertThat(policies.get(0).getName()).isEqualTo("Rate Limit API");
        }

        @Test
        @DisplayName("listDynamicPolicies with filters should include query params")
        void listDynamicPoliciesWithFiltersShouldIncludeQueryParams() {
            stubFor(get(urlPathEqualTo("/api/v1/dynamic-policies"))
                .withQueryParam("category", equalTo("dynamic-cost"))
                .withQueryParam("enabled", equalTo("true"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_DYNAMIC_POLICY + "]")));

            ListDynamicPoliciesOptions options = ListDynamicPoliciesOptions.builder()
                .category(PolicyCategory.DYNAMIC_COST)
                .enabled(true)
                .build();

            axonflow.listDynamicPolicies(options);

            verify(getRequestedFor(urlPathEqualTo("/api/v1/dynamic-policies"))
                .withQueryParam("category", equalTo("dynamic-cost")));
        }

        @Test
        @DisplayName("getDynamicPolicy should return policy by ID")
        void getDynamicPolicyShouldReturnPolicyById() {
            stubFor(get(urlEqualTo("/api/v1/dynamic-policies/dpol_456"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_DYNAMIC_POLICY)));

            DynamicPolicy policy = axonflow.getDynamicPolicy("dpol_456");

            assertThat(policy.getId()).isEqualTo("dpol_456");
            assertThat(policy.getConfig().getType()).isEqualTo("rate-limit");
        }

        @Test
        @DisplayName("getDynamicPolicy should require non-null policyId")
        void getDynamicPolicyShouldRequirePolicyId() {
            assertThatThrownBy(() -> axonflow.getDynamicPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("createDynamicPolicy should create and return policy")
        void createDynamicPolicyShouldCreateAndReturnPolicy() {
            stubFor(post(urlEqualTo("/api/v1/dynamic-policies"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_DYNAMIC_POLICY)));

            CreateDynamicPolicyRequest request = CreateDynamicPolicyRequest.builder()
                .name("Rate Limit API")
                .category(PolicyCategory.DYNAMIC_COST)
                .config(DynamicPolicyConfig.builder()
                    .type("rate-limit")
                    .rules(Map.of("maxRequestsPerMinute", 100))
                    .action(PolicyAction.BLOCK)
                    .build())
                .build();

            DynamicPolicy policy = axonflow.createDynamicPolicy(request);

            assertThat(policy.getId()).isEqualTo("dpol_456");

            verify(postRequestedFor(urlEqualTo("/api/v1/dynamic-policies")));
        }

        @Test
        @DisplayName("createDynamicPolicy should require non-null request")
        void createDynamicPolicyShouldRequireRequest() {
            assertThatThrownBy(() -> axonflow.createDynamicPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("updateDynamicPolicy should update and return policy")
        void updateDynamicPolicyShouldUpdateAndReturnPolicy() {
            stubFor(put(urlEqualTo("/api/v1/dynamic-policies/dpol_456"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(SAMPLE_DYNAMIC_POLICY)));

            UpdateDynamicPolicyRequest request = UpdateDynamicPolicyRequest.builder()
                .config(DynamicPolicyConfig.builder()
                    .type("rate-limit")
                    .rules(Map.of("maxRequestsPerMinute", 200))
                    .action(PolicyAction.BLOCK)
                    .build())
                .build();

            DynamicPolicy policy = axonflow.updateDynamicPolicy("dpol_456", request);

            assertThat(policy).isNotNull();

            verify(putRequestedFor(urlEqualTo("/api/v1/dynamic-policies/dpol_456")));
        }

        @Test
        @DisplayName("deleteDynamicPolicy should delete policy")
        void deleteDynamicPolicyShouldDeletePolicy() {
            stubFor(delete(urlEqualTo("/api/v1/dynamic-policies/dpol_456"))
                .willReturn(aResponse()
                    .withStatus(204)));

            axonflow.deleteDynamicPolicy("dpol_456");

            verify(deleteRequestedFor(urlEqualTo("/api/v1/dynamic-policies/dpol_456")));
        }

        @Test
        @DisplayName("deleteDynamicPolicy should require non-null policyId")
        void deleteDynamicPolicyShouldRequirePolicyId() {
            assertThatThrownBy(() -> axonflow.deleteDynamicPolicy(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toggleDynamicPolicy should toggle enabled status")
        void toggleDynamicPolicyShouldToggleEnabledStatus() {
            String toggledPolicy = SAMPLE_DYNAMIC_POLICY.replace("\"enabled\": true", "\"enabled\": false");
            stubFor(patch(urlEqualTo("/api/v1/dynamic-policies/dpol_456"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toggledPolicy)));

            DynamicPolicy policy = axonflow.toggleDynamicPolicy("dpol_456", false);

            assertThat(policy.isEnabled()).isFalse();

            verify(patchRequestedFor(urlEqualTo("/api/v1/dynamic-policies/dpol_456"))
                .withRequestBody(containing("\"enabled\":false")));
        }

        @Test
        @DisplayName("getEffectiveDynamicPolicies should return effective policies")
        void getEffectiveDynamicPoliciesShouldReturnEffectivePolicies() {
            stubFor(get(urlPathEqualTo("/api/v1/dynamic-policies/effective"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" + SAMPLE_DYNAMIC_POLICY + "]")));

            List<DynamicPolicy> policies = axonflow.getEffectiveDynamicPolicies();

            assertThat(policies).hasSize(1);
        }
    }

    // ========================================================================
    // Type Validation Tests
    // ========================================================================

    @Nested
    @DisplayName("Policy Types")
    class PolicyTypes {

        @Test
        @DisplayName("CreateStaticPolicyRequest should have defaults")
        void createStaticPolicyRequestShouldHaveDefaults() {
            CreateStaticPolicyRequest request = CreateStaticPolicyRequest.builder()
                .name("Test Policy")
                .category(PolicyCategory.PII_GLOBAL)
                .pattern("\\d{3}-\\d{2}-\\d{4}")
                .build();

            assertThat(request.getName()).isEqualTo("Test Policy");
            assertThat(request.getCategory()).isEqualTo(PolicyCategory.PII_GLOBAL);
            assertThat(request.isEnabled()).isTrue();
            assertThat(request.getSeverity()).isEqualTo(5);
            assertThat(request.getAction()).isEqualTo(PolicyAction.BLOCK);
        }

        @Test
        @DisplayName("CreateDynamicPolicyRequest should have defaults")
        void createDynamicPolicyRequestShouldHaveDefaults() {
            CreateDynamicPolicyRequest request = CreateDynamicPolicyRequest.builder()
                .name("Test Dynamic")
                .category(PolicyCategory.DYNAMIC_RISK)
                .config(DynamicPolicyConfig.builder()
                    .type("custom")
                    .rules(Map.of("threshold", 0.8))
                    .action(PolicyAction.WARN)
                    .build())
                .build();

            assertThat(request.getName()).isEqualTo("Test Dynamic");
            assertThat(request.isEnabled()).isTrue();
            assertThat(request.getConfig().getAction()).isEqualTo(PolicyAction.WARN);
        }

        @Test
        @DisplayName("ListStaticPoliciesOptions should build correctly")
        void listStaticPoliciesOptionsShouldBuildCorrectly() {
            ListStaticPoliciesOptions options = ListStaticPoliciesOptions.builder()
                .category(PolicyCategory.SECURITY_SQLI)
                .tier(PolicyTier.SYSTEM)
                .enabled(true)
                .limit(10)
                .offset(0)
                .sortBy("name")
                .sortOrder("asc")
                .search("sql")
                .build();

            assertThat(options.getCategory()).isEqualTo(PolicyCategory.SECURITY_SQLI);
            assertThat(options.getTier()).isEqualTo(PolicyTier.SYSTEM);
            assertThat(options.getEnabled()).isTrue();
            assertThat(options.getLimit()).isEqualTo(10);
            assertThat(options.getOffset()).isEqualTo(0);
            assertThat(options.getSortBy()).isEqualTo("name");
            assertThat(options.getSortOrder()).isEqualTo("asc");
            assertThat(options.getSearch()).isEqualTo("sql");
        }

        @Test
        @DisplayName("PolicyCategory enum values should serialize correctly")
        void policyCategoryEnumValuesShouldSerializeCorrectly() {
            assertThat(PolicyCategory.SECURITY_SQLI.getValue()).isEqualTo("security-sqli");
            assertThat(PolicyCategory.PII_GLOBAL.getValue()).isEqualTo("pii-global");
            assertThat(PolicyCategory.DYNAMIC_COST.getValue()).isEqualTo("dynamic-cost");
            assertThat(PolicyCategory.CUSTOM.getValue()).isEqualTo("custom");
        }

        @Test
        @DisplayName("PolicyTier enum values should serialize correctly")
        void policyTierEnumValuesShouldSerializeCorrectly() {
            assertThat(PolicyTier.SYSTEM.getValue()).isEqualTo("system");
            assertThat(PolicyTier.ORGANIZATION.getValue()).isEqualTo("organization");
            assertThat(PolicyTier.TENANT.getValue()).isEqualTo("tenant");
        }

        @Test
        @DisplayName("OverrideAction enum values should serialize correctly")
        void overrideActionEnumValuesShouldSerializeCorrectly() {
            assertThat(OverrideAction.BLOCK.getValue()).isEqualTo("block");
            assertThat(OverrideAction.WARN.getValue()).isEqualTo("warn");
            assertThat(OverrideAction.LOG.getValue()).isEqualTo("log");
            assertThat(OverrideAction.REDACT.getValue()).isEqualTo("redact");
        }

        @Test
        @DisplayName("PolicyAction enum values should serialize correctly")
        void policyActionEnumValuesShouldSerializeCorrectly() {
            assertThat(PolicyAction.BLOCK.getValue()).isEqualTo("block");
            assertThat(PolicyAction.WARN.getValue()).isEqualTo("warn");
            assertThat(PolicyAction.LOG.getValue()).isEqualTo("log");
            assertThat(PolicyAction.REDACT.getValue()).isEqualTo("redact");
            assertThat(PolicyAction.ALLOW.getValue()).isEqualTo("allow");
        }
    }
}
