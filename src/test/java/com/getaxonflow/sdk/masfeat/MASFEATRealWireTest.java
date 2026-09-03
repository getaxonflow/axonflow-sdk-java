// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.masfeat;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real-wire masfeat model tests (getaxonflow/axonflow-enterprise#3254, pin-advance batch).
 *
 * <p>Fixture provenance: every payload in this class is SOURCE-DERIVED from the orchestrator's
 * masfeat structs ({@code platform/orchestrator/masfeat/types.go} at community tag v9.13.0,
 * df027c788) and the pinned {@code masfeat-api.yaml} schemas - these are NOT live captures (the
 * masfeat module is an Enterprise feature; the community stack registers no masfeat routes, see
 * {@code masfeat_community.go}). Payloads marked "both spellings" are hand-constructed
 * discriminators and say so.
 *
 * <p>These tests exercise the SDK's REAL parse path - the hand-written parsers in {@code
 * AxonFlow.MASFEATNamespace} reached through the public client methods over WireMock - not the
 * post-parse object shape (a #3254-class fiction lives in what the parser READS, so only driving
 * the parser can catch it).
 *
 * <p>Every assertion on a #3254 fix is mutation-proof by construction: the asserted value differs
 * from what the PRE-FIX parser would have produced (0 / null / the legacy key's value).
 */
@WireMockTest
@DisplayName("MAS FEAT real-wire parsing (#3254)")
class MASFEATRealWireTest {

  private AxonFlow client;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    client =
        AxonFlow.create(
            AxonFlow.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
  }

  @Test
  @DisplayName("RegistrySummary - server-shaped payload populates every real field")
  @SuppressWarnings("deprecation")
  void registrySummaryServerShape() {
    // Source-derived from masfeat.RegistrySummary (types.go:431-440): the
    // real keys carry NO _count suffix. Distinct values everywhere so a
    // wrong-key read cannot accidentally pass. The pre-#3254 parser read
    // medium/low ONLY under the _count fiction spelling (always 0 against
    // this payload) and never read org_id / assessments_due /
    // kill_switches_triggered at all.
    String responseJson =
        "{"
            + "\"org_id\": \"org-mas-1\","
            + "\"total_systems\": 11,"
            + "\"active_systems\": 7,"
            + "\"high_materiality\": 2,"
            + "\"medium_materiality\": 5,"
            + "\"low_materiality\": 4,"
            + "\"assessments_due\": 3,"
            + "\"kill_switches_triggered\": 1"
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/summary"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    RegistrySummary result = client.masfeat().getRegistrySummary();

    assertThat(result.getOrgId()).isEqualTo("org-mas-1");
    assertThat(result.getTotalSystems()).isEqualTo(11);
    assertThat(result.getActiveSystems()).isEqualTo(7);
    assertThat(result.getHighMaterialityCount()).isEqualTo(2);
    assertThat(result.getMediumMaterialityCount()).isEqualTo(5);
    assertThat(result.getLowMaterialityCount()).isEqualTo(4);
    assertThat(result.getAssessmentsDue()).isEqualTo(3);
    assertThat(result.getKillSwitchesTriggered()).isEqualTo(1);
    // The server's RegistrySummary has no by_use_case / by_status - the
    // deprecated fiction maps stay null.
    assertThat(result.getByUseCase()).isNull();
    assertThat(result.getByStatus()).isNull();
  }

  @Test
  @DisplayName("RegistrySummary - real key wins over legacy _count spelling when both present")
  void registrySummaryRealKeyWins() {
    // Hand-constructed discriminator (both spellings, different values, not
    // a capture): the real key must win even when it is 0. The pre-#3254
    // parser preferred the _count spelling for high and read ONLY the
    // _count spelling for medium/low.
    String responseJson =
        "{"
            + "\"total_systems\": 1,"
            + "\"high_materiality\": 0,"
            + "\"high_materiality_count\": 9,"
            + "\"medium_materiality\": 6,"
            + "\"medium_materiality_count\": 9,"
            + "\"low_materiality\": 5,"
            + "\"low_materiality_count\": 9"
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/summary"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    RegistrySummary result = client.masfeat().getRegistrySummary();

    assertThat(result.getHighMaterialityCount()).isEqualTo(0);
    assertThat(result.getMediumMaterialityCount()).isEqualTo(6);
    assertThat(result.getLowMaterialityCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("RegistrySummary - legacy _count-only payload still parses (fallback tolerance)")
  void registrySummaryLegacyFallback() {
    // Hand-constructed legacy shape (fiction spellings only, not a capture):
    // the fallback keeps tolerating it.
    String responseJson =
        "{"
            + "\"total_systems\": 4,"
            + "\"high_materiality_count\": 1,"
            + "\"medium_materiality_count\": 2,"
            + "\"low_materiality_count\": 1"
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/summary"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    RegistrySummary result = client.masfeat().getRegistrySummary();

    assertThat(result.getHighMaterialityCount()).isEqualTo(1);
    assertThat(result.getMediumMaterialityCount()).isEqualTo(2);
    assertThat(result.getLowMaterialityCount()).isEqualTo(1);
    // Fields absent from a legacy payload default cleanly.
    assertThat(result.getOrgId()).isNull();
    assertThat(result.getAssessmentsDue()).isEqualTo(0);
    assertThat(result.getKillSwitchesTriggered()).isEqualTo(0);
  }

  @Test
  @DisplayName("KillSwitch - trigger_reason (real key) wins over triggered_reason")
  void killSwitchTriggerReasonRealKeyWins() {
    // Source-derived from masfeat.KillSwitch (types.go:283-303) plus a
    // hand-injected legacy spelling as a discriminator: the server serves
    // trigger_reason; triggered_reason has never been sent. The pre-#3254
    // parser preferred the legacy spelling, so it would return
    // "legacy-fiction" here.
    String responseJson =
        "{"
            + "\"id\": \"ks-1\","
            + "\"org_id\": \"org-mas-1\","
            + "\"system_id\": \"credit-model-v1\","
            + "\"status\": \"triggered\","
            + "\"trigger_reason\": \"accuracy breach\","
            + "\"triggered_reason\": \"legacy-fiction\","
            + "\"auto_trigger_enabled\": true,"
            + "\"accuracy_threshold\": 0.95,"
            + "\"triggered_at\": \"2026-08-01T10:00:00Z\","
            + "\"triggered_by\": \"ops@bank.sg\","
            + "\"created_at\": \"2026-07-01T10:00:00Z\","
            + "\"updated_at\": \"2026-08-01T10:00:00Z\""
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/killswitch/credit-model-v1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    KillSwitch result = client.masfeat().getKillSwitch("credit-model-v1");

    assertThat(result.getTriggeredReason()).isEqualTo("accuracy breach");
    assertThat(result.getStatus()).isEqualTo(KillSwitchStatus.TRIGGERED);
    assertThat(result.isAutoTriggerEnabled()).isTrue();
    assertThat(result.getAccuracyThreshold()).isEqualTo(0.95);
    assertThat(result.getTriggeredBy()).isEqualTo("ops@bank.sg");
  }

  @Test
  @DisplayName("AISystemRegistry - owner_email lands in ownerEmail AND the businessOwner alias")
  void aiSystemOwnerEmail() {
    // Source-derived from masfeat.AISystemRegistry (types.go:172-198): the
    // wire serves owner_email + owner_team; technical_owner has never been
    // sent. materiality_classification carries a both-spellings
    // discriminator (hand-injected "materiality" decoy): the real key must
    // win - the pre-#3254 parser read "materiality" first, so it would
    // report LOW here.
    String responseJson =
        "{"
            + "\"id\": \"sys-9\","
            + "\"org_id\": \"org-mas-1\","
            + "\"system_id\": \"fraud-model-v2\","
            + "\"system_name\": \"Fraud Detection v2\","
            + "\"use_case\": \"fraud_detection\","
            + "\"status\": \"active\","
            + "\"owner_team\": \"risk-analytics\","
            + "\"owner_email\": \"owner@bank.sg\","
            + "\"risk_rating_impact\": 4,"
            + "\"risk_rating_complexity\": 3,"
            + "\"risk_rating_reliance\": 5,"
            + "\"materiality_classification\": \"high\","
            + "\"materiality\": \"low\","
            + "\"created_at\": \"2026-05-01T00:00:00Z\","
            + "\"updated_at\": \"2026-06-01T00:00:00Z\","
            + "\"created_by\": \"admin@bank.sg\""
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/fraud-model-v2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    AISystemRegistry result = client.masfeat().getSystem("fraud-model-v2");

    assertThat(result.getOwnerEmail()).isEqualTo("owner@bank.sg");
    assertThat(result.getBusinessOwner()).isEqualTo("owner@bank.sg");
    assertThat(result.getOwnerTeam()).isEqualTo("risk-analytics");
    // technical_owner is never served: the deprecated accessor stays null.
    @SuppressWarnings("deprecation")
    String technicalOwner = result.getTechnicalOwner();
    assertThat(technicalOwner).isNull();
    // Real risk_rating_* keys land in the historic java names.
    assertThat(result.getCustomerImpact()).isEqualTo(4);
    assertThat(result.getModelComplexity()).isEqualTo(3);
    assertThat(result.getHumanReliance()).isEqualTo(5);
    // materiality_classification (real) wins over the "materiality" decoy.
    assertThat(result.getMaterialityClassification()).isEqualTo(MaterialityClassification.HIGH);
  }

  @Test
  @DisplayName("Minimal payloads - new fields absent, everything defaults, no throw")
  void absenceTolerance() {
    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/summary"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"total_systems\": 0}")));

    RegistrySummary summary = client.masfeat().getRegistrySummary();
    assertThat(summary.getOrgId()).isNull();
    assertThat(summary.getAssessmentsDue()).isEqualTo(0);
    assertThat(summary.getKillSwitchesTriggered()).isEqualTo(0);

    stubFor(
        get(urlEqualTo("/api/v1/masfeat/registry/minimal-sys"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": \"sys-min\", \"system_id\": \"minimal-sys\"}")));

    AISystemRegistry system = client.masfeat().getSystem("minimal-sys");
    assertThat(system.getOwnerEmail()).isNull();
    assertThat(system.getBusinessOwner()).isNull();
  }
}
