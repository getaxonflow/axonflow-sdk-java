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
package com.getaxonflow.sdk.types.webhook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Webhook subscription types for AxonFlow SDK.
 *
 * <p>This class contains all types needed for webhook CRUD operations including:
 *
 * <ul>
 *   <li>Creating webhook subscriptions
 *   <li>Updating webhook subscriptions
 *   <li>Listing webhook subscriptions
 * </ul>
 */
public final class WebhookTypes {

  private WebhookTypes() {
    // Utility class
  }

  /** Request to create a new webhook subscription. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CreateWebhookRequest {

    @JsonProperty("url")
    private final String url;

    @JsonProperty("events")
    private final List<String> events;

    @JsonProperty("secret")
    private final String secret;

    @JsonProperty("active")
    private final boolean active;

    @JsonCreator
    public CreateWebhookRequest(
        @JsonProperty("url") String url,
        @JsonProperty("events") List<String> events,
        @JsonProperty("secret") String secret,
        @JsonProperty("active") boolean active) {
      this.url = Objects.requireNonNull(url, "url is required");
      this.events = events != null ? Collections.unmodifiableList(events) : Collections.emptyList();
      this.secret = secret;
      this.active = active;
    }

    public String getUrl() {
      return url;
    }

    public List<String> getEvents() {
      return events;
    }

    public String getSecret() {
      return secret;
    }

    public boolean isActive() {
      return active;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private String url;
      private List<String> events;
      private String secret;
      private boolean active = true;

      public Builder url(String url) {
        this.url = url;
        return this;
      }

      public Builder events(List<String> events) {
        this.events = events;
        return this;
      }

      public Builder secret(String secret) {
        this.secret = secret;
        return this;
      }

      public Builder active(boolean active) {
        this.active = active;
        return this;
      }

      public CreateWebhookRequest build() {
        return new CreateWebhookRequest(url, events, secret, active);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CreateWebhookRequest that = (CreateWebhookRequest) o;
      return active == that.active
          && Objects.equals(url, that.url)
          && Objects.equals(events, that.events)
          && Objects.equals(secret, that.secret);
    }

    @Override
    public int hashCode() {
      return Objects.hash(url, events, secret, active);
    }

    @Override
    public String toString() {
      return "CreateWebhookRequest{"
          + "url='"
          + url
          + '\''
          + ", events="
          + events
          + ", active="
          + active
          + '}';
    }
  }

  /** A webhook subscription. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class WebhookSubscription {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("url")
    private final String url;

    @JsonProperty("events")
    private final List<String> events;

    @JsonProperty("active")
    private final boolean active;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("org_id")
    private final String orgId;

    @JsonProperty("secret")
    private final String secret;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonProperty("updated_at")
    private final String updatedAt;

    @JsonCreator
    public WebhookSubscription(
        @JsonProperty("id") String id,
        @JsonProperty("url") String url,
        @JsonProperty("events") List<String> events,
        @JsonProperty("active") boolean active,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("org_id") String orgId,
        @JsonProperty("secret") String secret) {
      this.id = id;
      this.url = url;
      this.events = events != null ? Collections.unmodifiableList(events) : Collections.emptyList();
      this.active = active;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
      this.tenantId = tenantId;
      this.orgId = orgId;
      this.secret = secret;
    }

    /**
     * Source-compat overload that omits the v6 wire-canonical fields (tenantId, orgId, secret).
     * Existing user code calling the 6-arg constructor continues to compile; new code should pass
     * the security-relevant secret + scoping fields explicitly.
     */
    public WebhookSubscription(
        String id,
        String url,
        List<String> events,
        boolean active,
        String createdAt,
        String updatedAt) {
      this(id, url, events, active, createdAt, updatedAt, null, null, null);
    }

    public String getId() {
      return id;
    }

    public String getUrl() {
      return url;
    }

    public List<String> getEvents() {
      return events;
    }

    public boolean isActive() {
      return active;
    }

    /** Tenant ID that owns this subscription. */
    public String getTenantId() {
      return tenantId;
    }

    /** Organization ID that owns this subscription. */
    public String getOrgId() {
      return orgId;
    }

