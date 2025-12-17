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
package com.getaxonflow.sdk.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Exception Classes")
class ExceptionsTest {

    @Test
    @DisplayName("AxonFlowException - should create with message")
    void axonFlowExceptionWithMessage() {
        AxonFlowException ex = new AxonFlowException("Test error");

        assertThat(ex.getMessage()).isEqualTo("Test error");
        assertThat(ex.getStatusCode()).isEqualTo(0);
        assertThat(ex.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("AxonFlowException - should create with full details")
    void axonFlowExceptionWithFullDetails() {
        AxonFlowException ex = new AxonFlowException("Test error", 500, "INTERNAL_ERROR");

        assertThat(ex.getMessage()).isEqualTo("Test error");
        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getErrorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("AxonFlowException - toString should include details")
    void axonFlowExceptionToString() {
        AxonFlowException ex = new AxonFlowException("Test error", 500, "INTERNAL_ERROR");

        String str = ex.toString();
        assertThat(str).contains("Test error");
        assertThat(str).contains("500");
        assertThat(str).contains("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("AuthenticationException - should set correct status code")
    void authenticationException() {
        AuthenticationException ex = new AuthenticationException("Invalid credentials");

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
        assertThat(ex.getStatusCode()).isEqualTo(401);
        assertThat(ex.getErrorCode()).isEqualTo("AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("PolicyViolationException - should extract policy name")
    void policyViolationExceptionExtractsPolicyName() {
        PolicyViolationException ex = new PolicyViolationException(
            "Request blocked by policy: sql_injection_detection");

        assertThat(ex.getPolicyName()).isEqualTo("sql_injection_detection");
        assertThat(ex.getBlockReason()).isEqualTo("Request blocked by policy: sql_injection_detection");
        assertThat(ex.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("PolicyViolationException - should accept explicit policy name")
    void policyViolationExceptionWithExplicitPolicy() {
        PolicyViolationException ex = new PolicyViolationException(
            "PII detected in request",
            "pii_detection",
            List.of("pii_detection", "rate_limit")
        );

        assertThat(ex.getPolicyName()).isEqualTo("pii_detection");
        assertThat(ex.getPoliciesEvaluated()).containsExactly("pii_detection", "rate_limit");
    }

    @Test
    @DisplayName("PolicyViolationException - should handle bracket format")
    void policyViolationExceptionBracketFormat() {
        PolicyViolationException ex = new PolicyViolationException("[rate_limit] Too many requests");

        assertThat(ex.getPolicyName()).isEqualTo("rate_limit");
    }

    @Test
    @DisplayName("RateLimitException - should calculate retry duration")
    void rateLimitExceptionRetryDuration() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitException ex = new RateLimitException("Rate limit exceeded", 100, 0, resetTime);

        assertThat(ex.getLimit()).isEqualTo(100);
        assertThat(ex.getRemaining()).isEqualTo(0);
        assertThat(ex.getResetAt()).isEqualTo(resetTime);
        assertThat(ex.getRetryAfter()).isGreaterThan(Duration.ZERO);
        assertThat(ex.getStatusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("RateLimitException - should handle past reset time")
    void rateLimitExceptionPastResetTime() {
        Instant pastTime = Instant.now().minusSeconds(60);
        RateLimitException ex = new RateLimitException("Rate limit exceeded", 100, 0, pastTime);

        assertThat(ex.getRetryAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("TimeoutException - should include timeout duration")
    void timeoutException() {
        Duration timeout = Duration.ofSeconds(30);
        TimeoutException ex = new TimeoutException("Request timed out", timeout);

        assertThat(ex.getMessage()).isEqualTo("Request timed out");
        assertThat(ex.getTimeout()).isEqualTo(timeout);
        assertThat(ex.getErrorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("ConnectionException - should include host and port")
    void connectionException() {
        ConnectionException ex = new ConnectionException(
            "Connection refused",
            "api.example.com",
            8080,
            new RuntimeException("Connection refused")
        );

        assertThat(ex.getMessage()).isEqualTo("Connection refused");
        assertThat(ex.getHost()).isEqualTo("api.example.com");
        assertThat(ex.getPort()).isEqualTo(8080);
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    @DisplayName("ConfigurationException - should include config key")
    void configurationException() {
        ConfigurationException ex = new ConfigurationException("Invalid URL format", "agentUrl");

        assertThat(ex.getMessage()).isEqualTo("Invalid URL format");
        assertThat(ex.getConfigKey()).isEqualTo("agentUrl");
    }

    @Test
    @DisplayName("ConnectorException - should include connector details")
    void connectorException() {
        ConnectorException ex = new ConnectorException(
            "Connector query failed",
            "salesforce",
            "getAccounts"
        );

        assertThat(ex.getMessage()).isEqualTo("Connector query failed");
        assertThat(ex.getConnectorId()).isEqualTo("salesforce");
        assertThat(ex.getOperation()).isEqualTo("getAccounts");
    }

    @Test
    @DisplayName("PlanExecutionException - should include plan details")
    void planExecutionException() {
        PlanExecutionException ex = new PlanExecutionException(
            "Step failed",
            "plan_123",
            "step_002"
        );

        assertThat(ex.getMessage()).isEqualTo("Step failed");
        assertThat(ex.getPlanId()).isEqualTo("plan_123");
        assertThat(ex.getFailedStep()).isEqualTo("step_002");
    }
}
