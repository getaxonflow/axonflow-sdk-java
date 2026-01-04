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
package com.getaxonflow.sdk.types.costcontrols;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Objects;

/**
 * Cost Controls types for AxonFlow SDK.
 *
 * <p>This class contains all types needed for cost control operations including:
 * <ul>
 *   <li>Budget management (create, update, delete, list)</li>
 *   <li>Budget status and alerts</li>
 *   <li>Usage tracking (summary, breakdown, records)</li>
 *   <li>Pricing information</li>
 * </ul>
 */
public final class CostControlTypes {

    private CostControlTypes() {
        // Prevent instantiation
    }

    // ========================================
    // ENUMS
    // ========================================

    /**
     * Budget scope determines what entity the budget applies to.
     */
    public enum BudgetScope {
        @JsonProperty("organization") ORGANIZATION("organization"),
        @JsonProperty("team") TEAM("team"),
        @JsonProperty("agent") AGENT("agent"),
        @JsonProperty("workflow") WORKFLOW("workflow"),
        @JsonProperty("user") USER("user");

        private final String value;

        BudgetScope(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static BudgetScope fromValue(String value) {
            for (BudgetScope scope : values()) {
                if (scope.value.equals(value)) {
                    return scope;
                }
            }
            throw new IllegalArgumentException("Unknown budget scope: " + value);
        }
    }

    /**
     * Budget period determines the time window for budget tracking.
     */
    public enum BudgetPeriod {
        @JsonProperty("daily") DAILY("daily"),
        @JsonProperty("weekly") WEEKLY("weekly"),
        @JsonProperty("monthly") MONTHLY("monthly"),
        @JsonProperty("quarterly") QUARTERLY("quarterly"),
        @JsonProperty("yearly") YEARLY("yearly");

        private final String value;

        BudgetPeriod(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static BudgetPeriod fromValue(String value) {
            for (BudgetPeriod period : values()) {
                if (period.value.equals(value)) {
                    return period;
                }
            }
            throw new IllegalArgumentException("Unknown budget period: " + value);
        }
    }

    /**
     * Action to take when budget is exceeded.
     */
    public enum BudgetOnExceed {
        @JsonProperty("warn") WARN("warn"),
        @JsonProperty("block") BLOCK("block"),
        @JsonProperty("downgrade") DOWNGRADE("downgrade");

        private final String value;

