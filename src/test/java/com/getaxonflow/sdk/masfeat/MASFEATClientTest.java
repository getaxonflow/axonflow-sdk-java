/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.masfeat;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MAS FEAT client methods.
 */
@DisplayName("MAS FEAT Client Tests")
class MASFEATClientTest {

    private MockWebServer mockWebServer;
    private AxonFlow client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        client = AxonFlow.builder()
                .endpoint(mockWebServer.url("/").toString())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("Registry Methods")
    class RegistryMethodsTest {

        @Test
        @DisplayName("Should register a new AI system")
        void testRegisterSystem() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"sys-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"credit-model-v1\"," +
                    "\"system_name\": \"Credit Scoring Model\"," +
                    "\"use_case\": \"credit_scoring\"," +
                    "\"owner_team\": \"data-science\"," +
                    "\"risk_rating_impact\": 3," +
                    "\"risk_rating_complexity\": 2," +
                    "\"risk_rating_reliance\": 1," +
                    "\"materiality\": \"high\"," +
                    "\"status\": \"draft\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            RegisterSystemRequest request = RegisterSystemRequest.builder()
                    .systemId("credit-model-v1")
                    .systemName("Credit Scoring Model")
                    .useCase(AISystemUseCase.CREDIT_SCORING)
                    .ownerTeam("data-science")
                    .customerImpact(3)
                    .modelComplexity(2)
                    .humanReliance(1)
                    .build();

            AISystemRegistry result = client.masfeat().registerSystem(request);

            assertThat(result.getId()).isEqualTo("sys-123");
            assertThat(result.getSystemName()).isEqualTo("Credit Scoring Model");
            assertThat(result.getMateriality()).isEqualTo(MaterialityClassification.HIGH);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/registry");
        }

        @Test
        @DisplayName("Should get a system by ID")
        void testGetSystem() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"sys-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"model-v1\"," +
                    "\"system_name\": \"Test Model\"," +
                    "\"use_case\": \"credit_scoring\"," +
                    "\"owner_team\": \"team\"," +
                    "\"materiality\": \"high\"," +
                    "\"status\": \"active\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            AISystemRegistry result = client.masfeat().getSystem("sys-123");

