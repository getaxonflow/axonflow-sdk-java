// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.types.policies.PolicyTypes;
import com.getaxonflow.sdk.types.policies.PolicyTypes.PolicyCategory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Media Governance Types")
class MediaGovernanceTypesTest {

  private final ObjectMapper mapper = new ObjectMapper();

  // ========================================================================
  // MediaGovernanceConfig
  // ========================================================================

  @Nested
  @DisplayName("MediaGovernanceConfig")
  class MediaGovernanceConfigTests {

    @Test
    @DisplayName("should create with default constructor and set all fields")
    void shouldCreateWithDefaultConstructor() {
      MediaGovernanceConfig config = new MediaGovernanceConfig();
      config.setTenantId("tenant_001");
      config.setEnabled(true);
      config.setAllowedAnalyzers(Arrays.asList("nsfw", "biometric", "ocr"));
      config.setUpdatedAt("2026-02-18T10:00:00Z");
      config.setUpdatedBy("admin@example.com");

      assertThat(config.getTenantId()).isEqualTo("tenant_001");
      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getAllowedAnalyzers()).containsExactly("nsfw", "biometric", "ocr");
      assertThat(config.getUpdatedAt()).isEqualTo("2026-02-18T10:00:00Z");
      assertThat(config.getUpdatedBy()).isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("should handle disabled state")
    void shouldHandleDisabledState() {
      MediaGovernanceConfig config = new MediaGovernanceConfig();
      config.setEnabled(false);
      config.setAllowedAnalyzers(List.of());

      assertThat(config.isEnabled()).isFalse();
      assertThat(config.getAllowedAnalyzers()).isEmpty();
    }

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{"
              + "\"tenant_id\": \"tenant_abc\","
              + "\"enabled\": true,"
              + "\"allowed_analyzers\": [\"nsfw\", \"document\"],"
              + "\"updated_at\": \"2026-02-18T12:00:00Z\","
              + "\"updated_by\": \"user@example.com\""
              + "}";

      MediaGovernanceConfig config = mapper.readValue(json, MediaGovernanceConfig.class);

      assertThat(config.getTenantId()).isEqualTo("tenant_abc");
      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getAllowedAnalyzers()).containsExactly("nsfw", "document");
      assertThat(config.getUpdatedAt()).isEqualTo("2026-02-18T12:00:00Z");
      assertThat(config.getUpdatedBy()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should ignore unknown properties during deserialization")
    void shouldIgnoreUnknownProperties() throws Exception {
      String json = "{\"tenant_id\": \"t1\", \"enabled\": false, \"future_field\": 42}";

      MediaGovernanceConfig config = mapper.readValue(json, MediaGovernanceConfig.class);

      assertThat(config.getTenantId()).isEqualTo("t1");
      assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should serialize to JSON")
    void shouldSerializeToJson() throws Exception {
      MediaGovernanceConfig config = new MediaGovernanceConfig();
      config.setTenantId("tenant_xyz");
      config.setEnabled(true);
      config.setAllowedAnalyzers(List.of("nsfw"));

      String json = mapper.writeValueAsString(config);

      assertThat(json).contains("\"tenant_id\":\"tenant_xyz\"");
      assertThat(json).contains("\"enabled\":true");
      assertThat(json).contains("\"allowed_analyzers\":[\"nsfw\"]");
    }

    @Test
    @DisplayName("equals should be reflexive")
    void equalsShouldBeReflexive() {
      MediaGovernanceConfig config = new MediaGovernanceConfig();
      config.setTenantId("t1");
      config.setEnabled(true);

      assertThat(config).isEqualTo(config);
    }

    @Test
    @DisplayName("equals should compare all fields")
    void equalsShouldCompareAllFields() {
      MediaGovernanceConfig config1 = new MediaGovernanceConfig();
      config1.setTenantId("t1");
      config1.setEnabled(true);
      config1.setAllowedAnalyzers(List.of("nsfw"));
      config1.setUpdatedAt("2026-02-18T10:00:00Z");
      config1.setUpdatedBy("admin");

      MediaGovernanceConfig config2 = new MediaGovernanceConfig();
      config2.setTenantId("t1");
      config2.setEnabled(true);
      config2.setAllowedAnalyzers(List.of("nsfw"));
      config2.setUpdatedAt("2026-02-18T10:00:00Z");
      config2.setUpdatedBy("admin");

      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    @DisplayName("equals should detect differences")
    void equalsShouldDetectDifferences() {
      MediaGovernanceConfig config1 = new MediaGovernanceConfig();
      config1.setTenantId("t1");
      config1.setEnabled(true);

      MediaGovernanceConfig config2 = new MediaGovernanceConfig();
      config2.setTenantId("t2");
      config2.setEnabled(true);

      MediaGovernanceConfig config3 = new MediaGovernanceConfig();
      config3.setTenantId("t1");
      config3.setEnabled(false);

      assertThat(config1).isNotEqualTo(config2);
      assertThat(config1).isNotEqualTo(config3);
      assertThat(config1).isNotEqualTo(null);
      assertThat(config1).isNotEqualTo("string");
    }

    @Test
    @DisplayName("toString should include all fields")
    void toStringShouldIncludeAllFields() {
      MediaGovernanceConfig config = new MediaGovernanceConfig();
      config.setTenantId("t1");
      config.setEnabled(true);
      config.setAllowedAnalyzers(List.of("nsfw", "biometric"));
      config.setUpdatedAt("2026-02-18T10:00:00Z");
      config.setUpdatedBy("admin");

      String str = config.toString();

      assertThat(str).contains("t1");
      assertThat(str).contains("true");
      assertThat(str).contains("nsfw");
      assertThat(str).contains("biometric");
      assertThat(str).contains("2026-02-18T10:00:00Z");
      assertThat(str).contains("admin");
    }
  }

  // ========================================================================
  // MediaGovernanceStatus
  // ========================================================================

  @Nested
  @DisplayName("MediaGovernanceStatus")
  class MediaGovernanceStatusTests {

    @Test
    @DisplayName("should create with default constructor and set all fields")
    void shouldCreateWithDefaultConstructor() {
      MediaGovernanceStatus status = new MediaGovernanceStatus();
      status.setAvailable(true);
      status.setEnabledByDefault(false);
      status.setPerTenantControl(true);
      status.setTier("enterprise");

      assertThat(status.isAvailable()).isTrue();
      assertThat(status.isEnabledByDefault()).isFalse();
      assertThat(status.isPerTenantControl()).isTrue();
      assertThat(status.getTier()).isEqualTo("enterprise");
    }

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{"
              + "\"available\": true,"
              + "\"enabled_by_default\": true,"
              + "\"per_tenant_control\": false,"
              + "\"tier\": \"professional\""
              + "}";

      MediaGovernanceStatus status = mapper.readValue(json, MediaGovernanceStatus.class);

      assertThat(status.isAvailable()).isTrue();
      assertThat(status.isEnabledByDefault()).isTrue();
      assertThat(status.isPerTenantControl()).isFalse();
      assertThat(status.getTier()).isEqualTo("professional");
    }

    @Test
    @DisplayName("should ignore unknown properties during deserialization")
    void shouldIgnoreUnknownProperties() throws Exception {
      String json = "{\"available\": false, \"tier\": \"free\", \"unknown_field\": true}";

      MediaGovernanceStatus status = mapper.readValue(json, MediaGovernanceStatus.class);

      assertThat(status.isAvailable()).isFalse();
      assertThat(status.getTier()).isEqualTo("free");
    }

    @Test
    @DisplayName("should serialize to JSON")
    void shouldSerializeToJson() throws Exception {
      MediaGovernanceStatus status = new MediaGovernanceStatus();
      status.setAvailable(true);
      status.setEnabledByDefault(true);
      status.setPerTenantControl(true);
      status.setTier("enterprise");

      String json = mapper.writeValueAsString(status);

      assertThat(json).contains("\"available\":true");
      assertThat(json).contains("\"enabled_by_default\":true");
      assertThat(json).contains("\"per_tenant_control\":true");
      assertThat(json).contains("\"tier\":\"enterprise\"");
    }

    @Test
    @DisplayName("equals should be reflexive")
    void equalsShouldBeReflexive() {
      MediaGovernanceStatus status = new MediaGovernanceStatus();
      status.setAvailable(true);

      assertThat(status).isEqualTo(status);
    }

    @Test
    @DisplayName("equals should compare all fields")
    void equalsShouldCompareAllFields() {
      MediaGovernanceStatus status1 = new MediaGovernanceStatus();
      status1.setAvailable(true);
      status1.setEnabledByDefault(false);
      status1.setPerTenantControl(true);
      status1.setTier("enterprise");

      MediaGovernanceStatus status2 = new MediaGovernanceStatus();
      status2.setAvailable(true);
      status2.setEnabledByDefault(false);
      status2.setPerTenantControl(true);
      status2.setTier("enterprise");

      assertThat(status1).isEqualTo(status2);
      assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
    }

    @Test
    @DisplayName("equals should detect differences")
    void equalsShouldDetectDifferences() {
      MediaGovernanceStatus status1 = new MediaGovernanceStatus();
      status1.setAvailable(true);
      status1.setTier("enterprise");

      MediaGovernanceStatus status2 = new MediaGovernanceStatus();
      status2.setAvailable(false);
      status2.setTier("enterprise");

      MediaGovernanceStatus status3 = new MediaGovernanceStatus();
      status3.setAvailable(true);
      status3.setTier("free");

      assertThat(status1).isNotEqualTo(status2);
      assertThat(status1).isNotEqualTo(status3);
      assertThat(status1).isNotEqualTo(null);
      assertThat(status1).isNotEqualTo("string");
    }

    @Test
    @DisplayName("toString should include all fields")
    void toStringShouldIncludeAllFields() {
      MediaGovernanceStatus status = new MediaGovernanceStatus();
      status.setAvailable(true);
      status.setEnabledByDefault(false);
      status.setPerTenantControl(true);
      status.setTier("enterprise");

      String str = status.toString();

      assertThat(str).contains("true");
      assertThat(str).contains("enterprise");
    }
  }

  // ========================================================================
  // UpdateMediaGovernanceConfigRequest
  // ========================================================================

  @Nested
  @DisplayName("UpdateMediaGovernanceConfigRequest")
  class UpdateMediaGovernanceConfigRequestTests {

    @Test
    @DisplayName("should create with default constructor and set fields")
    void shouldCreateWithDefaultConstructor() {
      UpdateMediaGovernanceConfigRequest request = new UpdateMediaGovernanceConfigRequest();
      request.setEnabled(true);
      request.setAllowedAnalyzers(List.of("nsfw", "ocr"));

      assertThat(request.getEnabled()).isTrue();
      assertThat(request.getAllowedAnalyzers()).containsExactly("nsfw", "ocr");
    }

    @Test
    @DisplayName("should build with builder pattern")
    void shouldBuildWithBuilder() {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw", "biometric"))
              .build();

      assertThat(request.getEnabled()).isTrue();
      assertThat(request.getAllowedAnalyzers()).containsExactly("nsfw", "biometric");
    }

    @Test
    @DisplayName("builder should handle null enabled for partial update")
    void builderShouldHandleNullEnabled() {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().allowedAnalyzers(List.of("nsfw")).build();

      assertThat(request.getEnabled()).isNull();
      assertThat(request.getAllowedAnalyzers()).containsExactly("nsfw");
    }

    @Test
    @DisplayName("builder should handle null allowedAnalyzers for partial update")
    void builderShouldHandleNullAnalyzers() {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().enabled(false).build();

      assertThat(request.getEnabled()).isFalse();
      assertThat(request.getAllowedAnalyzers()).isNull();
    }

    @Test
    @DisplayName("should serialize omitting null fields")
    void shouldSerializeOmittingNulls() throws Exception {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().enabled(true).build();

      String json = mapper.writeValueAsString(request);

      assertThat(json).contains("\"enabled\":true");
      assertThat(json).doesNotContain("allowed_analyzers");
    }

    @Test
    @DisplayName("should serialize with all fields")
    void shouldSerializeAllFields() throws Exception {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(false)
              .allowedAnalyzers(List.of("ocr"))
              .build();

      String json = mapper.writeValueAsString(request);

      assertThat(json).contains("\"enabled\":false");
      assertThat(json).contains("\"allowed_analyzers\":[\"ocr\"]");
    }

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"enabled\": true, \"allowed_analyzers\": [\"nsfw\", \"biometric\"]}";

      UpdateMediaGovernanceConfigRequest request =
          mapper.readValue(json, UpdateMediaGovernanceConfigRequest.class);

      assertThat(request.getEnabled()).isTrue();
      assertThat(request.getAllowedAnalyzers()).containsExactly("nsfw", "biometric");
    }

    @Test
    @DisplayName("equals should be reflexive")
    void equalsShouldBeReflexive() {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().enabled(true).build();

      assertThat(request).isEqualTo(request);
    }

    @Test
    @DisplayName("equals should compare all fields")
    void equalsShouldCompareAllFields() {
      UpdateMediaGovernanceConfigRequest r1 =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw"))
              .build();

      UpdateMediaGovernanceConfigRequest r2 =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw"))
              .build();

      assertThat(r1).isEqualTo(r2);
      assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("equals should detect differences")
    void equalsShouldDetectDifferences() {
      UpdateMediaGovernanceConfigRequest r1 =
          UpdateMediaGovernanceConfigRequest.builder().enabled(true).build();

      UpdateMediaGovernanceConfigRequest r2 =
          UpdateMediaGovernanceConfigRequest.builder().enabled(false).build();

      assertThat(r1).isNotEqualTo(r2);
      assertThat(r1).isNotEqualTo(null);
      assertThat(r1).isNotEqualTo("string");
    }

    @Test
    @DisplayName("toString should include fields")
    void toStringShouldIncludeFields() {
      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw", "biometric"))
              .build();

      String str = request.toString();

      assertThat(str).contains("true");
      assertThat(str).contains("nsfw");
      assertThat(str).contains("biometric");
    }
  }

  // ========================================================================
  // Media Policy Category Constants & Enum Values
  // ========================================================================

  @Nested
  @DisplayName("Media Policy Categories")
  class MediaPolicyCategoryTests {

    @Test
    @DisplayName("CATEGORY_MEDIA_SAFETY constant should match enum value")
    void mediaSafetyConstantShouldMatchEnum() {
      assertThat(PolicyTypes.CATEGORY_MEDIA_SAFETY).isEqualTo("media-safety");
      assertThat(PolicyCategory.MEDIA_SAFETY.getValue()).isEqualTo("media-safety");
      assertThat(PolicyTypes.CATEGORY_MEDIA_SAFETY)
          .isEqualTo(PolicyCategory.MEDIA_SAFETY.getValue());
    }

    @Test
    @DisplayName("CATEGORY_MEDIA_BIOMETRIC constant should match enum value")
    void mediaBiometricConstantShouldMatchEnum() {
      assertThat(PolicyTypes.CATEGORY_MEDIA_BIOMETRIC).isEqualTo("media-biometric");
      assertThat(PolicyCategory.MEDIA_BIOMETRIC.getValue()).isEqualTo("media-biometric");
      assertThat(PolicyTypes.CATEGORY_MEDIA_BIOMETRIC)
          .isEqualTo(PolicyCategory.MEDIA_BIOMETRIC.getValue());
    }

    @Test
    @DisplayName("CATEGORY_MEDIA_DOCUMENT constant should match enum value")
    void mediaDocumentConstantShouldMatchEnum() {
      assertThat(PolicyTypes.CATEGORY_MEDIA_DOCUMENT).isEqualTo("media-document");
      assertThat(PolicyCategory.MEDIA_DOCUMENT.getValue()).isEqualTo("media-document");
      assertThat(PolicyTypes.CATEGORY_MEDIA_DOCUMENT)
          .isEqualTo(PolicyCategory.MEDIA_DOCUMENT.getValue());
    }

    @Test
    @DisplayName("CATEGORY_MEDIA_PII constant should match enum value")
    void mediaPiiConstantShouldMatchEnum() {
      assertThat(PolicyTypes.CATEGORY_MEDIA_PII).isEqualTo("media-pii");
      assertThat(PolicyCategory.MEDIA_PII.getValue()).isEqualTo("media-pii");
      assertThat(PolicyTypes.CATEGORY_MEDIA_PII).isEqualTo(PolicyCategory.MEDIA_PII.getValue());
    }

    @Test
    @DisplayName("all media categories should exist in PolicyCategory enum")
    void allMediaCategoriesShouldExist() {
      assertThat(PolicyCategory.valueOf("MEDIA_SAFETY")).isNotNull();
      assertThat(PolicyCategory.valueOf("MEDIA_BIOMETRIC")).isNotNull();
      assertThat(PolicyCategory.valueOf("MEDIA_DOCUMENT")).isNotNull();
      assertThat(PolicyCategory.valueOf("MEDIA_PII")).isNotNull();
    }
  }
}