    /**
     * HMAC-SHA256 signing key for verifying inbound webhook payload signatures
     * (X-AxonFlow-Signature header). Returned by the `createWebhook` call on initial creation;
     * required for callers to validate payload authenticity.
     */
    public String getSecret() {
      return secret;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    /**
     * Identity-based equality on {@code id}.
     *
     * <p>A {@code WebhookSubscription} is an entity, not a value object — two instances with the
     * same {@code id} represent the same subscription on the server, regardless of whether one view
     * has loaded {@code secret} (returned by {@code createWebhook} only) and another has not, or
     * whether {@code updatedAt} or {@code active} have moved between fetches. Field-by-field
     * equality would split same-id views into different objects and break {@code Set}/{@code Map}
     * membership and cache invalidation in caller code.
     *
     * <p>If you need content-equality (for example to detect rotated secrets), compare the relevant
     * getters directly.
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      WebhookSubscription that = (WebhookSubscription) o;
      return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }

    @Override
    public String toString() {
      return "WebhookSubscription{"
          + "id='"
          + id
          + '\''
          + ", url='"
          + url
          + '\''
          + ", events="
          + events
          + ", active="
          + active
          + ", tenantId='"
          + tenantId
          + '\''
          + ", orgId='"
          + orgId
          + '\''
          + ", secret='***'"
          + ", createdAt='"
          + createdAt
          + '\''
          + ", updatedAt='"
          + updatedAt
          + '\''
          + '}';
    }
  }

  /** Request to update an existing webhook subscription. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class UpdateWebhookRequest {

    @JsonProperty("url")
    private final String url;

    @JsonProperty("events")
    private final List<String> events;

    @JsonProperty("active")
    private final Boolean active;

    @JsonCreator
    public UpdateWebhookRequest(
        @JsonProperty("url") String url,
        @JsonProperty("events") List<String> events,
        @JsonProperty("active") Boolean active) {
      this.url = url;
      this.events = events != null ? Collections.unmodifiableList(events) : null;
      this.active = active;
    }

    public String getUrl() {
      return url;
    }

    public List<String> getEvents() {
      return events;
    }

    public Boolean getActive() {
      return active;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private String url;
      private List<String> events;
      private Boolean active;

      public Builder url(String url) {
        this.url = url;
        return this;
      }

      public Builder events(List<String> events) {
        this.events = events;
        return this;
      }

      public Builder active(Boolean active) {
        this.active = active;
        return this;
      }

      public UpdateWebhookRequest build() {
        return new UpdateWebhookRequest(url, events, active);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UpdateWebhookRequest that = (UpdateWebhookRequest) o;
      return Objects.equals(url, that.url)
          && Objects.equals(events, that.events)
          && Objects.equals(active, that.active);
    }

    @Override
    public int hashCode() {
      return Objects.hash(url, events, active);
    }

    @Override
    public String toString() {
      return "UpdateWebhookRequest{"
          + "url='"
          + url
          + '\''
          + ", events="
          + events
          + ", active="
          + active
          + '}';
    }
  }

  /** Response containing a list of webhook subscriptions. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ListWebhooksResponse {

    @JsonProperty("webhooks")
    private final List<WebhookSubscription> webhooks;

    @JsonProperty("total")
    private final int total;

    @JsonCreator
    public ListWebhooksResponse(
        @JsonProperty("webhooks") List<WebhookSubscription> webhooks,
        @JsonProperty("total") int total) {
      this.webhooks =
          webhooks != null ? Collections.unmodifiableList(webhooks) : Collections.emptyList();
      this.total = total;
    }

    public List<WebhookSubscription> getWebhooks() {
      return webhooks;
    }

    public int getTotal() {
      return total;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ListWebhooksResponse that = (ListWebhooksResponse) o;
      return total == that.total && Objects.equals(webhooks, that.webhooks);
    }

    @Override
    public int hashCode() {
      return Objects.hash(webhooks, total);
    }

    @Override
    public String toString() {
      return "ListWebhooksResponse{" + "webhooks=" + webhooks + ", total=" + total + '}';
    }
  }
}
