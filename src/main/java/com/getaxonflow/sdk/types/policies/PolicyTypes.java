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
package com.getaxonflow.sdk.types.policies;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Policy CRUD types for the Unified Policy Architecture v2.0.0.
 */
public final class PolicyTypes {

    private PolicyTypes() {}

    // ========================================================================
    // Enums
    // ========================================================================

    /**
     * Policy categories for organization and filtering.
     */
    public enum PolicyCategory {
        SECURITY_SQLI("security-sqli"),
        SECURITY_ADMIN("security-admin"),
        PII_GLOBAL("pii-global"),
        PII_US("pii-us"),
        PII_EU("pii-eu"),
        PII_INDIA("pii-india"),
        DYNAMIC_RISK("dynamic-risk"),
        DYNAMIC_COMPLIANCE("dynamic-compliance"),
        DYNAMIC_SECURITY("dynamic-security"),
        DYNAMIC_COST("dynamic-cost"),
        DYNAMIC_ACCESS("dynamic-access"),
        CUSTOM("custom");

        private final String value;

        PolicyCategory(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Policy tiers determine where policies apply.
     */
    public enum PolicyTier {
        SYSTEM("system"),
        ORGANIZATION("organization"),
        TENANT("tenant");

        private final String value;

        PolicyTier(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Override action for policy overrides.
     */
    public enum OverrideAction {
        BLOCK("block"),
        WARN("warn"),
        LOG("log"),
        REDACT("redact");

        private final String value;

        OverrideAction(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Action to take when a policy matches.
     */
    public enum PolicyAction {
        BLOCK("block"),
        WARN("warn"),
        LOG("log"),
        REDACT("redact"),
        ALLOW("allow");

        private final String value;

        PolicyAction(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    // ========================================================================
    // Static Policy Types
    // ========================================================================

    /**
     * Static policy definition.
     */
    public static class StaticPolicy {
        private String id;
        private String name;
        private String description;
        private PolicyCategory category;
        private PolicyTier tier;
        private String pattern;
        private int severity;
        private boolean enabled;
        private PolicyAction action;
        @JsonProperty("organization_id")
        private String organizationId;
        @JsonProperty("tenant_id")
        private String tenantId;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
        private Integer version;
        @JsonProperty("has_override")
        private Boolean hasOverride;
        private PolicyOverride override;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public PolicyCategory getCategory() { return category; }
        public void setCategory(PolicyCategory category) { this.category = category; }
        public PolicyTier getTier() { return tier; }
        public void setTier(PolicyTier tier) { this.tier = tier; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public int getSeverity() { return severity; }
        public void setSeverity(int severity) { this.severity = severity; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public PolicyAction getAction() { return action; }
        public void setAction(PolicyAction action) { this.action = action; }
        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Boolean getHasOverride() { return hasOverride; }
        public void setHasOverride(Boolean hasOverride) { this.hasOverride = hasOverride; }
        public PolicyOverride getOverride() { return override; }
        public void setOverride(PolicyOverride override) { this.override = override; }
    }

    /**
     * Policy override configuration.
     */
    public static class PolicyOverride {
        @JsonProperty("policy_id")
        private String policyId;
        private OverrideAction action;
        private String reason;
        @JsonProperty("created_by")
        private String createdBy;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("expires_at")
        private Instant expiresAt;
        private boolean active;

        // Getters and setters
        public String getPolicyId() { return policyId; }
        public void setPolicyId(String policyId) { this.policyId = policyId; }
        public OverrideAction getAction() { return action; }
        public void setAction(OverrideAction action) { this.action = action; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    /**
     * Options for listing static policies.
     */
    public static class ListStaticPoliciesOptions {
        private PolicyCategory category;
        private PolicyTier tier;
        private Boolean enabled;
        private Integer limit;
        private Integer offset;
        private String sortBy;
        private String sortOrder;
        private String search;

        public static Builder builder() {
            return new Builder();
        }

        public PolicyCategory getCategory() { return category; }
        public PolicyTier getTier() { return tier; }
        public Boolean getEnabled() { return enabled; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
        public String getSortBy() { return sortBy; }
        public String getSortOrder() { return sortOrder; }
        public String getSearch() { return search; }

        public static class Builder {
            private final ListStaticPoliciesOptions options = new ListStaticPoliciesOptions();

            public Builder category(PolicyCategory category) {
                options.category = category;
                return this;
            }

            public Builder tier(PolicyTier tier) {
                options.tier = tier;
                return this;
            }

            public Builder enabled(Boolean enabled) {
                options.enabled = enabled;
                return this;
            }

            public Builder limit(Integer limit) {
                options.limit = limit;
                return this;
            }

            public Builder offset(Integer offset) {
                options.offset = offset;
                return this;
            }

            public Builder sortBy(String sortBy) {
                options.sortBy = sortBy;
                return this;
            }

            public Builder sortOrder(String sortOrder) {
                options.sortOrder = sortOrder;
                return this;
            }

            public Builder search(String search) {
                options.search = search;
                return this;
            }

            public ListStaticPoliciesOptions build() {
                return options;
            }
        }
    }

    /**
     * Request to create a new static policy.
     */
    public static class CreateStaticPolicyRequest {
        private String name;
        private String description;
        private PolicyCategory category;
        private String pattern;
        private int severity = 5;
        private boolean enabled = true;
        private PolicyAction action = PolicyAction.BLOCK;

        public static Builder builder() {
            return new Builder();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public PolicyCategory getCategory() { return category; }
        public String getPattern() { return pattern; }
        public int getSeverity() { return severity; }
        public boolean isEnabled() { return enabled; }
        public PolicyAction getAction() { return action; }

        public static class Builder {
            private final CreateStaticPolicyRequest request = new CreateStaticPolicyRequest();

            public Builder name(String name) {
                request.name = name;
                return this;
            }

            public Builder description(String description) {
                request.description = description;
                return this;
            }

            public Builder category(PolicyCategory category) {
                request.category = category;
                return this;
            }

            public Builder pattern(String pattern) {
                request.pattern = pattern;
                return this;
            }

            public Builder severity(int severity) {
                request.severity = severity;
                return this;
            }

            public Builder enabled(boolean enabled) {
                request.enabled = enabled;
                return this;
            }

            public Builder action(PolicyAction action) {
                request.action = action;
                return this;
            }

            public CreateStaticPolicyRequest build() {
                return request;
            }
        }
    }

    /**
     * Request to update an existing static policy.
     */
    public static class UpdateStaticPolicyRequest {
        private String name;
        private String description;
        private PolicyCategory category;
        private String pattern;
        private Integer severity;
        private Boolean enabled;
        private PolicyAction action;

        public static Builder builder() {
            return new Builder();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public PolicyCategory getCategory() { return category; }
        public String getPattern() { return pattern; }
        public Integer getSeverity() { return severity; }
        public Boolean getEnabled() { return enabled; }
        public PolicyAction getAction() { return action; }

        public static class Builder {
            private final UpdateStaticPolicyRequest request = new UpdateStaticPolicyRequest();

            public Builder name(String name) {
                request.name = name;
                return this;
            }

            public Builder description(String description) {
                request.description = description;
                return this;
            }

            public Builder category(PolicyCategory category) {
                request.category = category;
                return this;
            }

            public Builder pattern(String pattern) {
                request.pattern = pattern;
                return this;
            }

            public Builder severity(Integer severity) {
                request.severity = severity;
                return this;
            }

            public Builder enabled(Boolean enabled) {
                request.enabled = enabled;
                return this;
            }

            public Builder action(PolicyAction action) {
                request.action = action;
                return this;
            }

            public UpdateStaticPolicyRequest build() {
                return request;
            }
        }
    }

    /**
     * Request to create a policy override.
     */
    public static class CreatePolicyOverrideRequest {
        private OverrideAction action;
        private String reason;
        @JsonProperty("expires_at")
        private Instant expiresAt;

        public static Builder builder() {
            return new Builder();
        }

        public OverrideAction getAction() { return action; }
        public String getReason() { return reason; }
        public Instant getExpiresAt() { return expiresAt; }

        public static class Builder {
            private final CreatePolicyOverrideRequest request = new CreatePolicyOverrideRequest();

            public Builder action(OverrideAction action) {
                request.action = action;
                return this;
            }

            public Builder reason(String reason) {
                request.reason = reason;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                request.expiresAt = expiresAt;
                return this;
            }

            public CreatePolicyOverrideRequest build() {
                return request;
            }
        }
    }

    // ========================================================================
    // Dynamic Policy Types
    // ========================================================================

    /**
     * Dynamic policy configuration.
     */
    public static class DynamicPolicyConfig {
        private String type;
        private Map<String, Object> rules;
        private List<DynamicPolicyCondition> conditions;
        private PolicyAction action;
        private Map<String, Object> parameters;

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Map<String, Object> getRules() { return rules; }
        public void setRules(Map<String, Object> rules) { this.rules = rules; }
        public List<DynamicPolicyCondition> getConditions() { return conditions; }
        public void setConditions(List<DynamicPolicyCondition> conditions) { this.conditions = conditions; }
        public PolicyAction getAction() { return action; }
        public void setAction(PolicyAction action) { this.action = action; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final DynamicPolicyConfig config = new DynamicPolicyConfig();

            public Builder type(String type) {
                config.type = type;
                return this;
            }

            public Builder rules(Map<String, Object> rules) {
                config.rules = rules;
                return this;
            }

            public Builder action(PolicyAction action) {
                config.action = action;
                return this;
            }

            public DynamicPolicyConfig build() {
                return config;
            }
        }
    }

    /**
     * Condition for dynamic policy evaluation.
     */
    public static class DynamicPolicyCondition {
        private String field;
        private String operator;
        private Object value;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    /**
     * Dynamic policy definition.
     */
    public static class DynamicPolicy {
        private String id;
        private String name;
        private String description;
        private PolicyCategory category;
        private PolicyTier tier;
        private boolean enabled;
        @JsonProperty("organization_id")
        private String organizationId;
        @JsonProperty("tenant_id")
        private String tenantId;
        private DynamicPolicyConfig config;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
        private Integer version;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public PolicyCategory getCategory() { return category; }
        public void setCategory(PolicyCategory category) { this.category = category; }
        public PolicyTier getTier() { return tier; }
        public void setTier(PolicyTier tier) { this.tier = tier; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public DynamicPolicyConfig getConfig() { return config; }
        public void setConfig(DynamicPolicyConfig config) { this.config = config; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    /**
     * Options for listing dynamic policies.
     */
    public static class ListDynamicPoliciesOptions {
        private PolicyCategory category;
        private PolicyTier tier;
        private Boolean enabled;
        private Integer limit;
        private Integer offset;
        private String sortBy;
        private String sortOrder;
        private String search;

        public static Builder builder() {
            return new Builder();
        }

        public PolicyCategory getCategory() { return category; }
        public PolicyTier getTier() { return tier; }
        public Boolean getEnabled() { return enabled; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
        public String getSortBy() { return sortBy; }
        public String getSortOrder() { return sortOrder; }
        public String getSearch() { return search; }

        public static class Builder {
            private final ListDynamicPoliciesOptions options = new ListDynamicPoliciesOptions();

            public Builder category(PolicyCategory category) {
                options.category = category;
                return this;
            }

            public Builder tier(PolicyTier tier) {
                options.tier = tier;
                return this;
            }

            public Builder enabled(Boolean enabled) {
                options.enabled = enabled;
                return this;
            }

            public Builder limit(Integer limit) {
                options.limit = limit;
                return this;
            }

            public Builder offset(Integer offset) {
                options.offset = offset;
                return this;
            }

            public Builder sortBy(String sortBy) {
                options.sortBy = sortBy;
                return this;
            }

            public Builder sortOrder(String sortOrder) {
                options.sortOrder = sortOrder;
                return this;
            }

            public Builder search(String search) {
                options.search = search;
                return this;
            }

            public ListDynamicPoliciesOptions build() {
                return options;
            }
        }
    }

    /**
     * Request to create a dynamic policy.
     */
    public static class CreateDynamicPolicyRequest {
        private String name;
        private String description;
        private PolicyCategory category;
        private DynamicPolicyConfig config;
        private boolean enabled = true;

        public static Builder builder() {
            return new Builder();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public PolicyCategory getCategory() { return category; }
        public DynamicPolicyConfig getConfig() { return config; }
        public boolean isEnabled() { return enabled; }

        public static class Builder {
            private final CreateDynamicPolicyRequest request = new CreateDynamicPolicyRequest();

            public Builder name(String name) {
                request.name = name;
                return this;
            }

            public Builder description(String description) {
                request.description = description;
                return this;
            }

            public Builder category(PolicyCategory category) {
                request.category = category;
                return this;
            }

            public Builder config(DynamicPolicyConfig config) {
                request.config = config;
                return this;
            }

            public Builder enabled(boolean enabled) {
                request.enabled = enabled;
                return this;
            }

            public CreateDynamicPolicyRequest build() {
                return request;
            }
        }
    }

    /**
     * Request to update a dynamic policy.
     */
    public static class UpdateDynamicPolicyRequest {
        private String name;
        private String description;
        private PolicyCategory category;
        private DynamicPolicyConfig config;
        private Boolean enabled;

        public static Builder builder() {
            return new Builder();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public PolicyCategory getCategory() { return category; }
        public DynamicPolicyConfig getConfig() { return config; }
        public Boolean getEnabled() { return enabled; }

        public static class Builder {
            private final UpdateDynamicPolicyRequest request = new UpdateDynamicPolicyRequest();

            public Builder name(String name) {
                request.name = name;
                return this;
            }

            public Builder description(String description) {
                request.description = description;
                return this;
            }

            public Builder category(PolicyCategory category) {
                request.category = category;
                return this;
            }

            public Builder config(DynamicPolicyConfig config) {
                request.config = config;
                return this;
            }

            public Builder enabled(Boolean enabled) {
                request.enabled = enabled;
                return this;
            }

            public UpdateDynamicPolicyRequest build() {
                return request;
            }
        }
    }

    // ========================================================================
    // Pattern Testing Types
    // ========================================================================

    /**
     * Result of testing a regex pattern.
     */
    public static class TestPatternResult {
        private boolean valid;
        private String error;
        private List<TestPatternMatch> results;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public List<TestPatternMatch> getResults() { return results; }
        public void setResults(List<TestPatternMatch> results) { this.results = results; }
    }

    /**
     * Individual pattern match result.
     */
    public static class TestPatternMatch {
        private String input;
        private boolean matched;
        @JsonProperty("matched_text")
        private String matchedText;
        private Integer position;

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public boolean isMatched() { return matched; }
        public void setMatched(boolean matched) { this.matched = matched; }
        public String getMatchedText() { return matchedText; }
        public void setMatchedText(String matchedText) { this.matchedText = matchedText; }
        public Integer getPosition() { return position; }
        public void setPosition(Integer position) { this.position = position; }
    }

    // ========================================================================
    // Policy Version Types
    // ========================================================================

    /**
     * Policy version history entry.
     */
    public static class PolicyVersion {
        private int version;
        @JsonProperty("changed_by")
        private String changedBy;
        @JsonProperty("changed_at")
        private Instant changedAt;
        @JsonProperty("change_type")
        private String changeType;
        @JsonProperty("change_description")
        private String changeDescription;
        @JsonProperty("previous_values")
        private Map<String, Object> previousValues;
        @JsonProperty("new_values")
        private Map<String, Object> newValues;

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getChangedBy() { return changedBy; }
        public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
        public Instant getChangedAt() { return changedAt; }
        public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getChangeDescription() { return changeDescription; }
        public void setChangeDescription(String changeDescription) { this.changeDescription = changeDescription; }
        public Map<String, Object> getPreviousValues() { return previousValues; }
        public void setPreviousValues(Map<String, Object> previousValues) { this.previousValues = previousValues; }
        public Map<String, Object> getNewValues() { return newValues; }
        public void setNewValues(Map<String, Object> newValues) { this.newValues = newValues; }
    }

    /**
     * Options for getting effective policies.
     */
    public static class EffectivePoliciesOptions {
        private PolicyCategory category;
        private boolean includeDisabled;
        private boolean includeOverridden;

        public static Builder builder() {
            return new Builder();
        }

        public PolicyCategory getCategory() { return category; }
        public boolean isIncludeDisabled() { return includeDisabled; }
        public boolean isIncludeOverridden() { return includeOverridden; }

        public static class Builder {
            private final EffectivePoliciesOptions options = new EffectivePoliciesOptions();

            public Builder category(PolicyCategory category) {
                options.category = category;
                return this;
            }

            public Builder includeDisabled(boolean includeDisabled) {
                options.includeDisabled = includeDisabled;
                return this;
            }

            public Builder includeOverridden(boolean includeOverridden) {
                options.includeOverridden = includeOverridden;
                return this;
            }

            public EffectivePoliciesOptions build() {
                return options;
            }
        }
    }
}