        BudgetOnExceed(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static BudgetOnExceed fromValue(String value) {
            for (BudgetOnExceed action : values()) {
                if (action.value.equals(value)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown budget on exceed action: " + value);
        }
    }

    // ========================================
    // BUDGET TYPES
    // ========================================

    /**
     * Request to create a new budget.
     */
    public static class CreateBudgetRequest {
        private final String id;
        private final String name;
        private final BudgetScope scope;
        @JsonProperty("limit_usd")
        private final Double limitUsd;
        private final BudgetPeriod period;
        @JsonProperty("on_exceed")
        private final BudgetOnExceed onExceed;
        @JsonProperty("alert_thresholds")
        private final List<Integer> alertThresholds;
        @JsonProperty("scope_id")
        private final String scopeId;

        private CreateBudgetRequest(Builder builder) {
            this.id = builder.id;
            this.name = builder.name;
            this.scope = builder.scope;
            this.limitUsd = builder.limitUsd;
            this.period = builder.period;
            this.onExceed = builder.onExceed;
            this.alertThresholds = builder.alertThresholds;
            this.scopeId = builder.scopeId;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public BudgetScope getScope() { return scope; }
        public Double getLimitUsd() { return limitUsd; }
        public BudgetPeriod getPeriod() { return period; }
        public BudgetOnExceed getOnExceed() { return onExceed; }
        public List<Integer> getAlertThresholds() { return alertThresholds; }
        public String getScopeId() { return scopeId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String id;
            private String name;
            private BudgetScope scope;
            private Double limitUsd;
            private BudgetPeriod period;
            private BudgetOnExceed onExceed;
            private List<Integer> alertThresholds;
            private String scopeId;

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder scope(BudgetScope scope) { this.scope = scope; return this; }
            public Builder limitUsd(Double limitUsd) { this.limitUsd = limitUsd; return this; }
            public Builder period(BudgetPeriod period) { this.period = period; return this; }
            public Builder onExceed(BudgetOnExceed onExceed) { this.onExceed = onExceed; return this; }
            public Builder alertThresholds(List<Integer> alertThresholds) { this.alertThresholds = alertThresholds; return this; }
            public Builder scopeId(String scopeId) { this.scopeId = scopeId; return this; }
            public CreateBudgetRequest build() { return new CreateBudgetRequest(this); }
        }
    }

    /**
     * Request to update an existing budget.
     */
    public static class UpdateBudgetRequest {
        private final String name;
        @JsonProperty("limit_usd")
        private final Double limitUsd;
        @JsonProperty("on_exceed")
        private final BudgetOnExceed onExceed;
        @JsonProperty("alert_thresholds")
        private final List<Integer> alertThresholds;

        private UpdateBudgetRequest(Builder builder) {
            this.name = builder.name;
            this.limitUsd = builder.limitUsd;
            this.onExceed = builder.onExceed;
            this.alertThresholds = builder.alertThresholds;
        }

        public String getName() { return name; }
        public Double getLimitUsd() { return limitUsd; }
        public BudgetOnExceed getOnExceed() { return onExceed; }
        public List<Integer> getAlertThresholds() { return alertThresholds; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String name;
            private Double limitUsd;
            private BudgetOnExceed onExceed;
            private List<Integer> alertThresholds;

            public Builder name(String name) { this.name = name; return this; }
            public Builder limitUsd(Double limitUsd) { this.limitUsd = limitUsd; return this; }
            public Builder onExceed(BudgetOnExceed onExceed) { this.onExceed = onExceed; return this; }
            public Builder alertThresholds(List<Integer> alertThresholds) { this.alertThresholds = alertThresholds; return this; }
            public UpdateBudgetRequest build() { return new UpdateBudgetRequest(this); }
        }
    }

    /**
     * Options for listing budgets.
     */
    public static class ListBudgetsOptions {
        private final BudgetScope scope;
        private final Integer limit;
        private final Integer offset;

        private ListBudgetsOptions(Builder builder) {
            this.scope = builder.scope;
            this.limit = builder.limit;
            this.offset = builder.offset;
        }

        public BudgetScope getScope() { return scope; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private BudgetScope scope;
            private Integer limit;
            private Integer offset;

            public Builder scope(BudgetScope scope) { this.scope = scope; return this; }
            public Builder limit(Integer limit) { this.limit = limit; return this; }
            public Builder offset(Integer offset) { this.offset = offset; return this; }
            public ListBudgetsOptions build() { return new ListBudgetsOptions(this); }
        }
    }

    /**
     * A budget entity.
     */
    public static class Budget {
        private String id;
        private String name;
        private String scope;
        @JsonProperty("limit_usd")
        private Double limitUsd;
        private String period;
        @JsonProperty("on_exceed")
        private String onExceed;
        @JsonProperty("alert_thresholds")
        private List<Integer> alertThresholds;
        private Boolean enabled;
        @JsonProperty("scope_id")
        private String scopeId;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;

        public Budget() {}

        public String getId() { return id; }
        public String getName() { return name; }
        public String getScope() { return scope; }
        public Double getLimitUsd() { return limitUsd; }
        public String getPeriod() { return period; }
        public String getOnExceed() { return onExceed; }
        public List<Integer> getAlertThresholds() { return alertThresholds; }
        public Boolean getEnabled() { return enabled; }
        public String getScopeId() { return scopeId; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }

    /**
     * Response containing a list of budgets.
     */
    public static class BudgetsResponse {
        private List<Budget> budgets;
        private Integer total;

        public BudgetsResponse() {}

        public List<Budget> getBudgets() { return budgets; }
        public Integer getTotal() { return total; }
    }

    // ========================================
    // BUDGET STATUS TYPES
    // ========================================

    /**
     * Current status of a budget.
     */
    public static class BudgetStatus {
        private Budget budget;
        @JsonProperty("used_usd")
        private Double usedUsd;
        @JsonProperty("remaining_usd")
        private Double remainingUsd;
        private Double percentage;
        @JsonProperty("is_exceeded")
        private Boolean isExceeded;
        @JsonProperty("is_blocked")
        private Boolean isBlocked;
        @JsonProperty("period_start")
        private String periodStart;
        @JsonProperty("period_end")
        private String periodEnd;

        public BudgetStatus() {}

        public Budget getBudget() { return budget; }
        public Double getUsedUsd() { return usedUsd; }
        public Double getRemainingUsd() { return remainingUsd; }
        public Double getPercentage() { return percentage; }
        public Boolean isExceeded() { return isExceeded; }
        public Boolean isBlocked() { return isBlocked; }
        public String getPeriodStart() { return periodStart; }
        public String getPeriodEnd() { return periodEnd; }
    }

    // ========================================
    // BUDGET ALERT TYPES
    // ========================================

    /**
     * A budget alert.
     */
    public static class BudgetAlert {
        private String id;
        @JsonProperty("budget_id")
        private String budgetId;
        @JsonProperty("alert_type")
        private String alertType;
        private Integer threshold;
        @JsonProperty("percentage_reached")
        private Double percentageReached;
        @JsonProperty("amount_usd")
        private Double amountUsd;
        private String message;
        @JsonProperty("created_at")
        private String createdAt;

        public BudgetAlert() {}

        public String getId() { return id; }
        public String getBudgetId() { return budgetId; }
        public String getAlertType() { return alertType; }
        public Integer getThreshold() { return threshold; }
        public Double getPercentageReached() { return percentageReached; }
        public Double getAmountUsd() { return amountUsd; }
        public String getMessage() { return message; }
        public String getCreatedAt() { return createdAt; }
    }

    /**
     * Response containing budget alerts.
     */
    public static class BudgetAlertsResponse {
        private List<BudgetAlert> alerts;
        private Integer count;

        public BudgetAlertsResponse() {}

        public List<BudgetAlert> getAlerts() { return alerts; }
        public Integer getCount() { return count; }
    }

    // ========================================
    // BUDGET CHECK TYPES
    // ========================================

    /**
     * Request to check if a request is allowed by budgets.
     */
    public static class BudgetCheckRequest {
        @JsonProperty("org_id")
        private final String orgId;
        @JsonProperty("team_id")
        private final String teamId;
        @JsonProperty("agent_id")
        private final String agentId;
        @JsonProperty("workflow_id")
        private final String workflowId;
        @JsonProperty("user_id")
        private final String userId;

        private BudgetCheckRequest(Builder builder) {
            this.orgId = builder.orgId;
            this.teamId = builder.teamId;
            this.agentId = builder.agentId;
            this.workflowId = builder.workflowId;
            this.userId = builder.userId;
        }

        public String getOrgId() { return orgId; }
        public String getTeamId() { return teamId; }
        public String getAgentId() { return agentId; }
        public String getWorkflowId() { return workflowId; }
        public String getUserId() { return userId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String orgId;
            private String teamId;
            private String agentId;
            private String workflowId;
            private String userId;

            public Builder orgId(String orgId) { this.orgId = orgId; return this; }
            public Builder teamId(String teamId) { this.teamId = teamId; return this; }
            public Builder agentId(String agentId) { this.agentId = agentId; return this; }
            public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public BudgetCheckRequest build() { return new BudgetCheckRequest(this); }
        }
    }

    /**
     * Budget decision result.
     */
    public static class BudgetDecision {
        private Boolean allowed;
        private String action;
        private String message;
        private List<Budget> budgets;

        public BudgetDecision() {}

        public Boolean isAllowed() { return allowed; }
        public String getAction() { return action; }
        public String getMessage() { return message; }
        public List<Budget> getBudgets() { return budgets; }
    }

    // ========================================
    // USAGE TYPES
    // ========================================

    /**
     * Usage summary for a period.
     */
    public static class UsageSummary {
        @JsonProperty("total_cost_usd")
        private Double totalCostUsd;
        @JsonProperty("total_requests")
        private Integer totalRequests;
        @JsonProperty("total_tokens_in")
        private Integer totalTokensIn;
        @JsonProperty("total_tokens_out")
        private Integer totalTokensOut;
        @JsonProperty("average_cost_per_request")
        private Double averageCostPerRequest;
        private String period;
        @JsonProperty("period_start")
        private String periodStart;
        @JsonProperty("period_end")
        private String periodEnd;

        public UsageSummary() {}

        public Double getTotalCostUsd() { return totalCostUsd; }
        public Integer getTotalRequests() { return totalRequests; }
        public Integer getTotalTokensIn() { return totalTokensIn; }
        public Integer getTotalTokensOut() { return totalTokensOut; }
        public Double getAverageCostPerRequest() { return averageCostPerRequest; }
        public String getPeriod() { return period; }
        public String getPeriodStart() { return periodStart; }
        public String getPeriodEnd() { return periodEnd; }
    }

    /**
     * An item in a usage breakdown.
     */
    public static class UsageBreakdownItem {
        @JsonProperty("group_value")
        private String groupValue;
        @JsonProperty("cost_usd")
        private Double costUsd;
        private Double percentage;
        @JsonProperty("request_count")
        private Integer requestCount;
        @JsonProperty("tokens_in")
        private Integer tokensIn;
        @JsonProperty("tokens_out")
        private Integer tokensOut;

        public UsageBreakdownItem() {}

        public String getGroupValue() { return groupValue; }
        public Double getCostUsd() { return costUsd; }
        public Double getPercentage() { return percentage; }
        public Integer getRequestCount() { return requestCount; }
        public Integer getTokensIn() { return tokensIn; }
        public Integer getTokensOut() { return tokensOut; }
    }

    /**
     * Usage breakdown by a grouping dimension.
     */
    public static class UsageBreakdown {
        @JsonProperty("group_by")
        private String groupBy;
        @JsonProperty("total_cost_usd")
        private Double totalCostUsd;
        private List<UsageBreakdownItem> items;
        private String period;
        @JsonProperty("period_start")
        private String periodStart;
        @JsonProperty("period_end")
        private String periodEnd;

        public UsageBreakdown() {}

        public String getGroupBy() { return groupBy; }
        public Double getTotalCostUsd() { return totalCostUsd; }
        public List<UsageBreakdownItem> getItems() { return items; }
        public String getPeriod() { return period; }
        public String getPeriodStart() { return periodStart; }
        public String getPeriodEnd() { return periodEnd; }
    }

    /**
     * Options for listing usage records.
     */
    public static class ListUsageRecordsOptions {
        private final Integer limit;
        private final Integer offset;
        private final String provider;
        private final String model;

        private ListUsageRecordsOptions(Builder builder) {
            this.limit = builder.limit;
            this.offset = builder.offset;
            this.provider = builder.provider;
            this.model = builder.model;
        }

        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
        public String getProvider() { return provider; }
        public String getModel() { return model; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer limit;
            private Integer offset;
            private String provider;
            private String model;

            public Builder limit(Integer limit) { this.limit = limit; return this; }
            public Builder offset(Integer offset) { this.offset = offset; return this; }
            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public ListUsageRecordsOptions build() { return new ListUsageRecordsOptions(this); }
        }
    }

    /**
     * A single usage record.
     */
    public static class UsageRecord {
        private String id;
        private String provider;
        private String model;
        @JsonProperty("tokens_in")
        private Integer tokensIn;
        @JsonProperty("tokens_out")
        private Integer tokensOut;
        @JsonProperty("cost_usd")
        private Double costUsd;
        @JsonProperty("request_id")
        private String requestId;
        @JsonProperty("org_id")
        private String orgId;
        @JsonProperty("agent_id")
        private String agentId;
        private String timestamp;

        public UsageRecord() {}

        public String getId() { return id; }
        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public Integer getTokensIn() { return tokensIn; }
        public Integer getTokensOut() { return tokensOut; }
        public Double getCostUsd() { return costUsd; }
        public String getRequestId() { return requestId; }
        public String getOrgId() { return orgId; }
        public String getAgentId() { return agentId; }
        public String getTimestamp() { return timestamp; }
    }

    /**
     * Response containing usage records.
     */
    public static class UsageRecordsResponse {
        private List<UsageRecord> records;
        private Integer total;

        public UsageRecordsResponse() {}

        public List<UsageRecord> getRecords() { return records; }
        public Integer getTotal() { return total; }
    }

    // ========================================
    // PRICING TYPES
    // ========================================

    /**
     * Model pricing information.
     */
    public static class ModelPricing {
        @JsonProperty("input_per_1k")
        private Double inputPer1k;
        @JsonProperty("output_per_1k")
        private Double outputPer1k;

        public ModelPricing() {}

        public Double getInputPer1k() { return inputPer1k; }
        public Double getOutputPer1k() { return outputPer1k; }
    }

    /**
     * Pricing information for a provider/model.
     */
    public static class PricingInfo {
        private String provider;
        private String model;
        private ModelPricing pricing;

        public PricingInfo() {}

        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public ModelPricing getPricing() { return pricing; }
    }

    /**
     * Response containing pricing information.
     */
    public static class PricingListResponse {
        private List<PricingInfo> pricing;

        public PricingListResponse() {}

        public List<PricingInfo> getPricing() { return pricing; }
        public void setPricing(List<PricingInfo> pricing) { this.pricing = pricing; }
    }
}
