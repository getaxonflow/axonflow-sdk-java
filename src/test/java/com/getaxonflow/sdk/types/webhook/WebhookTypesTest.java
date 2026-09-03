// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.webhook;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.types.webhook.WebhookTypes.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for Webhook types (Feature 7). */
@DisplayName("Webhook Types")
class WebhookTypesTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  // ========================================================================
  // CreateWebhookRequest
  // ========================================================================

  @Test
  @DisplayName("CreateWebhookRequest - should build with builder")
  void createWebhookRequestShouldBuildWithBuilder() {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder()
            .url("https://example.com/webhook")
            .events(Arrays.asList("workflow.completed", "step.blocked"))
            .secret("my-secret-key")
            .active(true)
            .build();

    assertThat(request.getUrl()).isEqualTo("https://example.com/webhook");
    assertThat(request.getEvents()).containsExactly("workflow.completed", "step.blocked");
    assertThat(request.getSecret()).isEqualTo("my-secret-key");
    assertThat(request.isActive()).isTrue();
  }

  @Test
  @DisplayName("CreateWebhookRequest - should default active to true")
  void createWebhookRequestShouldDefaultActiveToTrue() {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder().url("https://example.com/webhook").build();

    assertThat(request.isActive()).isTrue();
  }

  @Test
  @DisplayName("CreateWebhookRequest - should require url")
  void createWebhookRequestShouldRequireUrl() {
    assertThatThrownBy(() -> CreateWebhookRequest.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("url");
  }

  @Test
  @DisplayName("CreateWebhookRequest - should deserialize from JSON")
  void createWebhookRequestShouldDeserialize() throws Exception {
    String json =
        "{\"url\":\"https://example.com/hook\",\"events\":[\"step.blocked\"],"
            + "\"secret\":\"s3cret\",\"active\":true}";

    CreateWebhookRequest request = objectMapper.readValue(json, CreateWebhookRequest.class);

    assertThat(request.getUrl()).isEqualTo("https://example.com/hook");
    assertThat(request.getEvents()).containsExactly("step.blocked");
    assertThat(request.getSecret()).isEqualTo("s3cret");
    assertThat(request.isActive()).isTrue();
  }

  @Test
  @DisplayName("CreateWebhookRequest - should serialize to JSON")
  void createWebhookRequestShouldSerialize() throws Exception {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder()
            .url("https://example.com/webhook")
            .events(Arrays.asList("workflow.completed"))
            .secret("key")
            .active(true)
            .build();

    String json = objectMapper.writeValueAsString(request);

    assertThat(json).contains("\"url\":\"https://example.com/webhook\"");
    assertThat(json).contains("\"events\":[\"workflow.completed\"]");
    assertThat(json).contains("\"active\":true");
  }

  @Test
  @DisplayName("CreateWebhookRequest - should handle null events as empty list")
  void createWebhookRequestShouldHandleNullEvents() {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder().url("https://example.com/webhook").events(null).build();

    assertThat(request.getEvents()).isEmpty();
  }

  @Test
  @DisplayName("CreateWebhookRequest - events should be immutable")
  void createWebhookRequestEventsShouldBeImmutable() {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder()
            .url("https://example.com/webhook")
            .events(Arrays.asList("event-1"))
            .build();

    assertThatThrownBy(() -> request.getEvents().add("event-2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("CreateWebhookRequest - equals and hashCode")
  void createWebhookRequestEqualsAndHashCode() {
    CreateWebhookRequest r1 =
        CreateWebhookRequest.builder()
            .url("https://example.com")
            .events(Arrays.asList("e1"))
            .secret("s")
            .active(true)
            .build();
    CreateWebhookRequest r2 =
        CreateWebhookRequest.builder()
            .url("https://example.com")
            .events(Arrays.asList("e1"))
            .secret("s")
            .active(true)
            .build();
    CreateWebhookRequest r3 =
        CreateWebhookRequest.builder()
            .url("https://other.com")
            .events(Arrays.asList("e1"))
            .secret("s")
            .active(true)
            .build();

    assertThat(r1).isEqualTo(r2);
    assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    assertThat(r1).isNotEqualTo(r3);
  }

  @Test
  @DisplayName("CreateWebhookRequest - toString should not contain secret")
  void createWebhookRequestToStringShouldNotContainSecret() {
    CreateWebhookRequest request =
        CreateWebhookRequest.builder()
            .url("https://example.com/webhook")
            .events(Arrays.asList("event-1"))
            .secret("super-secret-key")
            .active(true)
            .build();

    String str = request.toString();

    assertThat(str).contains("https://example.com/webhook");
    assertThat(str).contains("event-1");
    // Secret is excluded from toString for security
    assertThat(str).doesNotContain("super-secret-key");
  }

  // ========================================================================
  // WebhookSubscription
  // ========================================================================

  @Test
  @DisplayName("WebhookSubscription - should construct with all fields")
  void webhookSubscriptionShouldConstructWithAllFields() {
    WebhookSubscription subscription =
        new WebhookSubscription(
            "wh-123",
            "https://example.com/hook",
            Arrays.asList("step.blocked"),
            true,
            "2026-02-07T10:00:00Z",
            "2026-02-07T11:00:00Z");

    assertThat(subscription.getId()).isEqualTo("wh-123");
    assertThat(subscription.getUrl()).isEqualTo("https://example.com/hook");
    assertThat(subscription.getEvents()).containsExactly("step.blocked");
    assertThat(subscription.isActive()).isTrue();
    assertThat(subscription.getCreatedAt()).isEqualTo("2026-02-07T10:00:00Z");
    assertThat(subscription.getUpdatedAt()).isEqualTo("2026-02-07T11:00:00Z");
  }

  @Test
  @DisplayName("WebhookSubscription - should deserialize from JSON")
  void webhookSubscriptionShouldDeserialize() throws Exception {
    String json =
        "{"
            + "\"id\":\"wh-456\","
            + "\"url\":\"https://example.com/hook\","
            + "\"events\":[\"workflow.completed\",\"step.blocked\"],"
            + "\"active\":true,"
            + "\"created_at\":\"2026-02-07T10:00:00Z\","
            + "\"updated_at\":\"2026-02-07T11:00:00Z\""
            + "}";

    WebhookSubscription subscription = objectMapper.readValue(json, WebhookSubscription.class);

    assertThat(subscription.getId()).isEqualTo("wh-456");
    assertThat(subscription.getUrl()).isEqualTo("https://example.com/hook");
    assertThat(subscription.getEvents()).containsExactly("workflow.completed", "step.blocked");
    assertThat(subscription.isActive()).isTrue();
  }

  @Test
  @DisplayName("WebhookSubscription - should serialize to JSON")
  void webhookSubscriptionShouldSerialize() throws Exception {
    WebhookSubscription subscription =
        new WebhookSubscription(
            "wh-789",
            "https://example.com/hook",
            Arrays.asList("step.blocked"),
            true,
            "2026-02-07T10:00:00Z",
            "2026-02-07T11:00:00Z");

    String json = objectMapper.writeValueAsString(subscription);

    assertThat(json).contains("\"id\":\"wh-789\"");
    assertThat(json).contains("\"url\":\"https://example.com/hook\"");
    assertThat(json).contains("\"active\":true");
  }

  @Test
  @DisplayName("WebhookSubscription - should handle null events as empty list")
  void webhookSubscriptionShouldHandleNullEvents() {
    WebhookSubscription subscription =
        new WebhookSubscription("wh-1", "https://example.com", null, true, null, null);

    assertThat(subscription.getEvents()).isEmpty();
  }

  @Test
  @DisplayName("WebhookSubscription - events should be immutable")
  void webhookSubscriptionEventsShouldBeImmutable() {
    WebhookSubscription subscription =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, null, null);

    assertThatThrownBy(() -> subscription.getEvents().add("e2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("WebhookSubscription - equals and hashCode")
  void webhookSubscriptionEqualsAndHashCode() {
    WebhookSubscription s1 =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, "c1", "u1");
    WebhookSubscription s2 =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, "c1", "u1");
    WebhookSubscription s3 =
        new WebhookSubscription(
            "wh-2", "https://example.com", Arrays.asList("e1"), true, "c1", "u1");

    assertThat(s1).isEqualTo(s2);
    assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    assertThat(s1).isNotEqualTo(s3);
  }

  @Test
  @DisplayName("WebhookSubscription - toString contains key info")
  void webhookSubscriptionToStringShouldContainInfo() {
    WebhookSubscription subscription =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, null, null);
    String str = subscription.toString();

    assertThat(str).contains("wh-1");
    assertThat(str).contains("https://example.com");
    assertThat(str).contains("active=true");
  }

  @Test
  @DisplayName("WebhookSubscription - should ignore unknown properties")
  void webhookSubscriptionShouldIgnoreUnknownProperties() throws Exception {
    String json =
        "{\"id\":\"wh-1\",\"url\":\"https://example.com\","
            + "\"events\":[],\"active\":true,\"extra\":\"field\"}";

    WebhookSubscription subscription = objectMapper.readValue(json, WebhookSubscription.class);

    assertThat(subscription.getId()).isEqualTo("wh-1");
  }

  // ========================================================================
  // UpdateWebhookRequest
  // ========================================================================

  @Test
  @DisplayName("UpdateWebhookRequest - should build with builder")
  void updateWebhookRequestShouldBuildWithBuilder() {
    UpdateWebhookRequest request =
        UpdateWebhookRequest.builder()
            .url("https://new-url.com/hook")
            .events(Arrays.asList("step.approved"))
            .active(false)
            .build();

    assertThat(request.getUrl()).isEqualTo("https://new-url.com/hook");
    assertThat(request.getEvents()).containsExactly("step.approved");
    assertThat(request.getActive()).isFalse();
  }

  @Test
  @DisplayName("UpdateWebhookRequest - should allow partial updates (null fields)")
  void updateWebhookRequestShouldAllowPartialUpdates() {
    UpdateWebhookRequest request = UpdateWebhookRequest.builder().active(false).build();

    assertThat(request.getUrl()).isNull();
    assertThat(request.getEvents()).isNull();
    assertThat(request.getActive()).isFalse();
  }

  @Test
  @DisplayName("UpdateWebhookRequest - should deserialize from JSON")
  void updateWebhookRequestShouldDeserialize() throws Exception {
    String json = "{\"url\":\"https://new.com\",\"events\":[\"e1\"],\"active\":false}";

    UpdateWebhookRequest request = objectMapper.readValue(json, UpdateWebhookRequest.class);

    assertThat(request.getUrl()).isEqualTo("https://new.com");
    assertThat(request.getEvents()).containsExactly("e1");
    assertThat(request.getActive()).isFalse();
  }

  @Test
  @DisplayName("UpdateWebhookRequest - should serialize to JSON")
  void updateWebhookRequestShouldSerialize() throws Exception {
    UpdateWebhookRequest request =
        UpdateWebhookRequest.builder().url("https://new.com").active(true).build();

    String json = objectMapper.writeValueAsString(request);

    assertThat(json).contains("\"url\":\"https://new.com\"");
    assertThat(json).contains("\"active\":true");
  }

  @Test
  @DisplayName("UpdateWebhookRequest - equals and hashCode")
  void updateWebhookRequestEqualsAndHashCode() {
    UpdateWebhookRequest r1 =
        UpdateWebhookRequest.builder().url("https://a.com").active(true).build();
    UpdateWebhookRequest r2 =
        UpdateWebhookRequest.builder().url("https://a.com").active(true).build();
    UpdateWebhookRequest r3 =
        UpdateWebhookRequest.builder().url("https://b.com").active(true).build();

    assertThat(r1).isEqualTo(r2);
    assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    assertThat(r1).isNotEqualTo(r3);
  }

  @Test
  @DisplayName("UpdateWebhookRequest - toString contains fields")
  void updateWebhookRequestToStringShouldContainFields() {
    UpdateWebhookRequest request =
        UpdateWebhookRequest.builder().url("https://example.com").active(true).build();
    String str = request.toString();

    assertThat(str).contains("https://example.com");
    assertThat(str).contains("true");
  }

  // ========================================================================
  // ListWebhooksResponse
  // ========================================================================

  @Test
  @DisplayName("ListWebhooksResponse - should construct with all fields")
  void listWebhooksResponseShouldConstructWithAllFields() {
    WebhookSubscription sub =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, null, null);
    ListWebhooksResponse response = new ListWebhooksResponse(Collections.singletonList(sub), 1);

    assertThat(response.getWebhooks()).hasSize(1);
    assertThat(response.getTotal()).isEqualTo(1);
  }

  @Test
  @DisplayName("ListWebhooksResponse - should handle null webhooks list")
  void listWebhooksResponseShouldHandleNullList() {
    ListWebhooksResponse response = new ListWebhooksResponse(null, 0);

    assertThat(response.getWebhooks()).isEmpty();
    assertThat(response.getTotal()).isEqualTo(0);
  }

  @Test
  @DisplayName("ListWebhooksResponse - should deserialize from JSON")
  void listWebhooksResponseShouldDeserialize() throws Exception {
    String json =
        "{"
            + "\"webhooks\":["
            + "  {\"id\":\"wh-1\",\"url\":\"https://example.com\","
            + "   \"events\":[\"e1\"],\"active\":true}"
            + "],"
            + "\"total\":1"
            + "}";

    ListWebhooksResponse response = objectMapper.readValue(json, ListWebhooksResponse.class);

    assertThat(response.getWebhooks()).hasSize(1);
    assertThat(response.getTotal()).isEqualTo(1);
    assertThat(response.getWebhooks().get(0).getId()).isEqualTo("wh-1");
  }

  @Test
  @DisplayName("ListWebhooksResponse - webhooks list should be immutable")
  void listWebhooksResponseListShouldBeImmutable() {
    WebhookSubscription sub =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, null, null);
    ListWebhooksResponse response = new ListWebhooksResponse(Arrays.asList(sub), 1);

    assertThatThrownBy(
            () ->
                response
                    .getWebhooks()
                    .add(new WebhookSubscription("wh-2", "url", null, true, null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("ListWebhooksResponse - equals and hashCode")
  void listWebhooksResponseEqualsAndHashCode() {
    WebhookSubscription sub =
        new WebhookSubscription(
            "wh-1", "https://example.com", Arrays.asList("e1"), true, null, null);
    ListWebhooksResponse r1 = new ListWebhooksResponse(Collections.singletonList(sub), 1);
    ListWebhooksResponse r2 = new ListWebhooksResponse(Collections.singletonList(sub), 1);

    assertThat(r1).isEqualTo(r2);
    assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
  }

  @Test
  @DisplayName("ListWebhooksResponse - toString contains key info")
  void listWebhooksResponseToStringShouldContainInfo() {
    ListWebhooksResponse response = new ListWebhooksResponse(Collections.emptyList(), 0);
    String str = response.toString();

    assertThat(str).contains("total=0");
    assertThat(str).contains("webhooks=");
  }
}
