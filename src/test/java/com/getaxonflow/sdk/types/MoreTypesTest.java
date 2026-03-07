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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for SDK types.
 */
@DisplayName("SDK Types")
class MoreTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("PortalLoginResponse")
    class PortalLoginResponseTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            PortalLoginResponse response = new PortalLoginResponse(
                "session-123",
                "org-456",
                "user@example.com",
                "Test User",
                "2026-01-04T12:00:00Z"
            );

            assertThat(response.getSessionId()).isEqualTo("session-123");
            assertThat(response.getOrgId()).isEqualTo("org-456");
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getName()).isEqualTo("Test User");
            assertThat(response.getExpiresAt()).isEqualTo("2026-01-04T12:00:00Z");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"session_id\":\"sess-abc\"," +
                "\"org_id\":\"org-xyz\"," +
                "\"email\":\"test@test.com\"," +
                "\"name\":\"Test\"," +
                "\"expires_at\":\"2026-01-05T00:00:00Z\"" +
                "}";

            PortalLoginResponse response = objectMapper.readValue(json, PortalLoginResponse.class);

            assertThat(response.getSessionId()).isEqualTo("sess-abc");
            assertThat(response.getOrgId()).isEqualTo("org-xyz");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PortalLoginResponse r1 = new PortalLoginResponse("s1", "o1", "e1", "n1", "ex1");
            PortalLoginResponse r2 = new PortalLoginResponse("s1", "o1", "e1", "n1", "ex1");
            PortalLoginResponse r3 = new PortalLoginResponse("s2", "o1", "e1", "n1", "ex1");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PortalLoginResponse response = new PortalLoginResponse("s", "o", "e", "n", "ex");
            assertThat(response.toString()).contains("PortalLoginResponse");
        }
    }

    @Nested
    @DisplayName("CodeArtifact")
    class CodeArtifactTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            List<String> policies = Arrays.asList("policy1", "policy2");
            CodeArtifact artifact = new CodeArtifact(
                true,
                "python",
                "function",
                1024,
                50,
                0,
                1,
                policies
            );

            assertThat(artifact.isCodeOutput()).isTrue();
            assertThat(artifact.getLanguage()).isEqualTo("python");
            assertThat(artifact.getCodeType()).isEqualTo("function");
            assertThat(artifact.getSizeBytes()).isEqualTo(1024);
            assertThat(artifact.getLineCount()).isEqualTo(50);
            assertThat(artifact.getSecretsDetected()).isEqualTo(0);
            assertThat(artifact.getUnsafePatterns()).isEqualTo(1);
            assertThat(artifact.getPoliciesChecked()).containsExactly("policy1", "policy2");
        }

        @Test
        @DisplayName("should handle null values with defaults")
        void shouldHandleNullValues() {
            CodeArtifact artifact = new CodeArtifact(
                false, null, null, 0, 0, 0, 0, null
            );

            assertThat(artifact.getLanguage()).isEmpty();
            assertThat(artifact.getCodeType()).isEmpty();
            assertThat(artifact.getPoliciesChecked()).isEmpty();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"is_code_output\":true," +
                "\"language\":\"javascript\"," +
                "\"code_type\":\"class\"," +
                "\"size_bytes\":2048," +
                "\"line_count\":100," +
                "\"secrets_detected\":2," +
                "\"unsafe_patterns\":3," +
                "\"policies_checked\":[\"p1\",\"p2\"]" +
                "}";

            CodeArtifact artifact = objectMapper.readValue(json, CodeArtifact.class);

            assertThat(artifact.isCodeOutput()).isTrue();
            assertThat(artifact.getLanguage()).isEqualTo("javascript");
            assertThat(artifact.getSecretsDetected()).isEqualTo(2);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            CodeArtifact a1 = new CodeArtifact(true, "py", "fn", 100, 10, 0, 0, null);
            CodeArtifact a2 = new CodeArtifact(true, "py", "fn", 100, 10, 0, 0, null);
            CodeArtifact a3 = new CodeArtifact(false, "py", "fn", 100, 10, 0, 0, null);

            assertThat(a1).isEqualTo(a2);
            assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
            assertThat(a1).isNotEqualTo(a3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            CodeArtifact artifact = new CodeArtifact(true, "go", "script", 512, 25, 0, 0, null);
            String str = artifact.toString();
            assertThat(str).contains("CodeArtifact");
            assertThat(str).contains("go");
        }
    }

    @Nested
    @DisplayName("ConnectorHealthStatus")
    class ConnectorHealthStatusTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            Map<String, String> details = new HashMap<>();
            details.put("db", "connected");

            ConnectorHealthStatus status = new ConnectorHealthStatus(
                true, 150L, details, "2026-01-04T10:00:00Z", null
            );

            assertThat(status.isHealthy()).isTrue();
            assertThat(status.getLatency()).isEqualTo(150L);
            assertThat(status.getDetails()).containsEntry("db", "connected");
            assertThat(status.getTimestamp()).isEqualTo("2026-01-04T10:00:00Z");
            assertThat(status.getError()).isNull();
        }

        @Test
        @DisplayName("should handle null values with defaults")
        void shouldHandleNullValues() {
            ConnectorHealthStatus status = new ConnectorHealthStatus(
                null, null, null, null, "Connection failed"
            );

            assertThat(status.isHealthy()).isFalse();
            assertThat(status.getLatency()).isEqualTo(0L);
            assertThat(status.getDetails()).isEmpty();
            assertThat(status.getTimestamp()).isEmpty();
            assertThat(status.getError()).isEqualTo("Connection failed");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"healthy\":false," +
                "\"latency\":500," +
                "\"timestamp\":\"2026-01-04T12:00:00Z\"," +
                "\"error\":\"Timeout\"" +
                "}";

            ConnectorHealthStatus status = objectMapper.readValue(json, ConnectorHealthStatus.class);

            assertThat(status.isHealthy()).isFalse();
            assertThat(status.getLatency()).isEqualTo(500L);
            assertThat(status.getError()).isEqualTo("Timeout");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ConnectorHealthStatus s1 = new ConnectorHealthStatus(true, 100L, null, "ts1", null);
            ConnectorHealthStatus s2 = new ConnectorHealthStatus(true, 100L, null, "ts1", null);
            ConnectorHealthStatus s3 = new ConnectorHealthStatus(false, 100L, null, "ts1", null);

            assertThat(s1).isEqualTo(s2);
            assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
            assertThat(s1).isNotEqualTo(s3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ConnectorHealthStatus status = new ConnectorHealthStatus(true, 50L, null, "ts", null);
            assertThat(status.toString()).contains("ConnectorHealthStatus");
        }
    }

    @Nested
    @DisplayName("ConnectorInfo")
    class ConnectorInfoTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            List<String> caps = Arrays.asList("read", "write");
            Map<String, Object> schema = new HashMap<>();
            schema.put("host", "string");

            ConnectorInfo info = new ConnectorInfo(
                "conn-1", "PostgreSQL", "Database connector", "database",
                "1.0.0", caps, schema, true, true
            );

            assertThat(info.getId()).isEqualTo("conn-1");
            assertThat(info.getName()).isEqualTo("PostgreSQL");
            assertThat(info.getDescription()).isEqualTo("Database connector");
            assertThat(info.getType()).isEqualTo("database");
            assertThat(info.getVersion()).isEqualTo("1.0.0");
            assertThat(info.getCapabilities()).containsExactly("read", "write");
            assertThat(info.getConfigSchema()).containsEntry("host", "string");
            assertThat(info.isInstalled()).isTrue();
            assertThat(info.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() {
            ConnectorInfo info = new ConnectorInfo(
                "id", "name", "desc", "type", "v1",
                null, null, false, false
            );

            assertThat(info.getCapabilities()).isEmpty();
            assertThat(info.getConfigSchema()).isEmpty();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"id\":\"mysql-connector\"," +
                "\"name\":\"MySQL\"," +
                "\"type\":\"database\"," +
                "\"version\":\"2.0.0\"," +
                "\"installed\":true," +
                "\"enabled\":false" +
                "}";

            ConnectorInfo info = objectMapper.readValue(json, ConnectorInfo.class);

            assertThat(info.getId()).isEqualTo("mysql-connector");
            assertThat(info.isInstalled()).isTrue();
            assertThat(info.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ConnectorInfo i1 = new ConnectorInfo("id1", "n", "d", "t", "v", null, null, true, true);
            ConnectorInfo i2 = new ConnectorInfo("id1", "n", "d", "t", "v", null, null, true, true);
            ConnectorInfo i3 = new ConnectorInfo("id2", "n", "d", "t", "v", null, null, true, true);

            assertThat(i1).isEqualTo(i2);
            assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
            assertThat(i1).isNotEqualTo(i3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ConnectorInfo info = new ConnectorInfo("id", "MySQL", "d", "db", "1.0", null, null, true, true);
            assertThat(info.toString()).contains("ConnectorInfo").contains("MySQL");
        }
    }

    @Nested
    @DisplayName("AuditOptions")
    class AuditOptionsTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            AuditOptions options = AuditOptions.builder()
                .contextId("ctx-123")
                .clientId("client-456")
                .build();

            assertThat(options.getContextId()).isEqualTo("ctx-123");
            assertThat(options.getClientId()).isEqualTo("client-456");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            TokenUsage usage = TokenUsage.of(100, 200);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key", "value");

            AuditOptions options = AuditOptions.builder()
                .contextId("ctx-123")
                .clientId("client-456")
                .responseSummary("Summary of response")
                .provider("openai")
                .model("gpt-4")
                .tokenUsage(usage)
                .latencyMs(1234)
                .metadata(metadata)
                .success(true)
                .errorMessage(null)
                .build();

            assertThat(options.getContextId()).isEqualTo("ctx-123");
            assertThat(options.getResponseSummary()).isEqualTo("Summary of response");
            assertThat(options.getProvider()).isEqualTo("openai");
            assertThat(options.getModel()).isEqualTo("gpt-4");
            assertThat(options.getTokenUsage()).isEqualTo(usage);
            assertThat(options.getLatencyMs()).isEqualTo(1234L);
            assertThat(options.getMetadata()).containsEntry("key", "value");
            assertThat(options.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("should add metadata incrementally")
        void shouldAddMetadataIncrementally() {
            AuditOptions options = AuditOptions.builder()
                .contextId("ctx")
                .clientId("client")
                .addMetadata("k1", "v1")
                .addMetadata("k2", "v2")
                .build();

            assertThat(options.getMetadata()).hasSize(2);
            assertThat(options.getMetadata()).containsEntry("k1", "v1");
            assertThat(options.getMetadata()).containsEntry("k2", "v2");
        }

        @Test
        @DisplayName("should fail when contextId is null")
        void shouldFailWhenContextIdIsNull() {
            assertThatThrownBy(() -> AuditOptions.builder()
                .clientId("client")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should allow clientId to be null for smart defaults")
        void shouldAllowClientIdToBeNull() {
            // clientId can be null - SDK will use smart default "community"
            AuditOptions options = AuditOptions.builder()
                .contextId("ctx")
                .build();
            assertThat(options.getContextId()).isEqualTo("ctx");
            assertThat(options.getClientId()).isNull();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            AuditOptions o1 = AuditOptions.builder().contextId("c1").clientId("cl1").build();
            AuditOptions o2 = AuditOptions.builder().contextId("c1").clientId("cl1").build();
            AuditOptions o3 = AuditOptions.builder().contextId("c2").clientId("cl1").build();

            assertThat(o1).isEqualTo(o2);
            assertThat(o1.hashCode()).isEqualTo(o2.hashCode());
            assertThat(o1).isNotEqualTo(o3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            AuditOptions options = AuditOptions.builder()
                .contextId("ctx")
                .clientId("client")
                .provider("anthropic")
                .build();
            assertThat(options.toString()).contains("AuditOptions").contains("anthropic");
        }
    }

    @Nested
    @DisplayName("AuditResult")
    class AuditResultTests {

        @Test
        @DisplayName("should create successful result")
        void shouldCreateSuccessfulResult() {
            AuditResult result = new AuditResult(true, "audit-123", "Recorded", null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAuditId()).isEqualTo("audit-123");
            assertThat(result.getMessage()).isEqualTo("Recorded");
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("should create failed result")
        void shouldCreateFailedResult() {
            AuditResult result = new AuditResult(false, null, null, "Context expired");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("Context expired");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"success\":true," +
                "\"audit_id\":\"aud-456\"," +
                "\"message\":\"OK\"" +
                "}";

            AuditResult result = objectMapper.readValue(json, AuditResult.class);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAuditId()).isEqualTo("aud-456");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            AuditResult r1 = new AuditResult(true, "a1", "m1", null);
            AuditResult r2 = new AuditResult(true, "a1", "m1", null);
            AuditResult r3 = new AuditResult(false, "a1", "m1", null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            AuditResult result = new AuditResult(true, "aud", "msg", null);
            assertThat(result.toString()).contains("AuditResult");
        }
    }

    @Nested
    @DisplayName("PlanStep")
    class PlanStepTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            List<String> deps = Arrays.asList("step-1", "step-2");
            Map<String, Object> params = new HashMap<>();
            params.put("query", "SELECT * FROM users");

            PlanStep step = new PlanStep(
                "step-3", "Query Database", "connector-call", "Fetch user data",
                deps, "db-agent", params, "2s"
            );

            assertThat(step.getId()).isEqualTo("step-3");
            assertThat(step.getName()).isEqualTo("Query Database");
            assertThat(step.getType()).isEqualTo("connector-call");
            assertThat(step.getDescription()).isEqualTo("Fetch user data");
            assertThat(step.getDependsOn()).containsExactly("step-1", "step-2");
            assertThat(step.getAgent()).isEqualTo("db-agent");
            assertThat(step.getParameters()).containsEntry("query", "SELECT * FROM users");
            assertThat(step.getEstimatedTime()).isEqualTo("2s");
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() {
            PlanStep step = new PlanStep("id", "name", "type", "desc", null, "agent", null, "1s");

            assertThat(step.getDependsOn()).isEmpty();
            assertThat(step.getParameters()).isEmpty();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"id\":\"s1\"," +
                "\"name\":\"Step 1\"," +
                "\"type\":\"llm-call\"," +
                "\"description\":\"Call LLM\"," +
                "\"depends_on\":[\"s0\"]," +
                "\"agent\":\"llm-agent\"," +
                "\"estimated_time\":\"500ms\"" +
                "}";

            PlanStep step = objectMapper.readValue(json, PlanStep.class);

            assertThat(step.getId()).isEqualTo("s1");
            assertThat(step.getType()).isEqualTo("llm-call");
            assertThat(step.getDependsOn()).containsExactly("s0");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PlanStep s1 = new PlanStep("id1", "n", "t", "d", null, "a", null, "1s");
            PlanStep s2 = new PlanStep("id1", "n", "t", "d", null, "a", null, "1s");
            PlanStep s3 = new PlanStep("id2", "n", "t", "d", null, "a", null, "1s");

            assertThat(s1).isEqualTo(s2);
            assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
            assertThat(s1).isNotEqualTo(s3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PlanStep step = new PlanStep("id", "LLM Call", "llm-call", "desc", null, "agent", null, "1s");
            assertThat(step.toString()).contains("PlanStep").contains("LLM Call");
        }
    }

    @Nested
    @DisplayName("PolicyApprovalRequest")
    class PolicyApprovalRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            PolicyApprovalRequest request = PolicyApprovalRequest.builder()
                .userToken("user-123")
                .query("What is the weather?")
                .build();

            assertThat(request.getUserToken()).isEqualTo("user-123");
            assertThat(request.getQuery()).isEqualTo("What is the weather?");
            assertThat(request.getDataSources()).isEmpty();
            assertThat(request.getContext()).isEmpty();
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            List<String> sources = Arrays.asList("db1", "db2");
            Map<String, Object> context = new HashMap<>();
            context.put("env", "production");

            PolicyApprovalRequest request = PolicyApprovalRequest.builder()
                .userToken("user-456")
                .query("Query data")
                .dataSources(sources)
                .context(context)
                .clientId("client-789")
                .build();

            assertThat(request.getUserToken()).isEqualTo("user-456");
            assertThat(request.getDataSources()).containsExactly("db1", "db2");
            assertThat(request.getContext()).containsEntry("env", "production");
            assertThat(request.getClientId()).isEqualTo("client-789");
        }

        @Test
        @DisplayName("should add context incrementally")
        void shouldAddContextIncrementally() {
            PolicyApprovalRequest request = PolicyApprovalRequest.builder()
                .userToken("user")
                .query("query")
                .addContext("k1", "v1")
                .addContext("k2", "v2")
                .build();

            assertThat(request.getContext()).hasSize(2);
        }

        @Test
        @DisplayName("should fail when userToken is null")
        void shouldFailWhenUserTokenIsNull() {
            assertThatThrownBy(() -> PolicyApprovalRequest.builder()
                .query("query")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PolicyApprovalRequest r1 = PolicyApprovalRequest.builder().userToken("u1").query("q1").build();
            PolicyApprovalRequest r2 = PolicyApprovalRequest.builder().userToken("u1").query("q1").build();
            PolicyApprovalRequest r3 = PolicyApprovalRequest.builder().userToken("u2").query("q1").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PolicyApprovalRequest request = PolicyApprovalRequest.builder()
                .userToken("user")
                .query("test query")
                .build();
            assertThat(request.toString()).contains("PolicyApprovalRequest");
        }
    }

    @Nested
    @DisplayName("PolicyInfo")
    class PolicyInfoTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            List<String> policies = Arrays.asList("policy1", "policy2");
            List<String> checks = Arrays.asList("pii", "sqli");
            CodeArtifact artifact = new CodeArtifact(true, "python", "function", 100, 10, 0, 0, null);

            PolicyInfo info = new PolicyInfo(policies, checks, "17.48ms", "tenant-1", 0.15, artifact);

            assertThat(info.getPoliciesEvaluated()).containsExactly("policy1", "policy2");
            assertThat(info.getStaticChecks()).containsExactly("pii", "sqli");
            assertThat(info.getProcessingTime()).isEqualTo("17.48ms");
            assertThat(info.getTenantId()).isEqualTo("tenant-1");
            assertThat(info.getRiskScore()).isEqualTo(0.15);
            assertThat(info.getCodeArtifact()).isNotNull();
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() {
            PolicyInfo info = new PolicyInfo(null, null, null, null, null, null);

            assertThat(info.getPoliciesEvaluated()).isEmpty();
            assertThat(info.getStaticChecks()).isEmpty();
        }

        @Test
        @DisplayName("should parse processing time as Duration - milliseconds")
        void shouldParseProcessingTimeMs() {
            PolicyInfo info = new PolicyInfo(null, null, "17.48ms", null, null, null);
            Duration duration = info.getProcessingDuration();

            assertThat(duration.toMillis()).isEqualTo(17);
        }

        @Test
        @DisplayName("should parse processing time as Duration - seconds")
        void shouldParseProcessingTimeSeconds() {
            PolicyInfo info = new PolicyInfo(null, null, "1.5s", null, null, null);
            Duration duration = info.getProcessingDuration();

            assertThat(duration.toMillis()).isEqualTo(1500);
        }

        @Test
        @DisplayName("should handle plain numeric value as milliseconds")
        void shouldHandlePlainNumericValue() {
            PolicyInfo info = new PolicyInfo(null, null, "100", null, null, null);
            Duration duration = info.getProcessingDuration();

            assertThat(duration.toMillis()).isEqualTo(100);
        }

        @Test
        @DisplayName("should return zero duration for null or empty")
        void shouldReturnZeroForNullOrEmpty() {
            PolicyInfo infoNull = new PolicyInfo(null, null, null, null, null, null);
            PolicyInfo infoEmpty = new PolicyInfo(null, null, "", null, null, null);

            assertThat(infoNull.getProcessingDuration()).isEqualTo(Duration.ZERO);
            assertThat(infoEmpty.getProcessingDuration()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should return zero duration for invalid format")
        void shouldReturnZeroForInvalidFormat() {
            PolicyInfo info = new PolicyInfo(null, null, "invalid", null, null, null);
            assertThat(info.getProcessingDuration()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"policies_evaluated\":[\"p1\"]," +
                "\"static_checks\":[\"c1\"]," +
                "\"processing_time\":\"10ms\"," +
                "\"tenant_id\":\"t1\"," +
                "\"risk_score\":0.5" +
                "}";

            PolicyInfo info = objectMapper.readValue(json, PolicyInfo.class);

            assertThat(info.getPoliciesEvaluated()).containsExactly("p1");
            assertThat(info.getRiskScore()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PolicyInfo i1 = new PolicyInfo(Arrays.asList("p1"), null, "10ms", "t1", null, null);
            PolicyInfo i2 = new PolicyInfo(Arrays.asList("p1"), null, "10ms", "t1", null, null);
            PolicyInfo i3 = new PolicyInfo(Arrays.asList("p2"), null, "10ms", "t1", null, null);

            assertThat(i1).isEqualTo(i2);
            assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
            assertThat(i1).isNotEqualTo(i3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PolicyInfo info = new PolicyInfo(Arrays.asList("pol1"), null, "5ms", "tenant", null, null);
            assertThat(info.toString()).contains("PolicyInfo").contains("pol1");
        }
    }

    @Nested
    @DisplayName("TokenUsage")
    class TokenUsageTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            TokenUsage usage = new TokenUsage(100, 200, 300);

            assertThat(usage.getPromptTokens()).isEqualTo(100);
            assertThat(usage.getCompletionTokens()).isEqualTo(200);
            assertThat(usage.getTotalTokens()).isEqualTo(300);
        }

        @Test
        @DisplayName("should create using factory method with auto-calculated total")
        void shouldCreateUsingFactoryMethod() {
            TokenUsage usage = TokenUsage.of(150, 250);

            assertThat(usage.getPromptTokens()).isEqualTo(150);
            assertThat(usage.getCompletionTokens()).isEqualTo(250);
            assertThat(usage.getTotalTokens()).isEqualTo(400);
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"prompt_tokens\":50," +
                "\"completion_tokens\":75," +
                "\"total_tokens\":125" +
                "}";

            TokenUsage usage = objectMapper.readValue(json, TokenUsage.class);

            assertThat(usage.getPromptTokens()).isEqualTo(50);
            assertThat(usage.getCompletionTokens()).isEqualTo(75);
            assertThat(usage.getTotalTokens()).isEqualTo(125);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            TokenUsage u1 = TokenUsage.of(100, 200);
            TokenUsage u2 = TokenUsage.of(100, 200);
            TokenUsage u3 = TokenUsage.of(100, 300);

            assertThat(u1).isEqualTo(u2);
            assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
            assertThat(u1).isNotEqualTo(u3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            TokenUsage usage = TokenUsage.of(10, 20);
            assertThat(usage.toString()).contains("TokenUsage").contains("10").contains("20");
        }
    }

    @Nested
    @DisplayName("RateLimitInfo")
    class RateLimitInfoTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            Instant resetAt = Instant.parse("2026-01-04T12:00:00Z");
            RateLimitInfo info = new RateLimitInfo(1000, 500, resetAt);

            assertThat(info.getLimit()).isEqualTo(1000);
            assertThat(info.getRemaining()).isEqualTo(500);
            assertThat(info.getResetAt()).isEqualTo(resetAt);
        }

        @Test
        @DisplayName("should detect exceeded rate limit")
        void shouldDetectExceededRateLimit() {
            RateLimitInfo exceeded = new RateLimitInfo(100, 0, null);
            RateLimitInfo notExceeded = new RateLimitInfo(100, 50, null);

            assertThat(exceeded.isExceeded()).isTrue();
            assertThat(notExceeded.isExceeded()).isFalse();
        }

        @Test
        @DisplayName("should detect exceeded with negative remaining")
        void shouldDetectExceededWithNegative() {
            RateLimitInfo info = new RateLimitInfo(100, -5, null);
            assertThat(info.isExceeded()).isTrue();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            Instant reset = Instant.now();
            RateLimitInfo r1 = new RateLimitInfo(100, 50, reset);
            RateLimitInfo r2 = new RateLimitInfo(100, 50, reset);
            RateLimitInfo r3 = new RateLimitInfo(100, 25, reset);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            RateLimitInfo info = new RateLimitInfo(100, 75, null);
            assertThat(info.toString()).contains("RateLimitInfo").contains("100").contains("75");
        }
    }

    @Nested
    @DisplayName("Mode")
    class ModeTests {

        @Test
        @DisplayName("should have correct values")
        void shouldHaveCorrectValues() {
            assertThat(Mode.PRODUCTION.getValue()).isEqualTo("production");
            assertThat(Mode.SANDBOX.getValue()).isEqualTo("sandbox");
        }

        @Test
        @DisplayName("should parse from value")
        void shouldParseFromValue() {
            assertThat(Mode.fromValue("production")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue("sandbox")).isEqualTo(Mode.SANDBOX);
            assertThat(Mode.fromValue("PRODUCTION")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue("SANDBOX")).isEqualTo(Mode.SANDBOX);
        }

        @Test
        @DisplayName("should return PRODUCTION for unknown values")
        void shouldReturnProductionForUnknown() {
            assertThat(Mode.fromValue("unknown")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue("")).isEqualTo(Mode.PRODUCTION);
        }

        @Test
        @DisplayName("should return PRODUCTION for null")
        void shouldReturnProductionForNull() {
            assertThat(Mode.fromValue(null)).isEqualTo(Mode.PRODUCTION);
        }
    }

    @Nested
    @DisplayName("RequestType")
    class RequestTypeTests {

        @Test
        @DisplayName("should have correct values")
        void shouldHaveCorrectValues() {
            assertThat(RequestType.CHAT.getValue()).isEqualTo("chat");
            assertThat(RequestType.SQL.getValue()).isEqualTo("sql");
            assertThat(RequestType.MCP_QUERY.getValue()).isEqualTo("mcp-query");
            assertThat(RequestType.MULTI_AGENT_PLAN.getValue()).isEqualTo("multi-agent-plan");
        }

        @Test
        @DisplayName("should parse from value")
        void shouldParseFromValue() {
            assertThat(RequestType.fromValue("chat")).isEqualTo(RequestType.CHAT);
            assertThat(RequestType.fromValue("sql")).isEqualTo(RequestType.SQL);
            assertThat(RequestType.fromValue("mcp-query")).isEqualTo(RequestType.MCP_QUERY);
            assertThat(RequestType.fromValue("multi-agent-plan")).isEqualTo(RequestType.MULTI_AGENT_PLAN);
        }

        @Test
        @DisplayName("should parse case insensitively")
        void shouldParseCaseInsensitively() {
            assertThat(RequestType.fromValue("CHAT")).isEqualTo(RequestType.CHAT);
            assertThat(RequestType.fromValue("Chat")).isEqualTo(RequestType.CHAT);
        }

        @Test
        @DisplayName("should throw for unknown value")
        void shouldThrowForUnknownValue() {
            assertThatThrownBy(() -> RequestType.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown request type");
        }

        @Test
        @DisplayName("should throw for null value")
        void shouldThrowForNullValue() {
            assertThatThrownBy(() -> RequestType.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        }
    }

    @Nested
    @DisplayName("HealthStatus")
    class HealthStatusTests {

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            Map<String, Object> components = new HashMap<>();
            components.put("database", "healthy");
            components.put("cache", "healthy");

            HealthStatus status = new HealthStatus("healthy", "2.6.0", "24h5m", components, null, null);

            assertThat(status.getStatus()).isEqualTo("healthy");
            assertThat(status.getVersion()).isEqualTo("2.6.0");
            assertThat(status.getUptime()).isEqualTo("24h5m");
            assertThat(status.getComponents()).containsEntry("database", "healthy");
        }

        @Test
        @DisplayName("should handle null components")
        void shouldHandleNullComponents() {
            HealthStatus status = new HealthStatus("healthy", "1.0.0", "1h", null, null, null);
            assertThat(status.getComponents()).isEmpty();
        }

        @Test
        @DisplayName("should detect healthy status")
        void shouldDetectHealthyStatus() {
            HealthStatus healthy = new HealthStatus("healthy", "1.0", "1h", null, null, null);
            HealthStatus ok = new HealthStatus("ok", "1.0", "1h", null, null, null);
            HealthStatus degraded = new HealthStatus("degraded", "1.0", "1h", null, null, null);
            HealthStatus unhealthy = new HealthStatus("unhealthy", "1.0", "1h", null, null, null);

            assertThat(healthy.isHealthy()).isTrue();
            assertThat(ok.isHealthy()).isTrue();
            assertThat(degraded.isHealthy()).isFalse();
            assertThat(unhealthy.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"status\":\"healthy\"," +
                "\"version\":\"2.5.0\"," +
                "\"uptime\":\"12h30m\"" +
                "}";

            HealthStatus status = objectMapper.readValue(json, HealthStatus.class);

            assertThat(status.getStatus()).isEqualTo("healthy");
            assertThat(status.getVersion()).isEqualTo("2.5.0");
            assertThat(status.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            HealthStatus s1 = new HealthStatus("healthy", "1.0", "1h", null, null, null);
            HealthStatus s2 = new HealthStatus("healthy", "1.0", "1h", null, null, null);
            HealthStatus s3 = new HealthStatus("degraded", "1.0", "1h", null, null, null);

            assertThat(s1).isEqualTo(s2);
            assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
            assertThat(s1).isNotEqualTo(s3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            HealthStatus status = new HealthStatus("healthy", "2.0.0", "5h", null, null, null);
            assertThat(status.toString()).contains("HealthStatus").contains("healthy");
        }
    }

    @Nested
    @DisplayName("PolicyApprovalResult")
    class PolicyApprovalResultTests {

        @Test
        @DisplayName("should create approved result")
        void shouldCreateApprovedResult() {
            Map<String, Object> data = new HashMap<>();
            data.put("sanitized_query", "SELECT * FROM users");
            List<String> policies = Arrays.asList("pii-check", "sqli-check");
            Instant expiresAt = Instant.now().plusSeconds(300);

            PolicyApprovalResult result = new PolicyApprovalResult(
                "ctx-123", true, false, data, policies, expiresAt, null, null, "5.2ms"
            );

            assertThat(result.getContextId()).isEqualTo("ctx-123");
            assertThat(result.isApproved()).isTrue();
            assertThat(result.getApprovedData()).containsKey("sanitized_query");
            assertThat(result.getPolicies()).containsExactly("pii-check", "sqli-check");
            assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(result.getBlockReason()).isNull();
            assertThat(result.getProcessingTime()).isEqualTo("5.2ms");
        }

        @Test
        @DisplayName("should create blocked result")
        void shouldCreateBlockedResult() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                null, false, false, null, null, null,
                "Request blocked by policy: pii-detection", null, "3.1ms"
            );

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getBlockReason()).isEqualTo("Request blocked by policy: pii-detection");
        }

        @Test
        @DisplayName("should check expiration")
        void shouldCheckExpiration() {
            Instant future = Instant.now().plusSeconds(3600);
            Instant past = Instant.now().minusSeconds(3600);

            PolicyApprovalResult notExpired = new PolicyApprovalResult(
                "ctx", true, false, null, null, future, null, null, null
            );
            PolicyApprovalResult expired = new PolicyApprovalResult(
                "ctx", true, false, null, null, past, null, null, null
            );
            PolicyApprovalResult noExpiry = new PolicyApprovalResult(
                "ctx", true, false, null, null, null, null, null, null
            );

            assertThat(notExpired.isExpired()).isFalse();
            assertThat(expired.isExpired()).isTrue();
            assertThat(noExpiry.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should extract blocking policy name - format 1")
        void shouldExtractBlockingPolicyNameFormat1() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                null, false, false, null, null, null,
                "Request blocked by policy: my-policy", null, null
            );

            assertThat(result.getBlockingPolicyName()).isEqualTo("my-policy");
        }

        @Test
        @DisplayName("should extract blocking policy name - format 2")
        void shouldExtractBlockingPolicyNameFormat2() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                null, false, false, null, null, null,
                "Blocked by policy: another-policy", null, null
            );

            assertThat(result.getBlockingPolicyName()).isEqualTo("another-policy");
        }

        @Test
        @DisplayName("should extract blocking policy name - bracket format")
        void shouldExtractBlockingPolicyNameBracket() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                null, false, false, null, null, null,
                "[policy-name] Description of violation", null, null
            );

            assertThat(result.getBlockingPolicyName()).isEqualTo("policy-name");
        }

        @Test
        @DisplayName("should return full reason when no pattern matches")
        void shouldReturnFullReasonWhenNoPattern() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                null, false, false, null, null, null,
                "Generic block reason", null, null
            );

            assertThat(result.getBlockingPolicyName()).isEqualTo("Generic block reason");
        }

        @Test
        @DisplayName("should return null for null block reason")
        void shouldReturnNullForNullBlockReason() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                "ctx", true, false, null, null, null, null, null, null
            );

            assertThat(result.getBlockingPolicyName()).isNull();
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                "ctx", true, false, null, null, null, null, null, null
            );

            assertThat(result.getApprovedData()).isEmpty();
            assertThat(result.getPolicies()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PolicyApprovalResult r1 = new PolicyApprovalResult("c1", true, false, null, null, null, null, null, null);
            PolicyApprovalResult r2 = new PolicyApprovalResult("c1", true, false, null, null, null, null, null, null);
            PolicyApprovalResult r3 = new PolicyApprovalResult("c2", true, false, null, null, null, null, null, null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PolicyApprovalResult result = new PolicyApprovalResult(
                "ctx-abc", true, false, null, Arrays.asList("p1"), null, null, null, "1ms"
            );
            assertThat(result.toString()).contains("PolicyApprovalResult").contains("ctx-abc");
        }
    }

    @Nested
    @DisplayName("PlanRequest")
    class PlanRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            PlanRequest request = PlanRequest.builder()
                .objective("Analyze sales data")
                .build();

            assertThat(request.getObjective()).isEqualTo("Analyze sales data");
            assertThat(request.getDomain()).isEqualTo("generic");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            Map<String, Object> context = new HashMap<>();
            context.put("dataset", "sales_2025");
            Map<String, Object> constraints = new HashMap<>();
            constraints.put("max_time", "60s");

            PlanRequest request = PlanRequest.builder()
                .objective("Generate report")
                .domain("finance")
                .userToken("user-123")
                .context(context)
                .constraints(constraints)
                .maxSteps(10)
                .parallel(true)
                .build();

            assertThat(request.getObjective()).isEqualTo("Generate report");
            assertThat(request.getDomain()).isEqualTo("finance");
            assertThat(request.getUserToken()).isEqualTo("user-123");
            assertThat(request.getContext()).containsEntry("dataset", "sales_2025");
            assertThat(request.getConstraints()).containsEntry("max_time", "60s");
            assertThat(request.getMaxSteps()).isEqualTo(10);
            assertThat(request.getParallel()).isTrue();
        }

        @Test
        @DisplayName("should add context incrementally")
        void shouldAddContextIncrementally() {
            PlanRequest request = PlanRequest.builder()
                .objective("test")
                .addContext("k1", "v1")
                .addContext("k2", "v2")
                .build();

            assertThat(request.getContext()).hasSize(2);
        }

        @Test
        @DisplayName("should fail when objective is null")
        void shouldFailWhenObjectiveIsNull() {
            assertThatThrownBy(() -> PlanRequest.builder().build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PlanRequest r1 = PlanRequest.builder().objective("obj1").build();
            PlanRequest r2 = PlanRequest.builder().objective("obj1").build();
            PlanRequest r3 = PlanRequest.builder().objective("obj2").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PlanRequest request = PlanRequest.builder()
                .objective("My objective")
                .domain("healthcare")
                .build();
            assertThat(request.toString()).contains("PlanRequest").contains("My objective");
        }
    }

    @Nested
    @DisplayName("ClientRequest")
    class ClientRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            ClientRequest request = ClientRequest.builder()
                .query("Hello, world!")
                .build();

            assertThat(request.getQuery()).isEqualTo("Hello, world!");
            assertThat(request.getRequestType()).isEqualTo("chat");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            Map<String, Object> context = new HashMap<>();
            context.put("session", "sess-123");

            ClientRequest request = ClientRequest.builder()
                .query("What is AI governance?")
                .userToken("user-456")
                .clientId("client-789")
                .requestType(RequestType.CHAT)
                .context(context)
                .llmProvider("anthropic")
                .model("claude-3-opus")
                .build();

            assertThat(request.getQuery()).isEqualTo("What is AI governance?");
            assertThat(request.getUserToken()).isEqualTo("user-456");
            assertThat(request.getClientId()).isEqualTo("client-789");
            assertThat(request.getRequestType()).isEqualTo("chat");
            assertThat(request.getContext()).containsEntry("session", "sess-123");
            assertThat(request.getLlmProvider()).isEqualTo("anthropic");
            assertThat(request.getModel()).isEqualTo("claude-3-opus");
        }

        @Test
        @DisplayName("should add context incrementally")
        void shouldAddContextIncrementally() {
            ClientRequest request = ClientRequest.builder()
                .query("test")
                .addContext("k1", "v1")
                .addContext("k2", "v2")
                .build();

            assertThat(request.getContext()).hasSize(2);
        }

        @Test
        @DisplayName("should use different request types")
        void shouldUseDifferentRequestTypes() {
            ClientRequest chat = ClientRequest.builder().query("q").requestType(RequestType.CHAT).build();
            ClientRequest sql = ClientRequest.builder().query("q").requestType(RequestType.SQL).build();
            ClientRequest mcp = ClientRequest.builder().query("q").requestType(RequestType.MCP_QUERY).build();
            ClientRequest plan = ClientRequest.builder().query("q").requestType(RequestType.MULTI_AGENT_PLAN).build();

            assertThat(chat.getRequestType()).isEqualTo("chat");
            assertThat(sql.getRequestType()).isEqualTo("sql");
            assertThat(mcp.getRequestType()).isEqualTo("mcp-query");
            assertThat(plan.getRequestType()).isEqualTo("multi-agent-plan");
        }

        @Test
        @DisplayName("should fail when query is null")
        void shouldFailWhenQueryIsNull() {
            assertThatThrownBy(() -> ClientRequest.builder().build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ClientRequest r1 = ClientRequest.builder().query("q1").build();
            ClientRequest r2 = ClientRequest.builder().query("q1").build();
            ClientRequest r3 = ClientRequest.builder().query("q2").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ClientRequest request = ClientRequest.builder()
                .query("test query")
                .llmProvider("openai")
                .build();
            assertThat(request.toString()).contains("ClientRequest").contains("openai");
        }
    }

    @Nested
    @DisplayName("ClientResponse")
    class ClientResponseTests {

        @Test
        @DisplayName("should create successful response")
        void shouldCreateSuccessfulResponse() {
            PolicyInfo policyInfo = new PolicyInfo(
                Arrays.asList("policy1"), null, "5ms", "tenant1", null, null
            );

            ClientResponse response = new ClientResponse(
                true, "Response data", "result text", "plan-123",
                false, null, policyInfo, null, null, null
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo("Response data");
            assertThat(response.getResult()).isEqualTo("result text");
            assertThat(response.getPlanId()).isEqualTo("plan-123");
            assertThat(response.isBlocked()).isFalse();
            assertThat(response.getPolicyInfo()).isNotNull();
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("should create blocked response")
        void shouldCreateBlockedResponse() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                true, "Request blocked by policy: pii-check", null, null, null, null
            );

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isBlocked()).isTrue();
            assertThat(response.getBlockReason()).isEqualTo("Request blocked by policy: pii-check");
        }

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                false, null, null, "Internal server error", null, null
            );

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Internal server error");
        }

        @Test
        @DisplayName("should extract blocking policy name - format 1")
        void shouldExtractBlockingPolicyNameFormat1() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                true, "Request blocked by policy: my-policy", null, null, null, null
            );

            assertThat(response.getBlockingPolicyName()).isEqualTo("my-policy");
        }

        @Test
        @DisplayName("should extract blocking policy name - format 2")
        void shouldExtractBlockingPolicyNameFormat2() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                true, "Blocked by policy: another-policy", null, null, null, null
            );

            assertThat(response.getBlockingPolicyName()).isEqualTo("another-policy");
        }

        @Test
        @DisplayName("should extract blocking policy name - bracket format")
        void shouldExtractBlockingPolicyNameBracket() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                true, "[policy-name] Detailed description", null, null, null, null
            );

            assertThat(response.getBlockingPolicyName()).isEqualTo("policy-name");
        }

        @Test
        @DisplayName("should return full reason when no pattern matches")
        void shouldReturnFullReasonWhenNoPattern() {
            ClientResponse response = new ClientResponse(
                false, null, null, null,
                true, "Custom block reason", null, null, null, null
            );

            assertThat(response.getBlockingPolicyName()).isEqualTo("Custom block reason");
        }

        @Test
        @DisplayName("should return null for null or empty block reason")
        void shouldReturnNullForNullOrEmpty() {
            ClientResponse nullReason = new ClientResponse(
                true, null, null, null, false, null, null, null, null, null
            );
            ClientResponse emptyReason = new ClientResponse(
                true, null, null, null, false, "", null, null, null, null
            );

            assertThat(nullReason.getBlockingPolicyName()).isNull();
            assertThat(emptyReason.getBlockingPolicyName()).isNull();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"success\":true," +
                "\"data\":{\"key\":\"value\"}," +
                "\"blocked\":false," +
                "\"policy_info\":{\"policies_evaluated\":[\"p1\"],\"processing_time\":\"2ms\"}" +
                "}";

            ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isBlocked()).isFalse();
            assertThat(response.getPolicyInfo()).isNotNull();
            assertThat(response.getPolicyInfo().getPoliciesEvaluated()).containsExactly("p1");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ClientResponse r1 = new ClientResponse(true, "data", null, null, false, null, null, null, null, null);
            ClientResponse r2 = new ClientResponse(true, "data", null, null, false, null, null, null, null, null);
            ClientResponse r3 = new ClientResponse(false, "data", null, null, false, null, null, null, null, null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ClientResponse response = new ClientResponse(
                true, null, null, null, false, null, null, null, null, null
            );
            assertThat(response.toString()).contains("ClientResponse");
        }
    }

    @Nested
    @DisplayName("MCPCheckInputRequest")
    class MCPCheckInputRequestTests {

        @Test
        @DisplayName("should create instance with connector type and statement only")
        void shouldCreateWithBasicFields() {
            MCPCheckInputRequest request = new MCPCheckInputRequest("postgres", "SELECT * FROM users");

            assertThat(request.getConnectorType()).isEqualTo("postgres");
            assertThat(request.getStatement()).isEqualTo("SELECT * FROM users");
            assertThat(request.getOperation()).isEqualTo("execute");
            assertThat(request.getParameters()).isNull();
        }

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            Map<String, Object> params = Map.of("limit", 100);
            MCPCheckInputRequest request = new MCPCheckInputRequest(
                "postgres", "UPDATE users SET name = $1", params, "execute"
            );

            assertThat(request.getConnectorType()).isEqualTo("postgres");
            assertThat(request.getStatement()).isEqualTo("UPDATE users SET name = $1");
            assertThat(request.getOperation()).isEqualTo("execute");
            assertThat(request.getParameters()).containsEntry("limit", 100);
        }

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() throws Exception {
            MCPCheckInputRequest request = new MCPCheckInputRequest(
                "postgres", "SELECT 1", Map.of("timeout", 30), "query"
            );

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("\"connector_type\":\"postgres\"");
            assertThat(json).contains("\"statement\":\"SELECT 1\"");
            assertThat(json).contains("\"operation\":\"query\"");
            assertThat(json).contains("\"parameters\"");
        }

        @Test
        @DisplayName("should omit null parameters in JSON")
        void shouldOmitNullParametersInJson() throws Exception {
            MCPCheckInputRequest request = new MCPCheckInputRequest("postgres", "SELECT 1");

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).doesNotContain("\"parameters\"");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            MCPCheckInputRequest r1 = new MCPCheckInputRequest("postgres", "SELECT 1");
            MCPCheckInputRequest r2 = new MCPCheckInputRequest("postgres", "SELECT 1");
            MCPCheckInputRequest r3 = new MCPCheckInputRequest("mysql", "SELECT 1");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            MCPCheckInputRequest request = new MCPCheckInputRequest("postgres", "SELECT 1");
            assertThat(request.toString()).contains("MCPCheckInputRequest");
            assertThat(request.toString()).contains("postgres");
        }
    }

    @Nested
    @DisplayName("MCPCheckInputResponse")
    class MCPCheckInputResponseTests {

        @Test
        @DisplayName("should create allowed response")
        void shouldCreateAllowedResponse() {
            MCPCheckInputResponse response = new MCPCheckInputResponse(true, null, 3, null);

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.getBlockReason()).isNull();
            assertThat(response.getPoliciesEvaluated()).isEqualTo(3);
            assertThat(response.getPolicyInfo()).isNull();
        }

        @Test
        @DisplayName("should create blocked response")
        void shouldCreateBlockedResponse() {
            ConnectorPolicyInfo policyInfo = new ConnectorPolicyInfo(
                3, true, "SQL injection detected", 0, 1, null
            );
            MCPCheckInputResponse response = new MCPCheckInputResponse(
                false, "SQL injection detected", 3, policyInfo
            );

            assertThat(response.isAllowed()).isFalse();
            assertThat(response.getBlockReason()).isEqualTo("SQL injection detected");
            assertThat(response.getPolicyInfo()).isNotNull();
            assertThat(response.getPolicyInfo().isBlocked()).isTrue();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"allowed\":true," +
                "\"policies_evaluated\":5," +
                "\"policy_info\":{\"policies_evaluated\":5,\"blocked\":false," +
                "\"redactions_applied\":0,\"processing_time_ms\":2}" +
                "}";

            MCPCheckInputResponse response = objectMapper.readValue(json, MCPCheckInputResponse.class);

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.getPoliciesEvaluated()).isEqualTo(5);
            assertThat(response.getPolicyInfo()).isNotNull();
            assertThat(response.getPolicyInfo().getPoliciesEvaluated()).isEqualTo(5);
        }

        @Test
        @DisplayName("should deserialize blocked response from JSON")
        void shouldDeserializeBlockedResponseFromJson() throws Exception {
            String json = "{" +
                "\"allowed\":false," +
                "\"block_reason\":\"DROP TABLE not allowed\"," +
                "\"policies_evaluated\":3," +
                "\"policy_info\":{\"policies_evaluated\":3,\"blocked\":true," +
                "\"block_reason\":\"DROP TABLE not allowed\"," +
                "\"redactions_applied\":0,\"processing_time_ms\":1}" +
                "}";

            MCPCheckInputResponse response = objectMapper.readValue(json, MCPCheckInputResponse.class);

            assertThat(response.isAllowed()).isFalse();
            assertThat(response.getBlockReason()).isEqualTo("DROP TABLE not allowed");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            MCPCheckInputResponse r1 = new MCPCheckInputResponse(true, null, 3, null);
            MCPCheckInputResponse r2 = new MCPCheckInputResponse(true, null, 3, null);
            MCPCheckInputResponse r3 = new MCPCheckInputResponse(false, "blocked", 3, null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            MCPCheckInputResponse response = new MCPCheckInputResponse(true, null, 3, null);
            assertThat(response.toString()).contains("MCPCheckInputResponse");
        }
    }

    @Nested
    @DisplayName("MCPCheckOutputRequest")
    class MCPCheckOutputRequestTests {

        @Test
        @DisplayName("should create instance with connector type and response data only")
        void shouldCreateWithBasicFields() {
            List<Map<String, Object>> data = List.of(Map.of("id", 1, "name", "Alice"));
            MCPCheckOutputRequest request = new MCPCheckOutputRequest("postgres", data);

            assertThat(request.getConnectorType()).isEqualTo("postgres");
            assertThat(request.getResponseData()).hasSize(1);
            assertThat(request.getMessage()).isNull();
            assertThat(request.getMetadata()).isNull();
            assertThat(request.getRowCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should create instance with all fields")
        void shouldCreateWithAllFields() {
            List<Map<String, Object>> data = List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
            );
            Map<String, Object> metadata = Map.of("source", "analytics");
            MCPCheckOutputRequest request = new MCPCheckOutputRequest(
                "postgres", data, "Query completed", metadata, 2
            );

            assertThat(request.getConnectorType()).isEqualTo("postgres");
            assertThat(request.getResponseData()).hasSize(2);
            assertThat(request.getMessage()).isEqualTo("Query completed");
            assertThat(request.getMetadata()).containsEntry("source", "analytics");
            assertThat(request.getRowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() throws Exception {
            List<Map<String, Object>> data = List.of(Map.of("id", 1));
            MCPCheckOutputRequest request = new MCPCheckOutputRequest(
                "postgres", data, "done", Map.of("key", "val"), 1
            );

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("\"connector_type\":\"postgres\"");
            assertThat(json).contains("\"response_data\"");
            assertThat(json).contains("\"message\":\"done\"");
            assertThat(json).contains("\"row_count\":1");
        }

        @Test
        @DisplayName("should omit null fields in JSON")
        void shouldOmitNullFieldsInJson() throws Exception {
            List<Map<String, Object>> data = List.of(Map.of("id", 1));
            MCPCheckOutputRequest request = new MCPCheckOutputRequest("postgres", data);

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).doesNotContain("\"message\"");
            assertThat(json).doesNotContain("\"metadata\"");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            List<Map<String, Object>> data = List.of(Map.of("id", 1));
            MCPCheckOutputRequest r1 = new MCPCheckOutputRequest("postgres", data);
            MCPCheckOutputRequest r2 = new MCPCheckOutputRequest("postgres", data);
            MCPCheckOutputRequest r3 = new MCPCheckOutputRequest("mysql", data);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            List<Map<String, Object>> data = List.of(Map.of("id", 1));
            MCPCheckOutputRequest request = new MCPCheckOutputRequest("postgres", data);
            assertThat(request.toString()).contains("MCPCheckOutputRequest");
            assertThat(request.toString()).contains("postgres");
        }
    }

    @Nested
    @DisplayName("MCPCheckOutputResponse")
    class MCPCheckOutputResponseTests {

        @Test
        @DisplayName("should create allowed response")
        void shouldCreateAllowedResponse() {
            MCPCheckOutputResponse response = new MCPCheckOutputResponse(
                true, null, null, 4, null, null
            );

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.getBlockReason()).isNull();
            assertThat(response.getRedactedData()).isNull();
            assertThat(response.getPoliciesEvaluated()).isEqualTo(4);
            assertThat(response.getExfiltrationInfo()).isNull();
            assertThat(response.getPolicyInfo()).isNull();
        }

        @Test
        @DisplayName("should create blocked response with redacted data")
        void shouldCreateBlockedResponseWithRedactedData() {
            ConnectorPolicyInfo policyInfo = new ConnectorPolicyInfo(
                4, true, "PII detected", 1, 5, null
            );
            List<Map<String, Object>> redacted = List.of(
                Map.of("id", 1, "ssn", "***REDACTED***")
            );
            MCPCheckOutputResponse response = new MCPCheckOutputResponse(
                false, "PII detected", redacted, 4, null, policyInfo
            );

            assertThat(response.isAllowed()).isFalse();
            assertThat(response.getBlockReason()).isEqualTo("PII detected");
            assertThat(response.getRedactedData()).isNotNull();
            assertThat(response.getPolicyInfo().getRedactionsApplied()).isEqualTo(1);
        }

        @Test
        @DisplayName("should create response with exfiltration info")
        void shouldCreateResponseWithExfiltrationInfo() {
            ExfiltrationCheckInfo exfilInfo = new ExfiltrationCheckInfo(
                10, 1000, 2048, 1048576, true
            );
            MCPCheckOutputResponse response = new MCPCheckOutputResponse(
                true, null, null, 3, exfilInfo, null
            );

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.getExfiltrationInfo()).isNotNull();
            assertThat(response.getExfiltrationInfo().getRowsReturned()).isEqualTo(10);
            assertThat(response.getExfiltrationInfo().getRowLimit()).isEqualTo(1000);
            assertThat(response.getExfiltrationInfo().isWithinLimits()).isTrue();
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"allowed\":true," +
                "\"policies_evaluated\":3," +
                "\"exfiltration_info\":{\"rows_returned\":5,\"row_limit\":500," +
                "\"bytes_returned\":1024,\"byte_limit\":524288,\"within_limits\":true}," +
                "\"policy_info\":{\"policies_evaluated\":3,\"blocked\":false," +
                "\"redactions_applied\":0,\"processing_time_ms\":2}" +
                "}";

            MCPCheckOutputResponse response = objectMapper.readValue(json, MCPCheckOutputResponse.class);

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.getPoliciesEvaluated()).isEqualTo(3);
            assertThat(response.getExfiltrationInfo()).isNotNull();
            assertThat(response.getExfiltrationInfo().getRowsReturned()).isEqualTo(5);
            assertThat(response.getPolicyInfo()).isNotNull();
        }

        @Test
        @DisplayName("should deserialize blocked response with redacted data from JSON")
        void shouldDeserializeBlockedResponseFromJson() throws Exception {
            String json = "{" +
                "\"allowed\":false," +
                "\"block_reason\":\"PII content detected\"," +
                "\"redacted_data\":[{\"id\":1,\"ssn\":\"***REDACTED***\"}]," +
                "\"policies_evaluated\":4," +
                "\"policy_info\":{\"policies_evaluated\":4,\"blocked\":true," +
                "\"block_reason\":\"PII content detected\"," +
                "\"redactions_applied\":1,\"processing_time_ms\":3}" +
                "}";

            MCPCheckOutputResponse response = objectMapper.readValue(json, MCPCheckOutputResponse.class);

            assertThat(response.isAllowed()).isFalse();
            assertThat(response.getBlockReason()).isEqualTo("PII content detected");
            assertThat(response.getRedactedData()).isNotNull();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            MCPCheckOutputResponse r1 = new MCPCheckOutputResponse(true, null, null, 3, null, null);
            MCPCheckOutputResponse r2 = new MCPCheckOutputResponse(true, null, null, 3, null, null);
            MCPCheckOutputResponse r3 = new MCPCheckOutputResponse(false, "blocked", null, 3, null, null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            MCPCheckOutputResponse response = new MCPCheckOutputResponse(
                true, null, null, 3, null, null
            );
            assertThat(response.toString()).contains("MCPCheckOutputResponse");
        }
    }
}
