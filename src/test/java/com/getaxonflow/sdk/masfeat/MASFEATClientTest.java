/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.masfeat;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MAS FEAT client methods.
 */
@WireMockTest
@DisplayName("MAS FEAT Client Tests")
class MASFEATClientTest {

    private AxonFlow client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        client = AxonFlow.create(AxonFlow.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
    }

    @Nested
    @DisplayName("Registry Methods")
    class RegistryMethodsTest {

        @Test
        @DisplayName("Should register a new AI system")
        void testRegisterSystem() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/registry"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

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

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/registry")));
        }

        @Test
        @DisplayName("Should get a system by ID")
        void testGetSystem() {
            String responseJson = "{" +
                    "\"id\": \"sys-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"model-v1\"," +
                    "\"system_name\": \"Test Model\"," +
                    "\"use_case\": \"credit_scoring\"," +
                    "\"owner_team\": \"team\"," +
                    "\"customer_impact\": 3," +
                    "\"model_complexity\": 2," +
                    "\"human_reliance\": 1," +
                    "\"materiality\": \"high\"," +
                    "\"status\": \"active\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            stubFor(get(urlEqualTo("/api/v1/masfeat/registry/sys-123"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            AISystemRegistry result = client.masfeat().getSystem("sys-123");

            assertThat(result.getId()).isEqualTo("sys-123");
            assertThat(result.getStatus()).isEqualTo(SystemStatus.ACTIVE);

            verify(getRequestedFor(urlEqualTo("/api/v1/masfeat/registry/sys-123")));
        }

