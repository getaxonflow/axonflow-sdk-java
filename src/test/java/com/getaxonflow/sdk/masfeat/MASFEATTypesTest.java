/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.masfeat;

import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for MAS FEAT compliance types. */
@DisplayName("MAS FEAT Types Tests")
class MASFEATTypesTest {

  // =========================================================================
  // Enum Tests
  // =========================================================================

  @Nested
  @DisplayName("MaterialityClassification Enum Tests")
  class MaterialityClassificationTests {

    @Test
    @DisplayName("Should return correct values for all classifications")
    void testEnumValues() {
      assertThat(MaterialityClassification.HIGH.getValue()).isEqualTo("high");
      assertThat(MaterialityClassification.MEDIUM.getValue()).isEqualTo("medium");
      assertThat(MaterialityClassification.LOW.getValue()).isEqualTo("low");
    }

    @Test
    @DisplayName("Should convert from string value")
    void testFromValue() {
      assertThat(MaterialityClassification.fromValue("high"))
          .isEqualTo(MaterialityClassification.HIGH);
      assertThat(MaterialityClassification.fromValue("medium"))
          .isEqualTo(MaterialityClassification.MEDIUM);
      assertThat(MaterialityClassification.fromValue("low"))
          .isEqualTo(MaterialityClassification.LOW);
    }

    @Test
    @DisplayName("Should throw for unknown value")
    void testFromValueUnknown() {
      assertThatThrownBy(() -> MaterialityClassification.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown materiality");
    }
  }

  @Nested
  @DisplayName("SystemStatus Enum Tests")
  class SystemStatusTests {

    @Test
    @DisplayName("Should return correct values for all statuses")
    void testEnumValues() {
      assertThat(SystemStatus.DRAFT.getValue()).isEqualTo("draft");
      assertThat(SystemStatus.ACTIVE.getValue()).isEqualTo("active");
      assertThat(SystemStatus.SUSPENDED.getValue()).isEqualTo("suspended");
      assertThat(SystemStatus.RETIRED.getValue()).isEqualTo("retired");
    }

    @Test
    @DisplayName("Should convert from string value")
    void testFromValue() {
      assertThat(SystemStatus.fromValue("draft")).isEqualTo(SystemStatus.DRAFT);
      assertThat(SystemStatus.fromValue("active")).isEqualTo(SystemStatus.ACTIVE);
      assertThat(SystemStatus.fromValue("suspended")).isEqualTo(SystemStatus.SUSPENDED);
      assertThat(SystemStatus.fromValue("retired")).isEqualTo(SystemStatus.RETIRED);
    }

    @Test
    @DisplayName("Should throw for unknown value")
    void testFromValueUnknown() {
      assertThatThrownBy(() -> SystemStatus.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown status");
    }
  }

  @Nested
  @DisplayName("FEATAssessmentStatus Enum Tests")
  class FEATAssessmentStatusTests {

    @Test
    @DisplayName("Should return correct values for all statuses")
    void testEnumValues() {
      assertThat(FEATAssessmentStatus.PENDING.getValue()).isEqualTo("pending");
      assertThat(FEATAssessmentStatus.IN_PROGRESS.getValue()).isEqualTo("in_progress");
      assertThat(FEATAssessmentStatus.COMPLETED.getValue()).isEqualTo("completed");
      assertThat(FEATAssessmentStatus.APPROVED.getValue()).isEqualTo("approved");
      assertThat(FEATAssessmentStatus.REJECTED.getValue()).isEqualTo("rejected");
    }

    @Test
    @DisplayName("Should convert from string value")
    void testFromValue() {
      assertThat(FEATAssessmentStatus.fromValue("pending")).isEqualTo(FEATAssessmentStatus.PENDING);
      assertThat(FEATAssessmentStatus.fromValue("in_progress"))
          .isEqualTo(FEATAssessmentStatus.IN_PROGRESS);
      assertThat(FEATAssessmentStatus.fromValue("completed"))
          .isEqualTo(FEATAssessmentStatus.COMPLETED);
      assertThat(FEATAssessmentStatus.fromValue("approved"))
          .isEqualTo(FEATAssessmentStatus.APPROVED);
      assertThat(FEATAssessmentStatus.fromValue("rejected"))
          .isEqualTo(FEATAssessmentStatus.REJECTED);
    }

