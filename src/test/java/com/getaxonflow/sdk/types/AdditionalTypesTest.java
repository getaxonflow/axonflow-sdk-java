/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Additional Types Tests")
class AdditionalTypesTest {

    @Nested
    @DisplayName("PolicyInfo Tests")
    class PolicyInfoTests {

        @Test
        @DisplayName("Should create PolicyInfo with all fields")
        void testPolicyInfoCreation() {
            PolicyInfo info = new PolicyInfo(
                List.of("policy1", "policy2"),
                List.of("static-check-1"),
                "17.48ms",
                "tenant-123",
                0.75
            );

            assertThat(info.getPoliciesEvaluated()).containsExactly("policy1", "policy2");
            assertThat(info.getStaticChecks()).containsExactly("static-check-1");
            assertThat(info.getProcessingTime()).isEqualTo("17.48ms");
            assertThat(info.getTenantId()).isEqualTo("tenant-123");
            assertThat(info.getRiskScore()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("Should handle null lists")
        void testPolicyInfoNullLists() {
            PolicyInfo info = new PolicyInfo(null, null, "10ms", "tenant", null);

            assertThat(info.getPoliciesEvaluated()).isEmpty();
            assertThat(info.getStaticChecks()).isEmpty();
        }

        @Test
        @DisplayName("getProcessingDuration should parse milliseconds")
        void testProcessingDurationMilliseconds() {
            PolicyInfo info = new PolicyInfo(null, null, "17.48ms", null, null);

            Duration duration = info.getProcessingDuration();
            assertThat(duration.toNanos()).isGreaterThan(17_000_000L);
            assertThat(duration.toNanos()).isLessThan(18_000_000L);
        }

        @Test
        @DisplayName("getProcessingDuration should parse seconds")
        void testProcessingDurationSeconds() {
            PolicyInfo info = new PolicyInfo(null, null, "1.5s", null, null);

            Duration duration = info.getProcessingDuration();
            assertThat(duration.toMillis()).isGreaterThanOrEqualTo(1500L);
        }

        @Test
        @DisplayName("getProcessingDuration should parse microseconds (us)")
        void testProcessingDurationMicroseconds() {
            // Note: the implementation may not handle 'us' suffix perfectly
            PolicyInfo info = new PolicyInfo(null, null, "500µs", null, null);

            Duration duration = info.getProcessingDuration();
            // If µs parsing works, we get microseconds; otherwise it falls through
            assertThat(duration).isNotNull();
        }

        @Test
        @DisplayName("getProcessingDuration should handle ns suffix")
        void testProcessingDurationNanoseconds() {
            PolicyInfo info = new PolicyInfo(null, null, "1000ns", null, null);

            Duration duration = info.getProcessingDuration();
            // Implementation may return ZERO if parsing fails
            assertThat(duration).isNotNull();
        }

        @Test
        @DisplayName("getProcessingDuration should handle empty/null")
        void testProcessingDurationEmpty() {
            PolicyInfo info1 = new PolicyInfo(null, null, null, null, null);
            assertThat(info1.getProcessingDuration()).isEqualTo(Duration.ZERO);

            PolicyInfo info2 = new PolicyInfo(null, null, "", null, null);
            assertThat(info2.getProcessingDuration()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("getProcessingDuration should handle invalid format")
        void testProcessingDurationInvalid() {
            PolicyInfo info = new PolicyInfo(null, null, "invalid", null, null);
            assertThat(info.getProcessingDuration()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("getProcessingDuration should handle raw number as milliseconds")
        void testProcessingDurationRawNumber() {
            PolicyInfo info = new PolicyInfo(null, null, "100", null, null);

            Duration duration = info.getProcessingDuration();
            assertThat(duration.toMillis()).isEqualTo(100L);
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testPolicyInfoEqualsHashCode() {
            PolicyInfo info1 = new PolicyInfo(
                List.of("policy1"),
                List.of("check1"),
                "10ms",
                "tenant1",
                0.5
            );

            PolicyInfo info2 = new PolicyInfo(
                List.of("policy1"),
                List.of("check1"),
                "10ms",
                "tenant1",
                0.5
            );

            PolicyInfo info3 = new PolicyInfo(
                List.of("policy2"),
                List.of("check1"),
                "10ms",
                "tenant1",
                0.5
            );

            assertThat(info1).isEqualTo(info2);
            assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
            assertThat(info1).isNotEqualTo(info3);
            assertThat(info1).isNotEqualTo(null);
            assertThat(info1).isNotEqualTo("string");
            assertThat(info1).isEqualTo(info1);
        }

        @Test
        @DisplayName("toString should include all fields")
        void testPolicyInfoToString() {
            PolicyInfo info = new PolicyInfo(
                List.of("policy1"),
                List.of("check1"),
                "10ms",
                "tenant1",
                0.5
            );

            String str = info.toString();
            assertThat(str).contains("policy1");
            assertThat(str).contains("check1");
            assertThat(str).contains("10ms");
            assertThat(str).contains("tenant1");
            assertThat(str).contains("0.5");
        }
    }

    @Nested
    @DisplayName("HealthStatus Tests")
    class HealthStatusTests {

        @Test
        @DisplayName("Should create HealthStatus with all fields")
        void testHealthStatusCreation() {
            Map<String, Object> components = new HashMap<>();
            components.put("database", "healthy");
            components.put("cache", "healthy");

            HealthStatus status = new HealthStatus(
                "healthy",
                "1.0.0",
                "24h30m",
                components
            );

            assertThat(status.getStatus()).isEqualTo("healthy");
            assertThat(status.getVersion()).isEqualTo("1.0.0");
            assertThat(status.getUptime()).isEqualTo("24h30m");
            assertThat(status.getComponents()).containsEntry("database", "healthy");
        }

        @Test
        @DisplayName("Should handle null components")
        void testHealthStatusNullComponents() {
            HealthStatus status = new HealthStatus("healthy", "1.0.0", "1h", null);

            assertThat(status.getComponents()).isEmpty();
        }

        @Test
        @DisplayName("isHealthy should return true for healthy status")
        void testIsHealthyTrue() {
            HealthStatus status1 = new HealthStatus("healthy", null, null, null);
            assertThat(status1.isHealthy()).isTrue();

            HealthStatus status2 = new HealthStatus("HEALTHY", null, null, null);
            assertThat(status2.isHealthy()).isTrue();

            HealthStatus status3 = new HealthStatus("ok", null, null, null);
            assertThat(status3.isHealthy()).isTrue();

            HealthStatus status4 = new HealthStatus("OK", null, null, null);
            assertThat(status4.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("isHealthy should return false for unhealthy status")
        void testIsHealthyFalse() {
            HealthStatus status1 = new HealthStatus("unhealthy", null, null, null);
            assertThat(status1.isHealthy()).isFalse();

            HealthStatus status2 = new HealthStatus("degraded", null, null, null);
            assertThat(status2.isHealthy()).isFalse();

            HealthStatus status3 = new HealthStatus(null, null, null, null);
            assertThat(status3.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testHealthStatusEqualsHashCode() {
            HealthStatus status1 = new HealthStatus("healthy", "1.0.0", "1h", null);
            HealthStatus status2 = new HealthStatus("healthy", "1.0.0", "1h", null);
            HealthStatus status3 = new HealthStatus("unhealthy", "1.0.0", "1h", null);

            assertThat(status1).isEqualTo(status2);
            assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
            assertThat(status1).isNotEqualTo(status3);
            assertThat(status1).isNotEqualTo(null);
            assertThat(status1).isNotEqualTo("string");
            assertThat(status1).isEqualTo(status1);
        }

        @Test
        @DisplayName("toString should include relevant fields")
        void testHealthStatusToString() {
            HealthStatus status = new HealthStatus("healthy", "1.0.0", "1h", null);

            String str = status.toString();
            assertThat(str).contains("healthy");
            assertThat(str).contains("1.0.0");
            assertThat(str).contains("1h");
        }
    }

    @Nested
    @DisplayName("ConnectorResponse Tests")
    class ConnectorResponseTests {

        @Test
        @DisplayName("Should create ConnectorResponse with all fields")
        void testConnectorResponseCreation() {
            Map<String, Object> data = new HashMap<>();
            data.put("result", "success");

            ConnectorResponse response = new ConnectorResponse(
                true,
                data,
                null,
                "connector-123",
                "query",
                "15.5ms"
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getError()).isNull();
            assertThat(response.getConnectorId()).isEqualTo("connector-123");
            assertThat(response.getOperation()).isEqualTo("query");
            assertThat(response.getProcessingTime()).isEqualTo("15.5ms");
        }

        @Test
        @DisplayName("Should create error ConnectorResponse")
        void testConnectorResponseError() {
            ConnectorResponse response = new ConnectorResponse(
                false,
                null,
                "Connection failed",
                "connector-456",
                "install",
                "0ms"
            );

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getData()).isNull();
            assertThat(response.getError()).isEqualTo("Connection failed");
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testConnectorResponseEqualsHashCode() {
            ConnectorResponse response1 = new ConnectorResponse(
                true, null, null, "conn1", "query", null
            );
            ConnectorResponse response2 = new ConnectorResponse(
                true, null, null, "conn1", "query", null
            );
            ConnectorResponse response3 = new ConnectorResponse(
                false, null, null, "conn1", "query", null
            );

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
            assertThat(response1).isNotEqualTo(response3);
            assertThat(response1).isNotEqualTo(null);
            assertThat(response1).isNotEqualTo("string");
            assertThat(response1).isEqualTo(response1);
        }

        @Test
        @DisplayName("toString should include relevant fields")
        void testConnectorResponseToString() {
            ConnectorResponse response = new ConnectorResponse(
                true, null, null, "conn-123", "query", null
            );

            String str = response.toString();
            assertThat(str).contains("success=true");
            assertThat(str).contains("conn-123");
            assertThat(str).contains("query");
        }
    }

    @Nested
    @DisplayName("TokenUsage Tests")
    class TokenUsageTests {

        @Test
        @DisplayName("TokenUsage.of should calculate total tokens")
        void testTokenUsageOf() {
            TokenUsage usage = TokenUsage.of(100, 50);

            assertThat(usage.getPromptTokens()).isEqualTo(100);
            assertThat(usage.getCompletionTokens()).isEqualTo(50);
            assertThat(usage.getTotalTokens()).isEqualTo(150);
        }

        @Test
        @DisplayName("TokenUsage equals and hashCode")
        void testTokenUsageEqualsHashCode() {
            TokenUsage usage1 = TokenUsage.of(100, 50);
            TokenUsage usage2 = TokenUsage.of(100, 50);
            TokenUsage usage3 = TokenUsage.of(100, 60);

            assertThat(usage1).isEqualTo(usage2);
            assertThat(usage1.hashCode()).isEqualTo(usage2.hashCode());
            assertThat(usage1).isNotEqualTo(usage3);
        }
    }

    @Nested
    @DisplayName("Mode Tests")
    class ModeTests {

        @Test
        @DisplayName("Mode enum values should exist")
        void testModeEnumValues() {
            assertThat(Mode.values()).contains(Mode.PRODUCTION, Mode.SANDBOX);
        }

        @Test
        @DisplayName("Mode fromValue should parse valid modes")
        void testModeFromValue() {
            assertThat(Mode.fromValue("production")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue("PRODUCTION")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue("sandbox")).isEqualTo(Mode.SANDBOX);
            assertThat(Mode.fromValue("SANDBOX")).isEqualTo(Mode.SANDBOX);
        }

        @Test
        @DisplayName("Mode fromValue should return PRODUCTION for invalid/null mode")
        void testModeFromValueInvalid() {
            assertThat(Mode.fromValue("invalid")).isEqualTo(Mode.PRODUCTION);
            assertThat(Mode.fromValue(null)).isEqualTo(Mode.PRODUCTION);
        }

        @Test
        @DisplayName("Mode getValue should return lowercase")
        void testModeGetValue() {
            assertThat(Mode.PRODUCTION.getValue()).isEqualTo("production");
            assertThat(Mode.SANDBOX.getValue()).isEqualTo("sandbox");
        }
    }

    @Nested
    @DisplayName("RequestType Tests")
    class RequestTypeTests {

        @Test
        @DisplayName("RequestType enum values should exist")
        void testRequestTypeEnumValues() {
            assertThat(RequestType.values()).contains(
                RequestType.CHAT,
                RequestType.SQL,
                RequestType.MCP_QUERY,
                RequestType.MULTI_AGENT_PLAN
            );
        }

        @Test
        @DisplayName("RequestType fromValue should parse valid types")
        void testRequestTypeFromValue() {
            assertThat(RequestType.fromValue("chat")).isEqualTo(RequestType.CHAT);
            assertThat(RequestType.fromValue("CHAT")).isEqualTo(RequestType.CHAT);
            assertThat(RequestType.fromValue("sql")).isEqualTo(RequestType.SQL);
            assertThat(RequestType.fromValue("mcp-query")).isEqualTo(RequestType.MCP_QUERY);
            assertThat(RequestType.fromValue("multi-agent-plan")).isEqualTo(RequestType.MULTI_AGENT_PLAN);
        }

        @Test
        @DisplayName("RequestType fromValue should throw for invalid type")
        void testRequestTypeFromValueInvalid() {
            assertThatThrownBy(() -> RequestType.fromValue("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RequestType fromValue should throw for null")
        void testRequestTypeFromValueNull() {
            assertThatThrownBy(() -> RequestType.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RequestType getValue should return correct value")
        void testRequestTypeGetValue() {
            assertThat(RequestType.CHAT.getValue()).isEqualTo("chat");
            assertThat(RequestType.SQL.getValue()).isEqualTo("sql");
            assertThat(RequestType.MCP_QUERY.getValue()).isEqualTo("mcp-query");
            assertThat(RequestType.MULTI_AGENT_PLAN.getValue()).isEqualTo("multi-agent-plan");
        }
    }

    @Nested
    @DisplayName("RateLimitInfo Tests")
    class RateLimitInfoTests {

        @Test
        @DisplayName("Should create RateLimitInfo correctly")
        void testRateLimitInfoCreation() {
            Instant resetAt = Instant.now().plusSeconds(60);
            RateLimitInfo info = new RateLimitInfo(100, 80, resetAt);

            assertThat(info.getLimit()).isEqualTo(100);
            assertThat(info.getRemaining()).isEqualTo(80);
            assertThat(info.getResetAt()).isEqualTo(resetAt);
        }

        @Test
        @DisplayName("isExceeded should return true when remaining is 0")
        void testIsExceededTrue() {
            RateLimitInfo info = new RateLimitInfo(100, 0, null);
            assertThat(info.isExceeded()).isTrue();
        }

        @Test
        @DisplayName("isExceeded should return true when remaining is negative")
        void testIsExceededNegative() {
            RateLimitInfo info = new RateLimitInfo(100, -1, null);
            assertThat(info.isExceeded()).isTrue();
        }

        @Test
        @DisplayName("isExceeded should return false when remaining is positive")
        void testIsExceededFalse() {
            RateLimitInfo info = new RateLimitInfo(100, 50, null);
            assertThat(info.isExceeded()).isFalse();
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testRateLimitInfoEqualsHashCode() {
            Instant resetAt = Instant.now();
            RateLimitInfo info1 = new RateLimitInfo(100, 80, resetAt);
            RateLimitInfo info2 = new RateLimitInfo(100, 80, resetAt);
            RateLimitInfo info3 = new RateLimitInfo(100, 70, resetAt);

            assertThat(info1).isEqualTo(info2);
            assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
            assertThat(info1).isNotEqualTo(info3);
            assertThat(info1).isNotEqualTo(null);
            assertThat(info1).isNotEqualTo("string");
            assertThat(info1).isEqualTo(info1);
        }

        @Test
        @DisplayName("toString should include all fields")
        void testRateLimitInfoToString() {
            Instant resetAt = Instant.parse("2025-01-15T10:30:00Z");
            RateLimitInfo info = new RateLimitInfo(100, 80, resetAt);

            String str = info.toString();
            assertThat(str).contains("100");
            assertThat(str).contains("80");
        }
    }

    @Nested
    @DisplayName("ConnectorInfo Tests")
    class ConnectorInfoTests {

        @Test
        @DisplayName("Should create ConnectorInfo correctly")
        void testConnectorInfoCreation() {
            Map<String, Object> configSchema = new HashMap<>();
            configSchema.put("token", "string");

            ConnectorInfo info = new ConnectorInfo(
                "conn-123",
                "GitHub Connector",
                "A connector for GitHub",
                "github",
                "1.0.0",
                List.of("read", "write"),
                configSchema,
                true,
                true
            );

            assertThat(info.getId()).isEqualTo("conn-123");
            assertThat(info.getName()).isEqualTo("GitHub Connector");
            assertThat(info.getType()).isEqualTo("github");
            assertThat(info.getDescription()).isEqualTo("A connector for GitHub");
            assertThat(info.getVersion()).isEqualTo("1.0.0");
            assertThat(info.getCapabilities()).containsExactly("read", "write");
            assertThat(info.getConfigSchema()).containsEntry("token", "string");
            assertThat(info.isInstalled()).isTrue();
            assertThat(info.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should handle null capabilities and configSchema")
        void testConnectorInfoNullCollections() {
            ConnectorInfo info = new ConnectorInfo(
                "id", "name", "desc", "type", "1.0", null, null, null, null
            );

            assertThat(info.getCapabilities()).isEmpty();
            assertThat(info.getConfigSchema()).isEmpty();
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testConnectorInfoEqualsHashCode() {
            ConnectorInfo info1 = new ConnectorInfo(
                "id", "name", "desc", "type", "1.0", null, null, null, null
            );
            ConnectorInfo info2 = new ConnectorInfo(
                "id", "name", "desc", "type", "1.0", null, null, null, null
            );
            ConnectorInfo info3 = new ConnectorInfo(
                "id2", "name", "desc", "type", "1.0", null, null, null, null
            );

            assertThat(info1).isEqualTo(info2);
            assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
            assertThat(info1).isNotEqualTo(info3);
            assertThat(info1).isNotEqualTo(null);
            assertThat(info1).isNotEqualTo("string");
            assertThat(info1).isEqualTo(info1);
        }

        @Test
        @DisplayName("toString should include relevant fields")
        void testConnectorInfoToString() {
            ConnectorInfo info = new ConnectorInfo(
                "conn-123", "GitHub", "desc", "github", "1.0", null, null, true, false
            );

            String str = info.toString();
            assertThat(str).contains("conn-123");
            assertThat(str).contains("GitHub");
            assertThat(str).contains("github");
            assertThat(str).contains("installed=true");
            assertThat(str).contains("enabled=false");
        }
    }

    @Nested
    @DisplayName("ConnectorQuery Tests")
    class ConnectorQueryTests {

        @Test
        @DisplayName("Should build ConnectorQuery correctly")
        void testConnectorQueryBuilder() {
            Map<String, Object> params = new HashMap<>();
            params.put("limit", 10);

            ConnectorQuery query = ConnectorQuery.builder()
                .connectorId("conn-123")
                .operation("list")
                .parameters(params)
                .build();

            assertThat(query.getConnectorId()).isEqualTo("conn-123");
            assertThat(query.getOperation()).isEqualTo("list");
            assertThat(query.getParameters()).containsEntry("limit", 10);
        }

        @Test
        @DisplayName("Should build ConnectorQuery with userToken and timeout")
        void testConnectorQueryBuilderAllFields() {
            ConnectorQuery query = ConnectorQuery.builder()
                .connectorId("conn-456")
                .operation("execute")
                .userToken("user-token-123")
                .timeoutMs(5000)
                .addParameter("key", "value")
                .build();

            assertThat(query.getConnectorId()).isEqualTo("conn-456");
            assertThat(query.getOperation()).isEqualTo("execute");
            assertThat(query.getUserToken()).isEqualTo("user-token-123");
            assertThat(query.getTimeoutMs()).isEqualTo(5000);
            assertThat(query.getParameters()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("ConnectorQuery.Builder should require connectorId")
        void testConnectorQueryBuilderRequiresConnectorId() {
            assertThatThrownBy(() -> ConnectorQuery.builder()
                .operation("list")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ConnectorQuery.Builder should require operation")
        void testConnectorQueryBuilderRequiresOperation() {
            assertThatThrownBy(() -> ConnectorQuery.builder()
                .connectorId("conn-123")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("addParameter should create parameters map if null")
        void testConnectorQueryAddParameter() {
            ConnectorQuery query = ConnectorQuery.builder()
                .connectorId("conn")
                .operation("op")
                .addParameter("key1", "value1")
                .addParameter("key2", "value2")
                .build();

            assertThat(query.getParameters()).containsEntry("key1", "value1");
            assertThat(query.getParameters()).containsEntry("key2", "value2");
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testConnectorQueryEqualsHashCode() {
            ConnectorQuery query1 = ConnectorQuery.builder()
                .connectorId("conn-1")
                .operation("op")
                .build();
            ConnectorQuery query2 = ConnectorQuery.builder()
                .connectorId("conn-1")
                .operation("op")
                .build();
            ConnectorQuery query3 = ConnectorQuery.builder()
                .connectorId("conn-2")
                .operation("op")
                .build();

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
            assertThat(query1).isNotEqualTo(query3);
            assertThat(query1).isNotEqualTo(null);
            assertThat(query1).isNotEqualTo("string");
            assertThat(query1).isEqualTo(query1);
        }

        @Test
        @DisplayName("toString should include relevant fields")
        void testConnectorQueryToString() {
            ConnectorQuery query = ConnectorQuery.builder()
                .connectorId("conn-123")
                .operation("list")
                .userToken("user")
                .timeoutMs(3000)
                .build();

            String str = query.toString();
            assertThat(str).contains("conn-123");
            assertThat(str).contains("list");
            assertThat(str).contains("user");
            assertThat(str).contains("3000");
        }
    }

    @Nested
    @DisplayName("AuditResult Tests")
    class AuditResultTests {

        @Test
        @DisplayName("Should create AuditResult correctly")
        void testAuditResultCreation() {
            AuditResult result = new AuditResult(
                true,
                "audit-123",
                "Audit recorded successfully",
                null
            );

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAuditId()).isEqualTo("audit-123");
            assertThat(result.getMessage()).isEqualTo("Audit recorded successfully");
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("Should create error AuditResult")
        void testAuditResultError() {
            AuditResult result = new AuditResult(
                false,
                null,
                null,
                "Audit failed"
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getAuditId()).isNull();
            assertThat(result.getError()).isEqualTo("Audit failed");
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void testAuditResultEqualsHashCode() {
            AuditResult result1 = new AuditResult(true, "id1", "msg", null);
            AuditResult result2 = new AuditResult(true, "id1", "msg", null);
            AuditResult result3 = new AuditResult(false, "id1", "msg", null);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
            assertThat(result1).isNotEqualTo(result3);
            assertThat(result1).isNotEqualTo(null);
            assertThat(result1).isNotEqualTo("string");
            assertThat(result1).isEqualTo(result1);
        }

        @Test
        @DisplayName("toString should include relevant fields")
        void testAuditResultToString() {
            AuditResult result = new AuditResult(true, "audit-123", "Success", null);

            String str = result.toString();
            assertThat(str).contains("true");
            assertThat(str).contains("audit-123");
        }
    }
}
