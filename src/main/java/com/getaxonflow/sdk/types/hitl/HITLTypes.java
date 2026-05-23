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
package com.getaxonflow.sdk.types.hitl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Human-in-the-Loop (HITL) Queue types for AxonFlow SDK.
 *
 * <p>This class contains all types needed for HITL queue operations including:
 *
 * <ul>
 *   <li>Listing pending approval requests
 *   <li>Getting individual approval request details
 *   <li>Approving or rejecting requests
 *   <li>Retrieving dashboard statistics
 * </ul>
 *
 * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
 */
public final class HITLTypes {

  private HITLTypes() {
    // Utility class
  }

  // ========================================================================
  // Approval Request
  // ========================================================================

  /**
   * A pending HITL approval request.
   *
   * <p>Represents a request that has been paused by a policy trigger and requires human review
   * before proceeding.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HITLApprovalRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("original_query")
    private String originalQuery;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("request_context")
    private Map<String, Object> requestContext;

    @JsonProperty("triggered_policy_id")
    private String triggeredPolicyId;

    @JsonProperty("triggered_policy_name")
    private String triggeredPolicyName;

    @JsonProperty("trigger_reason")
    private String triggerReason;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("eu_ai_act_article")
    private String euAiActArticle;

    @JsonProperty("compliance_framework")
    private String complianceFramework;

    @JsonProperty("risk_classification")
    private String riskClassification;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reviewer_id")
    private String reviewerId;

    @JsonProperty("reviewer_email")
    private String reviewerEmail;

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonProperty("reviewed_at")
    private String reviewedAt;

    /**
     * Optional outbound webhook URL associated with the request.
     *
     * <p>Mirrors the value supplied on creation. Platforms that
     * implement the outbound-webhook dispatcher (introduced in
     * getaxonflow/axonflow-enterprise#2419) fire a signed POST to this
     * URL after the request reaches a terminal state
     * (approved/rejected/expired/overridden). Platforms that don't,
     * simply round-trip the field. Enables webhook-driven resume
     * (n8n Wait-node, ADK plugin polling-free mode).
     */
    @JsonProperty("notify_url")
    private String notifyUrl;

    @JsonProperty("expires_at")
    private String expiresAt;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    public HITLApprovalRequest() {}

    // Getters and setters
    public String getRequestId() {
      return requestId;
    }

    public void setRequestId(String requestId) {
      this.requestId = requestId;
    }

    public String getOrgId() {
      return orgId;
    }

    public void setOrgId(String orgId) {
      this.orgId = orgId;
    }

    public String getTenantId() {
      return tenantId;
    }

    public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public String getOriginalQuery() {
      return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
      this.originalQuery = originalQuery;
    }

    public String getRequestType() {
      return requestType;
    }

    public void setRequestType(String requestType) {
      this.requestType = requestType;
    }

    public Map<String, Object> getRequestContext() {
      return requestContext;
    }

    public void setRequestContext(Map<String, Object> requestContext) {
      this.requestContext = requestContext;
    }

    public String getTriggeredPolicyId() {
      return triggeredPolicyId;
    }

    public void setTriggeredPolicyId(String triggeredPolicyId) {
      this.triggeredPolicyId = triggeredPolicyId;
    }

    public String getTriggeredPolicyName() {
      return triggeredPolicyName;
    }

    public void setTriggeredPolicyName(String triggeredPolicyName) {
      this.triggeredPolicyName = triggeredPolicyName;
    }

    public String getTriggerReason() {
      return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
      this.triggerReason = triggerReason;
    }

    public String getSeverity() {
      return severity;
    }

    public void setSeverity(String severity) {
      this.severity = severity;
    }

    public String getEuAiActArticle() {
      return euAiActArticle;
    }

    public void setEuAiActArticle(String euAiActArticle) {
      this.euAiActArticle = euAiActArticle;
    }

    public String getComplianceFramework() {
      return complianceFramework;
    }

    public void setComplianceFramework(String complianceFramework) {
      this.complianceFramework = complianceFramework;
    }