            assertThat(result.getId()).isEqualTo("sys-123");
            assertThat(result.getStatus()).isEqualTo(SystemStatus.ACTIVE);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/registry/sys-123");
        }

        @Test
        @DisplayName("Should activate a system")
        void testActivateSystem() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"sys-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"model-v1\"," +
                    "\"system_name\": \"Test Model\"," +
                    "\"use_case\": \"credit_scoring\"," +
                    "\"owner_team\": \"team\"," +
                    "\"materiality\": \"high\"," +
                    "\"status\": \"active\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            AISystemRegistry result = client.masfeat().activateSystem("sys-123");

            assertThat(result.getStatus()).isEqualTo(SystemStatus.ACTIVE);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/registry/sys-123");
        }

        @Test
        @DisplayName("Should get registry summary")
        void testGetRegistrySummary() throws Exception {
            String responseJson = "{" +
                    "\"total_systems\": 10," +
                    "\"active_systems\": 8," +
                    "\"high_materiality_count\": 2," +
                    "\"medium_materiality_count\": 5," +
                    "\"low_materiality_count\": 3," +
                    "\"by_use_case\": {\"credit_scoring\": 4}," +
                    "\"by_status\": {\"active\": 8}" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            RegistrySummary result = client.masfeat().getRegistrySummary();

            assertThat(result.getTotalSystems()).isEqualTo(10);
            assertThat(result.getActiveSystems()).isEqualTo(8);
            assertThat(result.getHighMaterialityCount()).isEqualTo(2);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/registry/summary");
        }
    }

    @Nested
    @DisplayName("Assessment Methods")
    class AssessmentMethodsTest {

        @Test
        @DisplayName("Should create a new assessment")
        void testCreateAssessment() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"pending\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            CreateAssessmentRequest request = CreateAssessmentRequest.builder()
                    .systemId("sys-789")
                    .assessmentType("annual")
                    .build();

            FEATAssessment result = client.masfeat().createAssessment(request);

            assertThat(result.getId()).isEqualTo("assess-123");
            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.PENDING);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/assessments");
        }

        @Test
        @DisplayName("Should get an assessment by ID")
        void testGetAssessment() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"completed\"," +
                    "\"overall_score\": 89," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            FEATAssessment result = client.masfeat().getAssessment("assess-123");

            assertThat(result.getId()).isEqualTo("assess-123");
            assertThat(result.getOverallScore()).isEqualTo(89);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/assessments/assess-123");
        }

        @Test
        @DisplayName("Should submit an assessment for review")
        void testSubmitAssessment() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"completed\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            FEATAssessment result = client.masfeat().submitAssessment("assess-123");

            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.COMPLETED);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/assessments/assess-123/submit");
        }

        @Test
        @DisplayName("Should approve an assessment")
        void testApproveAssessment() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"approved\"," +
                    "\"approved_by\": \"admin@example.com\"," +
                    "\"approved_at\": \"2026-01-23T13:00:00Z\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T13:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            FEATAssessment result = client.masfeat().approveAssessment("assess-123");

            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.APPROVED);
            assertThat(result.getApprovedBy()).isEqualTo("admin@example.com");

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/assessments/assess-123/approve");
        }
    }

    @Nested
    @DisplayName("Kill Switch Methods")
    class KillSwitchMethodsTest {

        @Test
        @DisplayName("Should get kill switch status")
        void testGetKillSwitch() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"ks-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"status\": \"enabled\"," +
                    "\"auto_trigger_enabled\": true," +
                    "\"accuracy_threshold\": 0.95," +
                    "\"bias_threshold\": 0.1," +
                    "\"error_rate_threshold\": 0.05," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            KillSwitch result = client.masfeat().getKillSwitch("sys-789");

            assertThat(result.getId()).isEqualTo("ks-123");
            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.ENABLED);
            assertThat(result.getAccuracyThreshold()).isEqualTo(0.95);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/killswitch/sys-789");
        }

        @Test
        @DisplayName("Should configure kill switch")
        void testConfigureKillSwitch() throws Exception {
            String responseJson = "{" +
                    "\"id\": \"ks-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"status\": \"enabled\"," +
                    "\"auto_trigger_enabled\": true," +
                    "\"accuracy_threshold\": 0.95," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            ConfigureKillSwitchRequest request = ConfigureKillSwitchRequest.builder()
                    .accuracyThreshold(0.95)
                    .autoTriggerEnabled(true)
                    .build();

            KillSwitch result = client.masfeat().configureKillSwitch("sys-789", request);

            assertThat(result.isAutoTriggerEnabled()).isTrue();

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/killswitch/sys-789/configure");
        }

        @Test
        @DisplayName("Should trigger kill switch")
        void testTriggerKillSwitch() throws Exception {
            String responseJson = "{" +
                    "\"kill_switch\": {" +
                    "\"id\": \"ks-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"status\": \"triggered\"," +
                    "\"auto_trigger_enabled\": true," +
                    "\"triggered_reason\": \"Manual trigger\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}," +
                    "\"message\": \"Kill switch triggered\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            KillSwitch result = client.masfeat().triggerKillSwitch("sys-789", "Manual trigger");

            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.TRIGGERED);
            assertThat(result.getTriggeredReason()).isEqualTo("Manual trigger");

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/killswitch/sys-789/trigger");
        }

        @Test
        @DisplayName("Should restore kill switch")
        void testRestoreKillSwitch() throws Exception {
            String responseJson = "{" +
                    "\"kill_switch\": {" +
                    "\"id\": \"ks-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"status\": \"enabled\"," +
                    "\"auto_trigger_enabled\": true," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}," +
                    "\"message\": \"Kill switch restored\"" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            KillSwitch result = client.masfeat().restoreKillSwitch("sys-789");

            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.ENABLED);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/killswitch/sys-789/restore");
        }

        @Test
        @DisplayName("Should get kill switch history")
        void testGetKillSwitchHistory() throws Exception {
            String responseJson = "{" +
                    "\"history\": [" +
                    "{\"id\": \"event-1\", \"kill_switch_id\": \"ks-123\", \"action\": \"enabled\", \"performed_by\": \"admin\", \"performed_at\": \"2026-01-23T12:00:00Z\"}," +
                    "{\"id\": \"event-2\", \"kill_switch_id\": \"ks-123\", \"action\": \"triggered\", \"reason\": \"Bias exceeded\", \"performed_by\": \"system\", \"performed_at\": \"2026-01-23T13:00:00Z\"}" +
                    "]," +
                    "\"count\": 2" +
                    "}";
            mockWebServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            var result = client.masfeat().getKillSwitchHistory("sys-789");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEventType()).isEqualTo(KillSwitchEventType.ENABLED);
            assertThat(result.get(1).getEventType()).isEqualTo(KillSwitchEventType.TRIGGERED);

            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/masfeat/killswitch/sys-789/history");
        }
    }
}