        @Test
        @DisplayName("Should activate a system")
        void testActivateSystem() {
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
            stubFor(put(urlEqualTo("/api/v1/masfeat/registry/sys-123"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            AISystemRegistry result = client.masfeat().activateSystem("sys-123");

            assertThat(result.getStatus()).isEqualTo(SystemStatus.ACTIVE);

            verify(putRequestedFor(urlEqualTo("/api/v1/masfeat/registry/sys-123")));
        }

        @Test
        @DisplayName("Should get registry summary")
        void testGetRegistrySummary() {
            String responseJson = "{" +
                    "\"total_systems\": 10," +
                    "\"active_systems\": 8," +
                    "\"high_materiality_count\": 2," +
                    "\"medium_materiality_count\": 5," +
                    "\"low_materiality_count\": 3" +
                    "}";
            stubFor(get(urlEqualTo("/api/v1/masfeat/registry/summary"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            RegistrySummary result = client.masfeat().getRegistrySummary();

            assertThat(result.getTotalSystems()).isEqualTo(10);
            assertThat(result.getActiveSystems()).isEqualTo(8);

            verify(getRequestedFor(urlEqualTo("/api/v1/masfeat/registry/summary")));
        }
    }

    @Nested
    @DisplayName("Assessment Methods")
    class AssessmentMethodsTest {

        @Test
        @DisplayName("Should create a new assessment")
        void testCreateAssessment() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/assessments"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            CreateAssessmentRequest request = CreateAssessmentRequest.builder()
                    .systemId("sys-789")
                    .assessmentType("annual")
                    .build();

            FEATAssessment result = client.masfeat().createAssessment(request);

            assertThat(result.getId()).isEqualTo("assess-123");
            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.PENDING);

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/assessments")));
        }

        @Test
        @DisplayName("Should get an assessment by ID")
        void testGetAssessment() {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"completed\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"overall_score\": 89," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            stubFor(get(urlEqualTo("/api/v1/masfeat/assessments/assess-123"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            FEATAssessment result = client.masfeat().getAssessment("assess-123");

            assertThat(result.getId()).isEqualTo("assess-123");
            assertThat(result.getOverallScore()).isEqualTo(89);

            verify(getRequestedFor(urlEqualTo("/api/v1/masfeat/assessments/assess-123")));
        }

        @Test
        @DisplayName("Should update an assessment")
        void testUpdateAssessment() {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"in_progress\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"fairness_score\": 85," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T12:00:00Z\"" +
                    "}";
            stubFor(put(urlEqualTo("/api/v1/masfeat/assessments/assess-123"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            UpdateAssessmentRequest request = UpdateAssessmentRequest.builder()
                    .fairnessScore(85)
                    .build();

            FEATAssessment result = client.masfeat().updateAssessment("assess-123", request);

            assertThat(result.getFairnessScore()).isEqualTo(85);

            verify(putRequestedFor(urlEqualTo("/api/v1/masfeat/assessments/assess-123")));
        }

        @Test
        @DisplayName("Should submit an assessment")
        void testSubmitAssessment() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/assessments/assess-123/submit"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            FEATAssessment result = client.masfeat().submitAssessment("assess-123");

            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.COMPLETED);

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/assessments/assess-123/submit")));
        }

        @Test
        @DisplayName("Should approve an assessment")
        void testApproveAssessment() {
            String responseJson = "{" +
                    "\"id\": \"assess-123\"," +
                    "\"org_id\": \"org-456\"," +
                    "\"system_id\": \"sys-789\"," +
                    "\"assessment_type\": \"annual\"," +
                    "\"status\": \"approved\"," +
                    "\"assessment_date\": \"2026-01-23T12:00:00Z\"," +
                    "\"approved_by\": \"admin@example.com\"," +
                    "\"approved_at\": \"2026-01-23T13:00:00Z\"," +
                    "\"created_at\": \"2026-01-23T12:00:00Z\"," +
                    "\"updated_at\": \"2026-01-23T13:00:00Z\"" +
                    "}";
            stubFor(post(urlEqualTo("/api/v1/masfeat/assessments/assess-123/approve"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            ApproveAssessmentRequest request = ApproveAssessmentRequest.builder().build();
            FEATAssessment result = client.masfeat().approveAssessment("assess-123", request);

            assertThat(result.getStatus()).isEqualTo(FEATAssessmentStatus.APPROVED);
            assertThat(result.getApprovedBy()).isEqualTo("admin@example.com");

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/assessments/assess-123/approve")));
        }
    }

    @Nested
    @DisplayName("Kill Switch Methods")
    class KillSwitchMethodsTest {

        @Test
        @DisplayName("Should get kill switch status")
        void testGetKillSwitch() {
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
            stubFor(get(urlEqualTo("/api/v1/masfeat/killswitch/sys-789"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            KillSwitch result = client.masfeat().getKillSwitch("sys-789");

            assertThat(result.getId()).isEqualTo("ks-123");
            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.ENABLED);
            assertThat(result.getAccuracyThreshold()).isEqualTo(0.95);

            verify(getRequestedFor(urlEqualTo("/api/v1/masfeat/killswitch/sys-789")));
        }

        @Test
        @DisplayName("Should configure kill switch")
        void testConfigureKillSwitch() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/configure"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            ConfigureKillSwitchRequest request = ConfigureKillSwitchRequest.builder()
                    .accuracyThreshold(0.95)
                    .autoTriggerEnabled(true)
                    .build();

            KillSwitch result = client.masfeat().configureKillSwitch("sys-789", request);

            assertThat(result.isAutoTriggerEnabled()).isTrue();

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/configure")));
        }

        @Test
        @DisplayName("Should trigger kill switch")
        void testTriggerKillSwitch() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/trigger"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            TriggerKillSwitchRequest request = TriggerKillSwitchRequest.builder()
                    .reason("Manual trigger")
                    .build();
            KillSwitch result = client.masfeat().triggerKillSwitch("sys-789", request);

            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.TRIGGERED);
            assertThat(result.getTriggeredReason()).isEqualTo("Manual trigger");

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/trigger")));
        }

        @Test
        @DisplayName("Should restore kill switch")
        void testRestoreKillSwitch() {
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
            stubFor(post(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/restore"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            RestoreKillSwitchRequest request = RestoreKillSwitchRequest.builder().build();
            KillSwitch result = client.masfeat().restoreKillSwitch("sys-789", request);

            assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.ENABLED);

            verify(postRequestedFor(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/restore")));
        }

        @Test
        @DisplayName("Should get kill switch history")
        void testGetKillSwitchHistory() {
            String responseJson = "{" +
                    "\"history\": [" +
                    "{\"id\": \"event-1\", \"kill_switch_id\": \"ks-123\", \"action\": \"enabled\", \"performed_by\": \"admin\", \"performed_at\": \"2026-01-23T12:00:00Z\"}," +
                    "{\"id\": \"event-2\", \"kill_switch_id\": \"ks-123\", \"action\": \"triggered\", \"reason\": \"Bias exceeded\", \"performed_by\": \"system\", \"performed_at\": \"2026-01-23T13:00:00Z\"}" +
                    "]," +
                    "\"count\": 2" +
                    "}";
            stubFor(get(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/history?limit=10"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            List<KillSwitchEvent> result = client.masfeat().getKillSwitchHistory("sys-789", 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEventType()).isEqualTo("enabled");
            assertThat(result.get(1).getEventType()).isEqualTo("triggered");

            verify(getRequestedFor(urlEqualTo("/api/v1/masfeat/killswitch/sys-789/history?limit=10")));
        }
    }
}
