// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Budget enforcement status information (Issue #1082).
 *
 * <p>Returned when a budget check is performed, showing current usage relative to budget limits.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BudgetInfo {

  @JsonProperty("budget_id")
  private final String budgetId;

  @JsonProperty("budget_name")
  private final String budgetName;

  @JsonProperty("used_usd")
  private final double usedUsd;

  @JsonProperty("limit_usd")
  private final double limitUsd;

  @JsonProperty("percentage")
  private final double percentage;

  @JsonProperty("exceeded")
  private final boolean exceeded;

  @JsonProperty("action")
  private final String action;

  public BudgetInfo(
      @JsonProperty("budget_id") String budgetId,
      @JsonProperty("budget_name") String budgetName,
      @JsonProperty("used_usd") double usedUsd,
      @JsonProperty("limit_usd") double limitUsd,
      @JsonProperty("percentage") double percentage,
      @JsonProperty("exceeded") boolean exceeded,
      @JsonProperty("action") String action) {
    this.budgetId = budgetId;
    this.budgetName = budgetName;
    this.usedUsd = usedUsd;
    this.limitUsd = limitUsd;
    this.percentage = percentage;
    this.exceeded = exceeded;
    this.action = action;
  }

  public String getBudgetId() {
    return budgetId;
  }

  public String getBudgetName() {
    return budgetName;
  }

  public double getUsedUsd() {
    return usedUsd;
  }

  public double getLimitUsd() {
    return limitUsd;
  }

  public double getPercentage() {
    return percentage;
  }

  public boolean isExceeded() {
    return exceeded;
  }

  public String getAction() {
    return action;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BudgetInfo that = (BudgetInfo) o;
    return Double.compare(that.usedUsd, usedUsd) == 0
        && Double.compare(that.limitUsd, limitUsd) == 0
        && Double.compare(that.percentage, percentage) == 0
        && exceeded == that.exceeded
        && Objects.equals(budgetId, that.budgetId)
        && Objects.equals(budgetName, that.budgetName)
        && Objects.equals(action, that.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(budgetId, budgetName, usedUsd, limitUsd, percentage, exceeded, action);
  }

  @Override
  public String toString() {
    return "BudgetInfo{"
        + "budgetId='"
        + budgetId
        + '\''
        + ", budgetName='"
        + budgetName
        + '\''
        + ", usedUsd="
        + usedUsd
        + ", limitUsd="
        + limitUsd
        + ", percentage="
        + percentage
        + ", exceeded="
        + exceeded
        + ", action='"
        + action
        + '\''
        + '}';
  }
}
