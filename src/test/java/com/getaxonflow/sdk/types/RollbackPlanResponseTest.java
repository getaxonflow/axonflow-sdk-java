/*
 * Copyright 2026 AxonFlow
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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RollbackPlanResponse")
class RollbackPlanResponseTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("should construct with all fields")
  void shouldConstructWithAllFields() {
    RollbackPlanResponse response = new RollbackPlanResponse("plan-123", 2, 3, "rolled_back");

    assertThat(response.getPlanId()).isEqualTo("plan-123");
    assertThat(response.getVersion()).isEqualTo(2);
    assertThat(response.getPreviousVersion()).isEqualTo(3);
    assertThat(response.getStatus()).isEqualTo("rolled_back");
  }

  @Test
  @DisplayName("should deserialize from JSON")
  void shouldDeserializeFromJson() throws Exception {
    String json =
        "{\"plan_id\":\"plan-456\",\"version\":1,\"previous_version\":3,\"status\":\"rolled_back\"}";

    RollbackPlanResponse response = objectMapper.readValue(json, RollbackPlanResponse.class);

    assertThat(response.getPlanId()).isEqualTo("plan-456");
    assertThat(response.getVersion()).isEqualTo(1);
    assertThat(response.getPreviousVersion()).isEqualTo(3);
    assertThat(response.getStatus()).isEqualTo("rolled_back");
  }

  @Test
  @DisplayName("should serialize to JSON")
  void shouldSerializeToJson() throws Exception {
    RollbackPlanResponse response = new RollbackPlanResponse("plan-789", 2, 4, "rolled_back");

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"plan_id\":\"plan-789\"");
    assertThat(json).contains("\"version\":2");
    assertThat(json).contains("\"previous_version\":4");
    assertThat(json).contains("\"status\":\"rolled_back\"");
  }

  @Test
  @DisplayName("should ignore unknown properties")
  void shouldIgnoreUnknownProperties() throws Exception {
    String json =
        "{\"plan_id\":\"plan-1\",\"version\":1,\"previous_version\":2,"
            + "\"status\":\"rolled_back\",\"unknown_field\":\"value\"}";

    RollbackPlanResponse response = objectMapper.readValue(json, RollbackPlanResponse.class);

    assertThat(response.getPlanId()).isEqualTo("plan-1");
  }

  @Test
  @DisplayName("equals and hashCode")
  void equalsAndHashCode() {
    RollbackPlanResponse r1 = new RollbackPlanResponse("plan-1", 2, 3, "rolled_back");
    RollbackPlanResponse r2 = new RollbackPlanResponse("plan-1", 2, 3, "rolled_back");
    RollbackPlanResponse r3 = new RollbackPlanResponse("plan-2", 2, 3, "rolled_back");

    assertThat(r1).isEqualTo(r2);
    assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    assertThat(r1).isNotEqualTo(r3);
  }

  @Test
  @DisplayName("toString contains all fields")
  void toStringShouldContainAllFields() {
    RollbackPlanResponse response = new RollbackPlanResponse("plan-1", 2, 3, "rolled_back");
    String str = response.toString();

    assertThat(str).contains("plan-1");
    assertThat(str).contains("2");
    assertThat(str).contains("3");
    assertThat(str).contains("rolled_back");
  }
}
