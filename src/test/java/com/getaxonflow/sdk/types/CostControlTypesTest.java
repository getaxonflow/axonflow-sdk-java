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
package com.getaxonflow.sdk.types;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.types.costcontrols.CostControlTypes.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Cost Control Types")
class CostControlTypesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nested
  @DisplayName("BudgetScope")
  class BudgetScopeTests {

    @Test
    @DisplayName("getValue should return correct string")
    void getValueShouldReturnCorrectString() {
      assertThat(BudgetScope.ORGANIZATION.getValue()).isEqualTo("organization");
      assertThat(BudgetScope.TEAM.getValue()).isEqualTo("team");
      assertThat(BudgetScope.AGENT.getValue()).isEqualTo("agent");
      assertThat(BudgetScope.WORKFLOW.getValue()).isEqualTo("workflow");
      assertThat(BudgetScope.USER.getValue()).isEqualTo("user");
    }

    @Test
    @DisplayName("fromValue should return correct enum")
    void fromValueShouldReturnCorrectEnum() {
      assertThat(BudgetScope.fromValue("organization")).isEqualTo(BudgetScope.ORGANIZATION);
      assertThat(BudgetScope.fromValue("team")).isEqualTo(BudgetScope.TEAM);
      assertThat(BudgetScope.fromValue("agent")).isEqualTo(BudgetScope.AGENT);
      assertThat(BudgetScope.fromValue("workflow")).isEqualTo(BudgetScope.WORKFLOW);
      assertThat(BudgetScope.fromValue("user")).isEqualTo(BudgetScope.USER);
    }

    @Test
    @DisplayName("fromValue should throw for invalid value")
    void fromValueShouldThrowForInvalid() {
      assertThatThrownBy(() -> BudgetScope.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown budget scope");
    }
  }

  @Nested
  @DisplayName("BudgetPeriod")
  class BudgetPeriodTests {

    @Test
    @DisplayName("getValue should return correct string")
    void getValueShouldReturnCorrectString() {
      assertThat(BudgetPeriod.DAILY.getValue()).isEqualTo("daily");
      assertThat(BudgetPeriod.WEEKLY.getValue()).isEqualTo("weekly");
      assertThat(BudgetPeriod.MONTHLY.getValue()).isEqualTo("monthly");
      assertThat(BudgetPeriod.QUARTERLY.getValue()).isEqualTo("quarterly");
      assertThat(BudgetPeriod.YEARLY.getValue()).isEqualTo("yearly");
    }

    @Test
    @DisplayName("fromValue should return correct enum")
    void fromValueShouldReturnCorrectEnum() {
      assertThat(BudgetPeriod.fromValue("daily")).isEqualTo(BudgetPeriod.DAILY);
      assertThat(BudgetPeriod.fromValue("weekly")).isEqualTo(BudgetPeriod.WEEKLY);
      assertThat(BudgetPeriod.fromValue("monthly")).isEqualTo(BudgetPeriod.MONTHLY);
      assertThat(BudgetPeriod.fromValue("quarterly")).isEqualTo(BudgetPeriod.QUARTERLY);
      assertThat(BudgetPeriod.fromValue("yearly")).isEqualTo(BudgetPeriod.YEARLY);
    }

    @Test
    @DisplayName("fromValue should throw for invalid value")
    void fromValueShouldThrowForInvalid() {
      assertThatThrownBy(() -> BudgetPeriod.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown budget period");
    }
  }

  @Nested
  @DisplayName("BudgetOnExceed")
  class BudgetOnExceedTests {

    @Test
    @DisplayName("getValue should return correct string")
    void getValueShouldReturnCorrectString() {
      assertThat(BudgetOnExceed.WARN.getValue()).isEqualTo("warn");
      assertThat(BudgetOnExceed.BLOCK.getValue()).isEqualTo("block");
      assertThat(BudgetOnExceed.DOWNGRADE.getValue()).isEqualTo("downgrade");
    }

    @Test
    @DisplayName("fromValue should return correct enum")
    void fromValueShouldReturnCorrectEnum() {
      assertThat(BudgetOnExceed.fromValue("warn")).isEqualTo(BudgetOnExceed.WARN);
      assertThat(BudgetOnExceed.fromValue("block")).isEqualTo(BudgetOnExceed.BLOCK);
      assertThat(BudgetOnExceed.fromValue("downgrade")).isEqualTo(BudgetOnExceed.DOWNGRADE);
    }

    @Test
    @DisplayName("fromValue should throw for invalid value")
    void fromValueShouldThrowForInvalid() {
      assertThatThrownBy(() -> BudgetOnExceed.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown budget on exceed action");
    }
  }

  @Nested
  @DisplayName("CreateBudgetRequest")
  class CreateBudgetRequestTests {

    @Test
    @DisplayName("builder should create request with all fields")
    void builderShouldCreateRequest() {
      CreateBudgetRequest request =
          CreateBudgetRequest.builder()
              .id("budget-123")
              .name("Monthly Budget")
              .scope(BudgetScope.ORGANIZATION)
              .limitUsd(1000.0)
              .period(BudgetPeriod.MONTHLY)
              .onExceed(BudgetOnExceed.WARN)
              .alertThresholds(List.of(50, 75, 90))
              .scopeId("org-123")
              .build();

      assertThat(request.getId()).isEqualTo("budget-123");
      assertThat(request.getName()).isEqualTo("Monthly Budget");
      assertThat(request.getScope()).isEqualTo(BudgetScope.ORGANIZATION);
      assertThat(request.getLimitUsd()).isEqualTo(1000.0);
      assertThat(request.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
      assertThat(request.getOnExceed()).isEqualTo(BudgetOnExceed.WARN);
      assertThat(request.getAlertThresholds()).containsExactly(50, 75, 90);
      assertThat(request.getScopeId()).isEqualTo("org-123");
    }
  }

  @Nested
  @DisplayName("UpdateBudgetRequest")
  class UpdateBudgetRequestTests {

    @Test
    @DisplayName("builder should create request with all fields")
    void builderShouldCreateRequest() {
      UpdateBudgetRequest request =
          UpdateBudgetRequest.builder()
              .name("Updated Budget")
              .limitUsd(2000.0)
              .onExceed(BudgetOnExceed.BLOCK)
              .alertThresholds(List.of(80, 95))
              .build();

      assertThat(request.getName()).isEqualTo("Updated Budget");
      assertThat(request.getLimitUsd()).isEqualTo(2000.0);
      assertThat(request.getOnExceed()).isEqualTo(BudgetOnExceed.BLOCK);
      assertThat(request.getAlertThresholds()).containsExactly(80, 95);
    }
  }

  @Nested
  @DisplayName("ListBudgetsOptions")
  class ListBudgetsOptionsTests {

    @Test
    @DisplayName("builder should create options with all fields")
    void builderShouldCreateOptions() {
      ListBudgetsOptions options =
          ListBudgetsOptions.builder().scope(BudgetScope.TEAM).limit(10).offset(20).build();

      assertThat(options.getScope()).isEqualTo(BudgetScope.TEAM);
      assertThat(options.getLimit()).isEqualTo(10);
      assertThat(options.getOffset()).isEqualTo(20);
    }

    @Test
    @DisplayName("builder should create options with default values")
    void builderShouldCreateDefaultOptions() {
      ListBudgetsOptions options = ListBudgetsOptions.builder().build();

      assertThat(options.getScope()).isNull();
      assertThat(options.getLimit()).isNull();
      assertThat(options.getOffset()).isNull();
    }
  }

  @Nested
  @DisplayName("BudgetCheckRequest")
  class BudgetCheckRequestTests {

    @Test
    @DisplayName("builder should create request with all fields")
    void builderShouldCreateRequest() {
      BudgetCheckRequest request =
          BudgetCheckRequest.builder()
              .orgId("org-123")
              .teamId("team-456")
              .agentId("agent-789")
              .workflowId("wf-101")
              .userId("user-202")
              .build();

      assertThat(request.getOrgId()).isEqualTo("org-123");
      assertThat(request.getTeamId()).isEqualTo("team-456");
      assertThat(request.getAgentId()).isEqualTo("agent-789");
      assertThat(request.getWorkflowId()).isEqualTo("wf-101");
      assertThat(request.getUserId()).isEqualTo("user-202");
    }
  }

  @Nested
  @DisplayName("ListUsageRecordsOptions")
  class ListUsageRecordsOptionsTests {

    @Test
    @DisplayName("builder should create options with all fields")
    void builderShouldCreateOptions() {
      ListUsageRecordsOptions options =
          ListUsageRecordsOptions.builder()
              .limit(50)
              .offset(100)
              .provider("openai")
              .model("gpt-4")
              .build();

      assertThat(options.getLimit()).isEqualTo(50);
      assertThat(options.getOffset()).isEqualTo(100);
      assertThat(options.getProvider()).isEqualTo("openai");
      assertThat(options.getModel()).isEqualTo("gpt-4");
    }
  }

  @Nested
  @DisplayName("Budget")
  class BudgetTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"id\":\"budget-123\",\"name\":\"Monthly Budget\",\"scope\":\"organization\","
              + "\"limit_usd\":1000.0,\"period\":\"monthly\",\"on_exceed\":\"warn\","
              + "\"alert_thresholds\":[50,75,90],\"enabled\":true,\"scope_id\":\"org-123\","
              + "\"created_at\":\"2025-01-01T00:00:00Z\",\"updated_at\":\"2025-01-02T00:00:00Z\"}";

      Budget budget = MAPPER.readValue(json, Budget.class);

      assertThat(budget.getId()).isEqualTo("budget-123");
      assertThat(budget.getName()).isEqualTo("Monthly Budget");
      assertThat(budget.getScope()).isEqualTo("organization");
      assertThat(budget.getLimitUsd()).isEqualTo(1000.0);
      assertThat(budget.getPeriod()).isEqualTo("monthly");
      assertThat(budget.getOnExceed()).isEqualTo("warn");
      assertThat(budget.getAlertThresholds()).containsExactly(50, 75, 90);
      assertThat(budget.getEnabled()).isTrue();
      assertThat(budget.getScopeId()).isEqualTo("org-123");
      assertThat(budget.getCreatedAt()).isEqualTo("2025-01-01T00:00:00Z");
      assertThat(budget.getUpdatedAt()).isEqualTo("2025-01-02T00:00:00Z");
    }
  }

  @Nested
  @DisplayName("BudgetsResponse")
  class BudgetsResponseTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"budgets\":[{\"id\":\"budget-1\"},{\"id\":\"budget-2\"}],\"total\":2}";

      BudgetsResponse response = MAPPER.readValue(json, BudgetsResponse.class);

      assertThat(response.getBudgets()).hasSize(2);
      assertThat(response.getTotal()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("BudgetStatus")
  class BudgetStatusTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"budget\":{\"id\":\"budget-123\"},\"used_usd\":500.0,\"remaining_usd\":500.0,"
              + "\"percentage\":50.0,\"is_exceeded\":false,\"is_blocked\":false,"
              + "\"period_start\":\"2025-01-01T00:00:00Z\",\"period_end\":\"2025-01-31T23:59:59Z\"}";

      BudgetStatus status = MAPPER.readValue(json, BudgetStatus.class);

      assertThat(status.getBudget().getId()).isEqualTo("budget-123");
      assertThat(status.getUsedUsd()).isEqualTo(500.0);
      assertThat(status.getRemainingUsd()).isEqualTo(500.0);
      assertThat(status.getPercentage()).isEqualTo(50.0);
      assertThat(status.isExceeded()).isFalse();
      assertThat(status.isBlocked()).isFalse();
      assertThat(status.getPeriodStart()).isEqualTo("2025-01-01T00:00:00Z");
      assertThat(status.getPeriodEnd()).isEqualTo("2025-01-31T23:59:59Z");
    }
  }

  @Nested
  @DisplayName("BudgetAlert")
  class BudgetAlertTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"id\":\"alert-123\",\"budget_id\":\"budget-456\",\"alert_type\":\"threshold\","
              + "\"threshold\":75,\"percentage_reached\":76.5,\"amount_usd\":765.0,"
              + "\"message\":\"Budget threshold reached\",\"created_at\":\"2025-01-15T12:00:00Z\"}";

      BudgetAlert alert = MAPPER.readValue(json, BudgetAlert.class);

      assertThat(alert.getId()).isEqualTo("alert-123");
      assertThat(alert.getBudgetId()).isEqualTo("budget-456");
      assertThat(alert.getAlertType()).isEqualTo("threshold");
      assertThat(alert.getThreshold()).isEqualTo(75);
      assertThat(alert.getPercentageReached()).isEqualTo(76.5);
      assertThat(alert.getAmountUsd()).isEqualTo(765.0);
      assertThat(alert.getMessage()).isEqualTo("Budget threshold reached");
      assertThat(alert.getCreatedAt()).isEqualTo("2025-01-15T12:00:00Z");
    }
  }

  @Nested
  @DisplayName("BudgetAlertsResponse")
  class BudgetAlertsResponseTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"alerts\":[{\"id\":\"alert-1\"},{\"id\":\"alert-2\"}],\"count\":2}";

      BudgetAlertsResponse response = MAPPER.readValue(json, BudgetAlertsResponse.class);

      assertThat(response.getAlerts()).hasSize(2);
      assertThat(response.getCount()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("BudgetDecision")
  class BudgetDecisionTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"allowed\":true,\"action\":\"allow\",\"message\":\"Within budget\","
              + "\"budgets\":[{\"id\":\"budget-1\"}]}";

      BudgetDecision decision = MAPPER.readValue(json, BudgetDecision.class);

      assertThat(decision.isAllowed()).isTrue();
      assertThat(decision.getAction()).isEqualTo("allow");
      assertThat(decision.getMessage()).isEqualTo("Within budget");
      assertThat(decision.getBudgets()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("UsageSummary")
  class UsageSummaryTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"total_cost_usd\":150.75,\"total_requests\":5000,\"total_tokens_in\":1000000,"
              + "\"total_tokens_out\":500000,\"average_cost_per_request\":0.03,\"period\":\"monthly\","
              + "\"period_start\":\"2025-01-01T00:00:00Z\",\"period_end\":\"2025-01-31T23:59:59Z\"}";

      UsageSummary summary = MAPPER.readValue(json, UsageSummary.class);

      assertThat(summary.getTotalCostUsd()).isEqualTo(150.75);
      assertThat(summary.getTotalRequests()).isEqualTo(5000);
      assertThat(summary.getTotalTokensIn()).isEqualTo(1000000);
      assertThat(summary.getTotalTokensOut()).isEqualTo(500000);
      assertThat(summary.getAverageCostPerRequest()).isEqualTo(0.03);
      assertThat(summary.getPeriod()).isEqualTo("monthly");
    }
  }

  @Nested
  @DisplayName("UsageBreakdownItem")
  class UsageBreakdownItemTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"group_value\":\"openai\",\"cost_usd\":100.0,\"percentage\":66.7,"
              + "\"request_count\":3000,\"tokens_in\":600000,\"tokens_out\":300000}";

      UsageBreakdownItem item = MAPPER.readValue(json, UsageBreakdownItem.class);

      assertThat(item.getGroupValue()).isEqualTo("openai");
      assertThat(item.getCostUsd()).isEqualTo(100.0);
      assertThat(item.getPercentage()).isEqualTo(66.7);
      assertThat(item.getRequestCount()).isEqualTo(3000);
      assertThat(item.getTokensIn()).isEqualTo(600000);
      assertThat(item.getTokensOut()).isEqualTo(300000);
    }
  }

  @Nested
  @DisplayName("UsageBreakdown")
  class UsageBreakdownTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"group_by\":\"provider\",\"total_cost_usd\":150.0,"
              + "\"items\":[{\"group_value\":\"openai\",\"cost_usd\":100.0}],\"period\":\"monthly\","
              + "\"period_start\":\"2025-01-01\",\"period_end\":\"2025-01-31\"}";

      UsageBreakdown breakdown = MAPPER.readValue(json, UsageBreakdown.class);

      assertThat(breakdown.getGroupBy()).isEqualTo("provider");
      assertThat(breakdown.getTotalCostUsd()).isEqualTo(150.0);
      assertThat(breakdown.getItems()).hasSize(1);
      assertThat(breakdown.getPeriod()).isEqualTo("monthly");
    }
  }

  @Nested
  @DisplayName("UsageRecord")
  class UsageRecordTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"id\":\"record-123\",\"provider\":\"openai\",\"model\":\"gpt-4\","
              + "\"tokens_in\":100,\"tokens_out\":50,\"cost_usd\":0.0045,"
              + "\"request_id\":\"req-456\",\"org_id\":\"org-789\",\"agent_id\":\"agent-101\","
              + "\"timestamp\":\"2025-01-15T12:00:00Z\"}";

      UsageRecord record = MAPPER.readValue(json, UsageRecord.class);

      assertThat(record.getId()).isEqualTo("record-123");
      assertThat(record.getProvider()).isEqualTo("openai");
      assertThat(record.getModel()).isEqualTo("gpt-4");
      assertThat(record.getTokensIn()).isEqualTo(100);
      assertThat(record.getTokensOut()).isEqualTo(50);
      assertThat(record.getCostUsd()).isEqualTo(0.0045);
      assertThat(record.getRequestId()).isEqualTo("req-456");
      assertThat(record.getOrgId()).isEqualTo("org-789");
      assertThat(record.getAgentId()).isEqualTo("agent-101");
      assertThat(record.getTimestamp()).isEqualTo("2025-01-15T12:00:00Z");
    }
  }

  @Nested
  @DisplayName("UsageRecordsResponse")
  class UsageRecordsResponseTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"records\":[{\"id\":\"record-1\"},{\"id\":\"record-2\"}],\"total\":2}";

      UsageRecordsResponse response = MAPPER.readValue(json, UsageRecordsResponse.class);

      assertThat(response.getRecords()).hasSize(2);
      assertThat(response.getTotal()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("ModelPricing")
  class ModelPricingTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"input_per_1k\":0.03,\"output_per_1k\":0.06}";

      ModelPricing pricing = MAPPER.readValue(json, ModelPricing.class);

      assertThat(pricing.getInputPer1k()).isEqualTo(0.03);
      assertThat(pricing.getOutputPer1k()).isEqualTo(0.06);
    }
  }

  @Nested
  @DisplayName("PricingInfo")
  class PricingInfoTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json =
          "{\"provider\":\"openai\",\"model\":\"gpt-4\","
              + "\"pricing\":{\"input_per_1k\":0.03,\"output_per_1k\":0.06}}";

      PricingInfo info = MAPPER.readValue(json, PricingInfo.class);

      assertThat(info.getProvider()).isEqualTo("openai");
      assertThat(info.getModel()).isEqualTo("gpt-4");
      assertThat(info.getPricing().getInputPer1k()).isEqualTo(0.03);
    }
  }

  @Nested
  @DisplayName("PricingListResponse")
  class PricingListResponseTests {

    @Test
    @DisplayName("should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
      String json = "{\"pricing\":[{\"provider\":\"openai\"},{\"provider\":\"anthropic\"}]}";

      PricingListResponse response = MAPPER.readValue(json, PricingListResponse.class);

      assertThat(response.getPricing()).hasSize(2);
    }

    @Test
    @DisplayName("setPricing should work")
    void setPricingShouldWork() {
      PricingListResponse response = new PricingListResponse();
      response.setPricing(List.of());
      assertThat(response.getPricing()).isEmpty();
    }
  }
}
