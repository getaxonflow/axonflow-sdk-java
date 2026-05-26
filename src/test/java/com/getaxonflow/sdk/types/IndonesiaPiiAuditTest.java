/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.types;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.getaxonflow.sdk.types.policies.PolicyTypes.PolicyCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Indonesia PII + AuditLogEntry cross-border fields")
class IndonesiaPiiAuditTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Nested
  @DisplayName("PolicyCategory.PII_INDONESIA")
  class PiiIndonesiaCategory {

    @Test
    @DisplayName("PII_INDONESIA value should be 'pii-indonesia'")
    void piiIndonesiaValueShouldBePiiIndonesia() {
      assertThat(PolicyCategory.PII_INDONESIA.getValue()).isEqualTo("pii-indonesia");
    }
  }

  @Nested
  @DisplayName("AuditLogEntry cross-border fields")
  class AuditLogEntryCrossBorderFields {

    @Test
    @DisplayName("should deserialize with data_residency and transfer_basis")
    void shouldDeserializeWithCrossBorderFields() throws Exception {
      String json =
          "{"
              + "\"id\": \"audit-indo-1\","
              + "\"request_id\": \"req-1\","
              + "\"timestamp\": \"2026-05-26T10:00:00Z\","
              + "\"user_email\": \"user@example.com\","
              + "\"client_id\": \"client-1\","
              + "\"tenant_id\": \"tenant-1\","
              + "\"request_type\": \"llm_chat\","
              + "\"query_summary\": \"Test query\","
              + "\"success\": true,"
              + "\"blocked\": false,"
              + "\"risk_score\": 0.1,"
              + "\"provider\": \"openai\","
              + "\"model\": \"gpt-4\","
              + "\"tokens_used\": 150,"
              + "\"latency_ms\": 250,"
              + "\"policy_violations\": [],"
              + "\"metadata\": {},"
              + "\"data_residency\": \"ID\","
              + "\"transfer_basis\": \"consent\""
              + "}";

      AuditLogEntry entry = MAPPER.readValue(json, AuditLogEntry.class);

      assertThat(entry.getId()).isEqualTo("audit-indo-1");
      assertThat(entry.getDataResidency()).isEqualTo("ID");
      assertThat(entry.getTransferBasis()).isEqualTo("consent");
    }

    @Test
    @DisplayName("should deserialize without cross-border fields (backward compat)")
    void shouldDeserializeWithoutCrossBorderFields() throws Exception {
      String json =
          "{"
              + "\"id\": \"audit-old-1\","
              + "\"request_id\": \"req-2\","
              + "\"timestamp\": \"2026-05-26T10:00:00Z\","
              + "\"user_email\": \"user@example.com\","
              + "\"client_id\": \"client-1\","
              + "\"tenant_id\": \"tenant-1\","
              + "\"request_type\": \"llm_chat\","
              + "\"query_summary\": \"Old platform query\","
              + "\"success\": true,"
              + "\"blocked\": false,"
              + "\"risk_score\": 0.2,"
              + "\"provider\": \"openai\","
              + "\"model\": \"gpt-4\","
              + "\"tokens_used\": 100,"
              + "\"latency_ms\": 200,"
              + "\"policy_violations\": [],"
              + "\"metadata\": {}"
              + "}";

      AuditLogEntry entry = MAPPER.readValue(json, AuditLogEntry.class);

      assertThat(entry.getId()).isEqualTo("audit-old-1");
      assertThat(entry.getDataResidency()).isNull();
      assertThat(entry.getTransferBasis()).isNull();
    }

    @Test
    @DisplayName("equals and hashCode should include cross-border fields")
    void equalsAndHashCodeShouldIncludeCrossBorderFields() throws Exception {
      String jsonWithFields =
          "{"
              + "\"id\": \"audit-1\","
              + "\"data_residency\": \"ID\","
              + "\"transfer_basis\": \"adequacy\""
              + "}";
      String jsonWithoutFields = "{\"id\": \"audit-1\"}";

      AuditLogEntry with = MAPPER.readValue(jsonWithFields, AuditLogEntry.class);
      AuditLogEntry without = MAPPER.readValue(jsonWithoutFields, AuditLogEntry.class);

      assertThat(with).isNotEqualTo(without);
      assertThat(with.hashCode()).isNotEqualTo(without.hashCode());
    }

    @Test
    @DisplayName("toString should include cross-border fields")
    void toStringShouldIncludeCrossBorderFields() throws Exception {
      String json =
          "{"
              + "\"id\": \"audit-1\","
              + "\"data_residency\": \"SG\","
              + "\"transfer_basis\": \"safeguards\""
              + "}";

      AuditLogEntry entry = MAPPER.readValue(json, AuditLogEntry.class);
      String str = entry.toString();

      assertThat(str).contains("dataResidency='SG'");
      assertThat(str).contains("transferBasis='safeguards'");
    }
  }
}
