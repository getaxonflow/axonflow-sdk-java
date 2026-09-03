// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real-wire audit model tests (getaxonflow/axonflow-enterprise#3254).
 *
 * <p>Fixture provenance:
 *
 * <ul>
 *   <li>{@code fixtures/audit-search-live.json} - REAL response, captured 2026-08-03 from an
 *       isolated community v9.13.0 stack (session 3254), via {@code POST /api/v1/audit/search}
 *       through the agent proxy. Verbatim, unmodified. Note what is on it: {@code policy_decision},
 *       {@code policy_details}, {@code response_time_ms} - and what is NOT: {@code query_summary},
 *       {@code success}, {@code blocked}, {@code risk_score}, {@code latency_ms}, {@code
 *       policy_violations}, {@code metadata}, the seven fiction fields the SDK modeled but no 9.x
 *       server ever served.
 *   <li>{@code fixtures/audit-search-old-server.json} - HAND-MODIFIED copy of the live capture with
 *       the three real-wire fields removed, simulating a pre-9.x server.
 *   <li>{@code fixtures/audit-search-both-present.json} - HAND-MODIFIED copy of the live capture
 *       with the seven fiction fields injected alongside the real ones (with non-default values,
 *       e.g. {@code success:false}, so every assertion can actually fail), proving both parse with
 *       no collision.
 *   <li>{@code fixtures/audit-search-explicit-null.json} - HAND-MODIFIED copy of the live capture
 *       with the three real-wire fields present as explicit JSON {@code null}, pinning the
 *       null-to-default normalization the canonical constructor performs.
 * </ul>
 *
 * <p>The mapper here is configured the same way {@code AxonFlow} configures its production mapper
 * (plain {@code ObjectMapper} + {@code JavaTimeModule}; unknown properties tolerated via the
 * model's {@code @JsonIgnoreProperties}). It is a separate instance, not the production object - if
 * {@code AxonFlow}'s mapper construction gains configuration, mirror it here.
 */
@DisplayName("Audit model - real wire fields (#3254)")
class AuditRealWireModelTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  private String fixture(String name) throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
      assertThat(in).as("fixture %s must exist on the test classpath", name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  @DisplayName("real captured payload - new fields populated, fiction fields stay at defaults")
  void realCapturedPayloadParses() throws Exception {
    AuditSearchResponse response =
        mapper.readValue(fixture("audit-search-live.json"), AuditSearchResponse.class);

    assertThat(response.getEntries()).hasSize(2);
    assertThat(response.getTotal()).isEqualTo(2);

    AuditLogEntry error = response.getEntries().get(0);
    assertThat(error.getPolicyDecision()).isEqualTo("error");
    assertThat(error.getPolicyDetails())
        .containsEntry("error_message", "blocked by policy sys_sqli_or_true")
        .containsEntry("tool_name", "s3254_blocked_probe");
    assertThat(error.getResponseTimeMs()).isNotNull().isEqualTo(0L);

    AuditLogEntry allowed = response.getEntries().get(1);
    assertThat(allowed.getPolicyDecision()).isEqualTo("allowed");
    assertThat(allowed.getPolicyDetails()).containsEntry("tool_name", "s3254_capture_probe");
    assertThat(allowed.getResponseTimeMs()).isNotNull().isEqualTo(0L);

    // The seven fiction fields are ABSENT on the real wire (see fixture
    // provenance above) and must sit at their documented defaults. Note
    // isSuccess() defaults TRUE even on the error-verdict row - exactly
    // why it is fiction and deprecated.
    for (AuditLogEntry e : response.getEntries()) {
      assertThat(e.getQuerySummary()).isEmpty();
      assertThat(e.isSuccess()).isTrue();
      assertThat(e.isBlocked()).isFalse();
      assertThat(e.getRiskScore()).isEqualTo(0.0);
      assertThat(e.getLatencyMs()).isEqualTo(0);
      assertThat(e.getPolicyViolations()).isEmpty();
      assertThat(e.getMetadata()).isEmpty();
    }
  }

  @Test
  @DisplayName("old-server payload (three new fields absent) - parses, new fields default")
  void oldServerPayloadTolerated() throws Exception {
    AuditSearchResponse response =
        mapper.readValue(fixture("audit-search-old-server.json"), AuditSearchResponse.class);

    assertThat(response.getEntries()).hasSize(2);
    for (AuditLogEntry e : response.getEntries()) {
      assertThat(e.getPolicyDecision()).isEmpty();
      assertThat(e.getPolicyDetails()).isEmpty();
      // Long responseTimeMs is null-safe: absent on the wire means null,
      // never a throw and never a silent 0 that fakes a measurement.
      assertThat(e.getResponseTimeMs()).isNull();
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  @DisplayName("fiction and real fields in one payload - both parse, no collision")
  void bothPresentPayloadParses() throws Exception {
    AuditSearchResponse response =
        mapper.readValue(fixture("audit-search-both-present.json"), AuditSearchResponse.class);

    AuditLogEntry e = response.getEntries().get(0);
    // Real fields, from the capture:
    assertThat(e.getPolicyDecision()).isEqualTo("error");
    assertThat(e.getPolicyDetails()).containsEntry("tool_name", "s3254_blocked_probe");
    assertThat(e.getResponseTimeMs()).isEqualTo(0L);
    // Fiction fields, hand-injected into the fixture. Every injected value
    // differs from the constructor default (success:false vs default true,
    // blocked:true vs default false, ...) so each assertion can fail.
    assertThat(e.getQuerySummary()).isEqualTo("hand-injected summary");
    assertThat(e.isSuccess()).isFalse();
    assertThat(e.isBlocked()).isTrue();
    assertThat(e.getRiskScore()).isEqualTo(0.42);
    assertThat(e.getLatencyMs()).isEqualTo(77);
    assertThat(e.getPolicyViolations()).containsExactly("sys_sqli_or_true");
    assertThat(e.getMetadata()).containsEntry("hand_injected", true);
  }

  @Test
  @DisplayName("explicit JSON null on the three new fields - normalized to defaults, no throw")
  void explicitNullPayloadNormalized() throws Exception {
    AuditSearchResponse response =
        mapper.readValue(fixture("audit-search-explicit-null.json"), AuditSearchResponse.class);

    assertThat(response.getEntries()).hasSize(2);
    for (AuditLogEntry e : response.getEntries()) {
      // Explicit null and absent must land identically: "" / empty map /
      // null Long. Pins the constructor's null guards through the real
      // mapper (Jackson passes explicit null to the creator).
      assertThat(e.getPolicyDecision()).isEmpty();
      assertThat(e.getPolicyDetails()).isEmpty();
      assertThat(e.getResponseTimeMs()).isNull();
    }
  }

  @Test
  @DisplayName("pre-#3254 constructor signature still compiles and delegates with defaults")
  void oldConstructorSignatureStillCompiles() {
    // Source-compatibility proof: this is the EXACT 19-argument constructor
    // shape that existed before #3254. If the new fields had been added to
    // the only constructor, this call would no longer compile.
    AuditLogEntry entry =
        new AuditLogEntry(
            "audit-1",
            "req-1",
            Instant.parse("2026-01-05T10:00:00Z"),
            "user@example.com",
            "client-1",
            "tenant-1",
            "llm_chat",
            "summary",
            true,
            false,
            0.1,
            "openai",
            "gpt-4",
            150,
            250,
            java.util.Collections.emptyList(),
            java.util.Collections.emptyMap(),
            null,
            null);

    assertThat(entry.getId()).isEqualTo("audit-1");
    assertThat(entry.getPolicyDecision()).isEmpty();
    assertThat(entry.getPolicyDetails()).isEmpty();
    assertThat(entry.getResponseTimeMs()).isNull();
  }

  @Test
  @DisplayName("search request - action serialized under 'action', omitted when unset")
  void searchRequestActionSerialization() throws Exception {
    String withAction =
        mapper.writeValueAsString(AuditSearchRequest.builder().action("blocked").build());
    assertThat(withAction).contains("\"action\":\"blocked\"");

    String withoutAction = mapper.writeValueAsString(AuditSearchRequest.builder().build());
    assertThat(withoutAction).doesNotContain("\"action\"");
  }

  @Test
  @SuppressWarnings("deprecation")
  @DisplayName("search request - deprecated request_type still sent on the wire (harmless)")
  void searchRequestRequestTypeStillSent() throws Exception {
    String json =
        mapper.writeValueAsString(AuditSearchRequest.builder().requestType("llm_chat").build());
    assertThat(json).contains("\"request_type\":\"llm_chat\"");
  }
}