    public String getRiskClassification() {
      return riskClassification;
    }

    public void setRiskClassification(String riskClassification) {
      this.riskClassification = riskClassification;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getReviewerId() {
      return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
      this.reviewerId = reviewerId;
    }

    public String getReviewerEmail() {
      return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
      this.reviewerEmail = reviewerEmail;
    }

    public String getReviewComment() {
      return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
      this.reviewComment = reviewComment;
    }

    public String getReviewedAt() {
      return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
      this.reviewedAt = reviewedAt;
    }

    public String getNotifyUrl() {
      return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
      this.notifyUrl = notifyUrl;
    }

    public String getExpiresAt() {
      return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
      this.expiresAt = expiresAt;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  // ========================================================================
  // Queue List Options
  // ========================================================================

  /** Options for listing HITL queue items. */
  public static class HITLQueueListOptions {

    private String status;
    private String severity;
    private Integer limit;
    private Integer offset;

    public static Builder builder() {
      return new Builder();
    }

    public String getStatus() {
      return status;
    }

    public String getSeverity() {
      return severity;
    }

    public Integer getLimit() {
      return limit;
    }

    public Integer getOffset() {
      return offset;
    }

    public static class Builder {
      private final HITLQueueListOptions options = new HITLQueueListOptions();

      /**
       * Filters by approval request status (e.g. "pending", "approved", "rejected").
       *
       * @param status the status filter
       * @return this builder
       */
      public Builder status(String status) {
        options.status = status;
        return this;
      }

      /**
       * Filters by severity level (e.g. "critical", "high", "medium", "low").
       *
       * @param severity the severity filter
       * @return this builder
       */
      public Builder severity(String severity) {
        options.severity = severity;
        return this;
      }

      /**
       * Sets the maximum number of items to return.
       *
       * @param limit the page size
       * @return this builder
       */
      public Builder limit(Integer limit) {
        options.limit = limit;
        return this;
      }

      /**
       * Sets the offset for pagination.
       *
       * @param offset the offset
       * @return this builder
       */
      public Builder offset(Integer offset) {
        options.offset = offset;
        return this;
      }

      public HITLQueueListOptions build() {
        return options;
      }
    }
  }

  // ========================================================================
  // Queue List Response
  // ========================================================================

  /** Response from listing HITL queue items. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HITLQueueListResponse {

    @JsonProperty("items")
    private List<HITLApprovalRequest> items;

    @JsonProperty("total")
    private long total;

    @JsonProperty("has_more")
    private boolean hasMore;

    public HITLQueueListResponse() {}

    public List<HITLApprovalRequest> getItems() {
      return items;
    }

    public void setItems(List<HITLApprovalRequest> items) {
      this.items = items;
    }

    public long getTotal() {
      return total;
    }

    public void setTotal(long total) {
      this.total = total;
    }

    public boolean isHasMore() {
      return hasMore;
    }

    public void setHasMore(boolean hasMore) {
      this.hasMore = hasMore;
    }
  }

  // ========================================================================
  // Create Input
  // ========================================================================

  /**
   * Input for creating a HITL approval request.
   *
   * <p>Mirrors {@code platform/agent/hitl/handler.go:86 CreateRequestInput}. The platform's
   * {@code POST /api/v1/hitl/queue} handler reads {@code X-Org-ID} and {@code X-Tenant-ID} from
   * request headers (set by the auth middleware from the SDK client's credentials), and the JSON
   * body must carry the fields below.
   *
   * <p>Used by agent-framework callers that detect {@code require_approval} from
   * {@code pre_check} / {@code check_tool_input} and want to enqueue the corresponding HITL row
   * before polling the reviewer's decision (or pivoting to webhook-driven resume via
   * {@code notifyUrl}).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HITLCreateInput {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("original_query")
    private String originalQuery;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("request_context")
    private Map<String, Object> requestContext;

    @JsonProperty("triggered_policy_id")
    private String triggeredPolicyId;

    @JsonProperty("triggered_policy_name")
    private String triggeredPolicyName;

    @JsonProperty("trigger_reason")
    private String triggerReason;

    @JsonProperty("severity")
    private String severity;

    /**
     * Optional outbound webhook URL fired async after terminal state transition. Must be
     * {@code https://} (or {@code http://} for self-hosted local-dev). Server-side validation
     * rejects bad schemes with HTTP 400. Pair with the HMAC-SHA256 {@code X-AxonFlow-Signature}
     * header on the receiver side; signing key is the deployment-configured
     * {@code AXONFLOW_HITL_WEBHOOK_SIGNING_KEY}. Introduced in
     * getaxonflow/axonflow-enterprise#2419.
     */
    @JsonProperty("notify_url")
    private String notifyUrl;

    @JsonProperty("eu_ai_act_article")
    private String euAiActArticle;

    @JsonProperty("compliance_framework")
    private String complianceFramework;

    @JsonProperty("risk_classification")
    private String riskClassification;

    @JsonProperty("expires_in_seconds")
    private Integer expiresInSeconds;

    public HITLCreateInput() {}

    public static Builder builder() {
      return new Builder();
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public String getOriginalQuery() {
      return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
      this.originalQuery = originalQuery;
    }

    public String getRequestType() {
      return requestType;
    }

    public void setRequestType(String requestType) {
      this.requestType = requestType;
    }

    public Map<String, Object> getRequestContext() {
      return requestContext;
    }

    public void setRequestContext(Map<String, Object> requestContext) {
      this.requestContext = requestContext;
    }

    public String getTriggeredPolicyId() {
      return triggeredPolicyId;
    }

    public void setTriggeredPolicyId(String triggeredPolicyId) {
      this.triggeredPolicyId = triggeredPolicyId;
    }

    public String getTriggeredPolicyName() {
      return triggeredPolicyName;
    }

    public void setTriggeredPolicyName(String triggeredPolicyName) {
      this.triggeredPolicyName = triggeredPolicyName;
    }

    public String getTriggerReason() {
      return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
      this.triggerReason = triggerReason;
    }

    public String getSeverity() {
      return severity;
    }

    public void setSeverity(String severity) {
      this.severity = severity;
    }

    public String getNotifyUrl() {
      return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
      this.notifyUrl = notifyUrl;
    }

    public String getEuAiActArticle() {
      return euAiActArticle;
    }

    public void setEuAiActArticle(String euAiActArticle) {
      this.euAiActArticle = euAiActArticle;
    }

    public String getComplianceFramework() {
      return complianceFramework;
    }

    public void setComplianceFramework(String complianceFramework) {
      this.complianceFramework = complianceFramework;
    }

    public String getRiskClassification() {
      return riskClassification;
    }

    public void setRiskClassification(String riskClassification) {
      this.riskClassification = riskClassification;
    }

    public Integer getExpiresInSeconds() {
      return expiresInSeconds;
    }

    public void setExpiresInSeconds(Integer expiresInSeconds) {
      this.expiresInSeconds = expiresInSeconds;
    }

    /** Builder for {@link HITLCreateInput}. */
    public static class Builder {
      private final HITLCreateInput input = new HITLCreateInput();

      public Builder clientId(String clientId) {
        input.clientId = clientId;
        return this;
      }

      public Builder userId(String userId) {
        input.userId = userId;
        return this;
      }

      public Builder originalQuery(String originalQuery) {
        input.originalQuery = originalQuery;
        return this;
      }

      public Builder requestType(String requestType) {
        input.requestType = requestType;
        return this;
      }

      public Builder requestContext(Map<String, Object> requestContext) {
        input.requestContext = requestContext;
        return this;
      }

      public Builder triggeredPolicyId(String triggeredPolicyId) {
        input.triggeredPolicyId = triggeredPolicyId;
        return this;
      }

      public Builder triggeredPolicyName(String triggeredPolicyName) {
        input.triggeredPolicyName = triggeredPolicyName;
        return this;
      }

      public Builder triggerReason(String triggerReason) {
        input.triggerReason = triggerReason;
        return this;
      }

      public Builder severity(String severity) {
        input.severity = severity;
        return this;
      }

      public Builder notifyUrl(String notifyUrl) {
        input.notifyUrl = notifyUrl;
        return this;
      }

      public Builder euAiActArticle(String euAiActArticle) {
        input.euAiActArticle = euAiActArticle;
        return this;
      }

      public Builder complianceFramework(String complianceFramework) {
        input.complianceFramework = complianceFramework;
        return this;
      }

      public Builder riskClassification(String riskClassification) {
        input.riskClassification = riskClassification;
        return this;
      }

      public Builder expiresInSeconds(Integer expiresInSeconds) {
        input.expiresInSeconds = expiresInSeconds;
        return this;
      }

      public HITLCreateInput build() {
        return input;
      }
    }
  }

  // ========================================================================
  // Review Input
  // ========================================================================

  /** Input for approving or rejecting a HITL request. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HITLReviewInput {

    @JsonProperty("reviewer_id")
    private String reviewerId;

    @JsonProperty("reviewer_email")
    private String reviewerEmail;

    @JsonProperty("reviewer_role")
    private String reviewerRole;

    @JsonProperty("comment")
    private String comment;

    public HITLReviewInput() {}

    public static Builder builder() {
      return new Builder();
    }

    public String getReviewerId() {
      return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
      this.reviewerId = reviewerId;
    }

    public String getReviewerEmail() {
      return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
      this.reviewerEmail = reviewerEmail;
    }

    public String getReviewerRole() {
      return reviewerRole;
    }

    public void setReviewerRole(String reviewerRole) {
      this.reviewerRole = reviewerRole;
    }

    public String getComment() {
      return comment;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }

    public static class Builder {
      private final HITLReviewInput input = new HITLReviewInput();

      /**
       * Sets the reviewer's user ID.
       *
       * @param reviewerId the reviewer ID
       * @return this builder
       */
      public Builder reviewerId(String reviewerId) {
        input.reviewerId = reviewerId;
        return this;
      }

      /**
       * Sets the reviewer's email address.
       *
       * @param reviewerEmail the reviewer email
       * @return this builder
       */
      public Builder reviewerEmail(String reviewerEmail) {
        input.reviewerEmail = reviewerEmail;
        return this;
      }

      /**
       * Sets the reviewer's role (optional).
       *
       * @param reviewerRole the reviewer role
       * @return this builder
       */
      public Builder reviewerRole(String reviewerRole) {
        input.reviewerRole = reviewerRole;
        return this;
      }

      /**
       * Sets the review comment (optional).
       *
       * @param comment the comment
       * @return this builder
       */
      public Builder comment(String comment) {
        input.comment = comment;
        return this;
      }

      public HITLReviewInput build() {
        return input;
      }
    }
  }

  // ========================================================================
  // Stats
  // ========================================================================

  /** HITL dashboard statistics. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HITLStats {

    @JsonProperty("total_pending")
    private long totalPending;

    @JsonProperty("high_priority")
    private long highPriority;

    @JsonProperty("critical_priority")
    private long criticalPriority;

    @JsonProperty("oldest_pending_hours")
    private Double oldestPendingHours;

    public HITLStats() {}

    public long getTotalPending() {
      return totalPending;
    }

    public void setTotalPending(long totalPending) {
      this.totalPending = totalPending;
    }

    public long getHighPriority() {
      return highPriority;
    }

    public void setHighPriority(long highPriority) {
      this.highPriority = highPriority;
    }

    public long getCriticalPriority() {
      return criticalPriority;
    }

    public void setCriticalPriority(long criticalPriority) {
      this.criticalPriority = criticalPriority;
    }

    public Double getOldestPendingHours() {
      return oldestPendingHours;
    }

    public void setOldestPendingHours(Double oldestPendingHours) {
      this.oldestPendingHours = oldestPendingHours;
    }
  }
}