    @Test
    @DisplayName("Should throw for unknown value")
    void testFromValueUnknown() {
      assertThatThrownBy(() -> FEATAssessmentStatus.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown assessment status");
    }
  }

  @Nested
  @DisplayName("KillSwitchStatus Enum Tests")
  class KillSwitchStatusTests {

    @Test
    @DisplayName("Should return correct values for all statuses")
    void testEnumValues() {
      assertThat(KillSwitchStatus.ENABLED.getValue()).isEqualTo("enabled");
      assertThat(KillSwitchStatus.DISABLED.getValue()).isEqualTo("disabled");
      assertThat(KillSwitchStatus.TRIGGERED.getValue()).isEqualTo("triggered");
    }

    @Test
    @DisplayName("Should convert from string value")
    void testFromValue() {
      assertThat(KillSwitchStatus.fromValue("enabled")).isEqualTo(KillSwitchStatus.ENABLED);
      assertThat(KillSwitchStatus.fromValue("disabled")).isEqualTo(KillSwitchStatus.DISABLED);
      assertThat(KillSwitchStatus.fromValue("triggered")).isEqualTo(KillSwitchStatus.TRIGGERED);
    }

    @Test
    @DisplayName("Should throw for unknown value")
    void testFromValueUnknown() {
      assertThatThrownBy(() -> KillSwitchStatus.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown kill switch status");
    }
  }

  @Nested
  @DisplayName("AISystemUseCase Enum Tests")
  class AISystemUseCaseTests {

    @Test
    @DisplayName("Should return correct values for all use cases")
    void testEnumValues() {
      assertThat(AISystemUseCase.CREDIT_SCORING.getValue()).isEqualTo("credit_scoring");
      assertThat(AISystemUseCase.ROBO_ADVISORY.getValue()).isEqualTo("robo_advisory");
      assertThat(AISystemUseCase.INSURANCE_UNDERWRITING.getValue())
          .isEqualTo("insurance_underwriting");
      assertThat(AISystemUseCase.TRADING_ALGORITHM.getValue()).isEqualTo("trading_algorithm");
      assertThat(AISystemUseCase.AML_CFT.getValue()).isEqualTo("aml_cft");
      assertThat(AISystemUseCase.CUSTOMER_SERVICE.getValue()).isEqualTo("customer_service");
      assertThat(AISystemUseCase.FRAUD_DETECTION.getValue()).isEqualTo("fraud_detection");
      assertThat(AISystemUseCase.OTHER.getValue()).isEqualTo("other");
    }

    @Test
    @DisplayName("Should convert from string value")
    void testFromValue() {
      assertThat(AISystemUseCase.fromValue("credit_scoring"))
          .isEqualTo(AISystemUseCase.CREDIT_SCORING);
      assertThat(AISystemUseCase.fromValue("robo_advisory"))
          .isEqualTo(AISystemUseCase.ROBO_ADVISORY);
      assertThat(AISystemUseCase.fromValue("fraud_detection"))
          .isEqualTo(AISystemUseCase.FRAUD_DETECTION);
      assertThat(AISystemUseCase.fromValue("other")).isEqualTo(AISystemUseCase.OTHER);
    }

    @Test
    @DisplayName("Should throw for unknown value")
    void testFromValueUnknown() {
      assertThatThrownBy(() -> AISystemUseCase.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown use case");
    }
  }

  // =========================================================================
  // Request Builder Tests
  // =========================================================================

  @Nested
  @DisplayName("RegisterSystemRequest Builder Tests")
  class RegisterSystemRequestTests {

    @Test
    @DisplayName("Should build with required fields")
    void testBuilderWithRequiredFields() {
      RegisterSystemRequest request =
          RegisterSystemRequest.builder()
              .systemId("credit-model-v1")
              .systemName("Credit Scoring Model")
              .useCase(AISystemUseCase.CREDIT_SCORING)
              .ownerTeam("data-science")
              .customerImpact(3)
              .modelComplexity(2)
              .humanReliance(1)
              .build();

      assertThat(request.getSystemId()).isEqualTo("credit-model-v1");
      assertThat(request.getSystemName()).isEqualTo("Credit Scoring Model");
      assertThat(request.getUseCase()).isEqualTo(AISystemUseCase.CREDIT_SCORING);
      assertThat(request.getOwnerTeam()).isEqualTo("data-science");
      assertThat(request.getCustomerImpact()).isEqualTo(3);
      assertThat(request.getModelComplexity()).isEqualTo(2);
      assertThat(request.getHumanReliance()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should build with optional fields")
    void testBuilderWithOptionalFields() {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("version", "1.0");

      RegisterSystemRequest request =
          RegisterSystemRequest.builder()
              .systemId("credit-model-v1")
              .systemName("Credit Scoring Model")
              .useCase(AISystemUseCase.CREDIT_SCORING)
              .ownerTeam("data-science")
              .customerImpact(3)
              .modelComplexity(2)
              .humanReliance(1)
              .description("AI model for credit scoring")
              .technicalOwner("tech@example.com")
              .businessOwner("business@example.com")
              .metadata(metadata)
              .build();

      assertThat(request.getDescription()).isEqualTo("AI model for credit scoring");
      assertThat(request.getTechnicalOwner()).isEqualTo("tech@example.com");
      assertThat(request.getBusinessOwner()).isEqualTo("business@example.com");
      assertThat(request.getMetadata()).containsEntry("version", "1.0");
    }
  }

  @Nested
  @DisplayName("CreateAssessmentRequest Builder Tests")
  class CreateAssessmentRequestTests {

    @Test
    @DisplayName("Should build with required fields")
    void testBuilderWithRequiredFields() {
      CreateAssessmentRequest request =
          CreateAssessmentRequest.builder()
              .systemId("credit-model-v1")
              .assessmentType("annual")
              .build();

      assertThat(request.getSystemId()).isEqualTo("credit-model-v1");
      assertThat(request.getAssessmentType()).isEqualTo("annual");
    }

    @Test
    @DisplayName("Should build with optional fields")
    void testBuilderWithOptionalFields() {
      CreateAssessmentRequest request =
          CreateAssessmentRequest.builder()
              .systemId("credit-model-v1")
              .assessmentType("annual")
              .assessors(List.of("assessor1", "assessor2"))
              .build();

      assertThat(request.getAssessors()).containsExactly("assessor1", "assessor2");
    }
  }

  @Nested
  @DisplayName("ConfigureKillSwitchRequest Builder Tests")
  class ConfigureKillSwitchRequestTests {

    @Test
    @DisplayName("Should build with all thresholds")
    void testBuilderWithAllThresholds() {
      ConfigureKillSwitchRequest request =
          ConfigureKillSwitchRequest.builder()
              .accuracyThreshold(0.95)
              .biasThreshold(0.10)
              .errorRateThreshold(0.05)
              .autoTriggerEnabled(true)
              .build();

      assertThat(request.getAccuracyThreshold()).isEqualTo(0.95);
      assertThat(request.getBiasThreshold()).isEqualTo(0.10);
      assertThat(request.getErrorRateThreshold()).isEqualTo(0.05);
      assertThat(request.getAutoTriggerEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should have null autoTriggerEnabled when not set")
    void testAutoTriggerDefault() {
      ConfigureKillSwitchRequest request =
          ConfigureKillSwitchRequest.builder().accuracyThreshold(0.95).build();

      assertThat(request.getAutoTriggerEnabled()).isNull();
    }
  }

  // =========================================================================
  // Response Type Tests (using setters)
  // =========================================================================

  @Nested
  @DisplayName("AISystemRegistry Tests")
  class AISystemRegistryTests {

    @Test
    @DisplayName("Should set and get all fields")
    void testSettersAndGetters() {
      Instant now = Instant.now();
      Map<String, Object> metadata = Map.of("version", "1.0");

      AISystemRegistry registry = new AISystemRegistry();
      registry.setId("sys-123");
      registry.setOrgId("org-456");
      registry.setSystemId("credit-model-v1");
      registry.setSystemName("Credit Scoring Model");
      registry.setDescription("AI model for credit scoring");
      registry.setUseCase(AISystemUseCase.CREDIT_SCORING);
      registry.setOwnerTeam("data-science");
      registry.setTechnicalOwner("tech@example.com");
      registry.setBusinessOwner("business@example.com");
      registry.setCustomerImpact(3);
      registry.setModelComplexity(2);
      registry.setHumanReliance(1);
      registry.setMateriality(MaterialityClassification.HIGH);
      registry.setStatus(SystemStatus.ACTIVE);
      registry.setMetadata(metadata);
      registry.setCreatedAt(now);
      registry.setUpdatedAt(now);
      registry.setCreatedBy("admin");

      assertThat(registry.getId()).isEqualTo("sys-123");
      assertThat(registry.getOrgId()).isEqualTo("org-456");
      assertThat(registry.getSystemId()).isEqualTo("credit-model-v1");
      assertThat(registry.getSystemName()).isEqualTo("Credit Scoring Model");
      assertThat(registry.getDescription()).isEqualTo("AI model for credit scoring");
      assertThat(registry.getUseCase()).isEqualTo(AISystemUseCase.CREDIT_SCORING);
      assertThat(registry.getOwnerTeam()).isEqualTo("data-science");
      assertThat(registry.getTechnicalOwner()).isEqualTo("tech@example.com");
      assertThat(registry.getBusinessOwner()).isEqualTo("business@example.com");
      assertThat(registry.getCustomerImpact()).isEqualTo(3);
      assertThat(registry.getModelComplexity()).isEqualTo(2);
      assertThat(registry.getHumanReliance()).isEqualTo(1);
      assertThat(registry.getMaterialityClassification()).isEqualTo(MaterialityClassification.HIGH);
      assertThat(registry.getStatus()).isEqualTo(SystemStatus.ACTIVE);
      assertThat(registry.getMetadata()).containsEntry("version", "1.0");
      assertThat(registry.getCreatedAt()).isEqualTo(now);
      assertThat(registry.getUpdatedAt()).isEqualTo(now);
      assertThat(registry.getCreatedBy()).isEqualTo("admin");
    }
  }

  @Nested
  @DisplayName("RegistrySummary Tests")
  class RegistrySummaryTests {

    @Test
    @DisplayName("Should set and get all fields")
    void testSettersAndGetters() {
      Map<String, Integer> byUseCase =
          Map.of(
              "credit_scoring", 4,
              "fraud_detection", 6);
      Map<String, Integer> byStatus =
          Map.of(
              "active", 8,
              "draft", 2);

      RegistrySummary summary = new RegistrySummary();
      summary.setTotalSystems(10);
      summary.setActiveSystems(8);
      summary.setHighMaterialityCount(2);
      summary.setMediumMaterialityCount(5);
      summary.setLowMaterialityCount(3);
      summary.setByUseCase(byUseCase);
      summary.setByStatus(byStatus);

      assertThat(summary.getTotalSystems()).isEqualTo(10);
      assertThat(summary.getActiveSystems()).isEqualTo(8);
      assertThat(summary.getHighMaterialityCount()).isEqualTo(2);
      assertThat(summary.getMediumMaterialityCount()).isEqualTo(5);
      assertThat(summary.getLowMaterialityCount()).isEqualTo(3);
      assertThat(summary.getByUseCase()).containsEntry("credit_scoring", 4);
      assertThat(summary.getByStatus()).containsEntry("active", 8);
    }
  }

  @Nested
  @DisplayName("FEATAssessment Tests")
  class FEATAssessmentTests {

    @Test
    @DisplayName("Should set and get all fields")
    void testSettersAndGetters() {
      Instant now = Instant.now();

      FEATAssessment assessment = new FEATAssessment();
      assessment.setId("assess-123");
      assessment.setOrgId("org-456");
      assessment.setSystemId("sys-789");
      assessment.setAssessmentType("annual");
      assessment.setStatus(FEATAssessmentStatus.COMPLETED);
      assessment.setAssessmentDate(now);
      assessment.setValidUntil(now.plusSeconds(86400 * 365));
      assessment.setFairnessScore(85);
      assessment.setEthicsScore(90);
      assessment.setAccountabilityScore(88);
      assessment.setTransparencyScore(92);
      assessment.setOverallScore(89);
      Finding finding =
          Finding.builder()
              .id("f-1")
              .pillar(FEATPillar.FAIRNESS)
              .severity(FindingSeverity.MINOR)
              .category("test-category")
              .description("Finding 1")
              .status(FindingStatus.OPEN)
              .build();
      assessment.setFindings(List.of(finding));
      assessment.setRecommendations(List.of("Recommendation 1"));
      assessment.setAssessors(List.of("assessor1"));
      assessment.setApprovedBy("approver@example.com");
      assessment.setApprovedAt(now);
      assessment.setCreatedAt(now);
      assessment.setUpdatedAt(now);
      assessment.setCreatedBy("admin");

      assertThat(assessment.getId()).isEqualTo("assess-123");
      assertThat(assessment.getOrgId()).isEqualTo("org-456");
      assertThat(assessment.getSystemId()).isEqualTo("sys-789");
      assertThat(assessment.getAssessmentType()).isEqualTo("annual");
      assertThat(assessment.getStatus()).isEqualTo(FEATAssessmentStatus.COMPLETED);
      assertThat(assessment.getFairnessScore()).isEqualTo(85);
      assertThat(assessment.getEthicsScore()).isEqualTo(90);
      assertThat(assessment.getAccountabilityScore()).isEqualTo(88);
      assertThat(assessment.getTransparencyScore()).isEqualTo(92);
      assertThat(assessment.getOverallScore()).isEqualTo(89);
      assertThat(assessment.getFindings()).hasSize(1);
      assertThat(assessment.getFindings().get(0).getDescription()).isEqualTo("Finding 1");
      assertThat(assessment.getRecommendations()).containsExactly("Recommendation 1");
      assertThat(assessment.getAssessors()).containsExactly("assessor1");
      assertThat(assessment.getApprovedBy()).isEqualTo("approver@example.com");
    }
  }

  @Nested
  @DisplayName("KillSwitch Tests")
  class KillSwitchTests {

    @Test
    @DisplayName("Should set and get all fields")
    void testSettersAndGetters() {
      Instant now = Instant.now();

      KillSwitch ks = new KillSwitch();
      ks.setId("ks-123");
      ks.setOrgId("org-456");
      ks.setSystemId("sys-789");
      ks.setStatus(KillSwitchStatus.ENABLED);
      ks.setAccuracyThreshold(0.95);
      ks.setBiasThreshold(0.10);
      ks.setErrorRateThreshold(0.05);
      ks.setAutoTriggerEnabled(true);
      ks.setCreatedAt(now);
      ks.setUpdatedAt(now);

      assertThat(ks.getId()).isEqualTo("ks-123");
      assertThat(ks.getOrgId()).isEqualTo("org-456");
      assertThat(ks.getSystemId()).isEqualTo("sys-789");
      assertThat(ks.getStatus()).isEqualTo(KillSwitchStatus.ENABLED);
      assertThat(ks.getAccuracyThreshold()).isEqualTo(0.95);
      assertThat(ks.getBiasThreshold()).isEqualTo(0.10);
      assertThat(ks.getErrorRateThreshold()).isEqualTo(0.05);
      assertThat(ks.isAutoTriggerEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should handle triggered state")
    void testTriggeredState() {
      Instant now = Instant.now();

      KillSwitch ks = new KillSwitch();
      ks.setId("ks-123");
      ks.setOrgId("org-456");
      ks.setSystemId("sys-789");
      ks.setStatus(KillSwitchStatus.TRIGGERED);
      ks.setTriggeredAt(now);
      ks.setTriggeredBy("admin");
      ks.setTriggeredReason("Bias threshold exceeded");

      assertThat(ks.getStatus()).isEqualTo(KillSwitchStatus.TRIGGERED);
      assertThat(ks.getTriggeredAt()).isEqualTo(now);
      assertThat(ks.getTriggeredBy()).isEqualTo("admin");
      assertThat(ks.getTriggeredReason()).isEqualTo("Bias threshold exceeded");
    }
  }
}
