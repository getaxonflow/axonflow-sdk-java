/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.interceptors;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor.AnthropicContentBlock;
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor.AnthropicMessage;
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor.AnthropicRequest;
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor.AnthropicResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Anthropic Interceptor")
class AnthropicInterceptorTest {

    @Nested
    @DisplayName("Type Tests")
    class TypeTests {

        @Test
        @DisplayName("AnthropicRequest builder should work correctly")
        void testAnthropicRequestBuilder() {
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-opus-20240229")
                .maxTokens(2048)
                .system("You are a helpful assistant.")
                .addUserMessage("Hello!")
                .addAssistantMessage("Hi there!")
                .addUserMessage("How are you?")
                .temperature(0.8)
                .topP(0.95)
                .topK(40)
                .build();

            assertThat(request.getModel()).isEqualTo("claude-3-opus-20240229");
            assertThat(request.getMaxTokens()).isEqualTo(2048);
            assertThat(request.getSystem()).isEqualTo("You are a helpful assistant.");
            assertThat(request.getMessages()).hasSize(3);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("user");
            assertThat(request.getMessages().get(1).getRole()).isEqualTo("assistant");
            assertThat(request.getMessages().get(2).getRole()).isEqualTo("user");
            assertThat(request.getTemperature()).isEqualTo(0.8);
            assertThat(request.getTopP()).isEqualTo(0.95);
            assertThat(request.getTopK()).isEqualTo(40);
        }

        @Test
        @DisplayName("AnthropicRequest extractPrompt should include system and messages")
        void testAnthropicRequestExtractPrompt() {
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-sonnet-20240229")
                .maxTokens(1024)
                .system("System message")
                .addUserMessage("User message")
                .addAssistantMessage("Assistant message")
                .build();

            String prompt = request.extractPrompt();
            assertThat(prompt).contains("System message");
            assertThat(prompt).contains("User message");
            assertThat(prompt).contains("Assistant message");
        }

        @Test
        @DisplayName("AnthropicRequest should require model")
        void testAnthropicRequestRequiresModel() {
            assertThatThrownBy(() -> AnthropicRequest.builder()
                .maxTokens(1024)
                .addUserMessage("Test")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("AnthropicResponse builder should work correctly")
        void testAnthropicResponseBuilder() {
            AnthropicResponse response = AnthropicResponse.builder()
                .id("msg-test-123")
                .type("message")
                .role("assistant")
                .model("claude-3-sonnet-20240229")
                .content(List.of(
                    AnthropicContentBlock.text("First paragraph."),
                    AnthropicContentBlock.text("Second paragraph.")
                ))
                .stopReason("end_turn")
                .usage(new AnthropicResponse.Usage(100, 50))
                .build();

            assertThat(response.getId()).isEqualTo("msg-test-123");
            assertThat(response.getType()).isEqualTo("message");
            assertThat(response.getRole()).isEqualTo("assistant");
            assertThat(response.getModel()).isEqualTo("claude-3-sonnet-20240229");
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getStopReason()).isEqualTo("end_turn");
            assertThat(response.getUsage().getInputTokens()).isEqualTo(100);
            assertThat(response.getUsage().getOutputTokens()).isEqualTo(50);
        }

        @Test
        @DisplayName("AnthropicResponse getSummary should truncate long content")
        void testAnthropicResponseGetSummaryTruncation() {
            String longText = "A".repeat(200);
            AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicContentBlock.text(longText)))
                .build();

            assertThat(response.getSummary()).hasSize(100);
        }

        @Test
        @DisplayName("AnthropicResponse getSummary should return empty for no content")
        void testAnthropicResponseGetSummaryEmpty() {
            AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of())
                .build();

            assertThat(response.getSummary()).isEmpty();
        }

        @Test
        @DisplayName("AnthropicMessage factory methods should work correctly")
        void testAnthropicMessageFactory() {
            AnthropicMessage user = AnthropicMessage.user("User content");
            assertThat(user.getRole()).isEqualTo("user");
            assertThat(user.getContent()).hasSize(1);
            assertThat(user.getContent().get(0).getType()).isEqualTo("text");
            assertThat(user.getContent().get(0).getText()).isEqualTo("User content");

            AnthropicMessage assistant = AnthropicMessage.assistant("Assistant content");
            assertThat(assistant.getRole()).isEqualTo("assistant");
            assertThat(assistant.getContent().get(0).getText()).isEqualTo("Assistant content");
        }

        @Test
        @DisplayName("AnthropicContentBlock text factory should work correctly")
        void testAnthropicContentBlock() {
            AnthropicContentBlock block = AnthropicContentBlock.text("Test text");
            assertThat(block.getType()).isEqualTo("text");
            assertThat(block.getText()).isEqualTo("Test text");
        }
    }

    @Nested
    @WireMockTest
    @DisplayName("Integration Tests")
    class IntegrationTests {

        private AxonFlow axonflow;
        private AnthropicInterceptor interceptor;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
            interceptor = AnthropicInterceptor.builder()
                .axonflow(axonflow)
                .userToken("test-user")
                .asyncAudit(false)
                .build();
        }

        @Test
        @DisplayName("Builder should require AxonFlow")
        void testBuilderRequiresAxonFlow() {
            assertThatThrownBy(() -> AnthropicInterceptor.builder().build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("wrap should allow request when not blocked")
        void testWrapAllowedRequest() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-123\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true}")));

            // Create mock Anthropic call
            Function<AnthropicRequest, AnthropicResponse> mockCall = request ->
                AnthropicResponse.builder()
                    .id("msg-123")
                    .model("claude-3-sonnet-20240229")
                    .role("assistant")
                    .content(List.of(AnthropicContentBlock.text("Hello! I'm Claude.")))
                    .stopReason("end_turn")
                    .usage(new AnthropicResponse.Usage(10, 20))
                    .build();

            // Create request
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-sonnet-20240229")
                .maxTokens(1024)
                .addUserMessage("Hello!")
                .temperature(0.7)
                .build();

            // Execute wrapped call
            AnthropicResponse response = interceptor.wrap(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("msg-123");
            assertThat(response.getSummary()).isEqualTo("Hello! I'm Claude.");

            // Verify API was called
            verify(postRequestedFor(urlEqualTo("/api/request")));
        }

        @Test
        @DisplayName("wrap should throw when blocked by policy")
        void testWrapBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Request blocked by policy: content-filter\"}")));

            // Create mock Anthropic call (should not be called)
            Function<AnthropicRequest, AnthropicResponse> mockCall = request -> {
                fail("Anthropic call should not be made when blocked");
                return null;
            };

            // Create request
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-sonnet-20240229")
                .maxTokens(1024)
                .addUserMessage("Blocked content")
                .build();

            // Execute wrapped call
            assertThatThrownBy(() -> interceptor.wrap(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("content-filter");
        }

        @Test
        @DisplayName("wrapAsync should allow request when not blocked")
        void testWrapAsyncAllowedRequest() throws Exception {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-456\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true}")));

            // Create mock async Anthropic call
            Function<AnthropicRequest, CompletableFuture<AnthropicResponse>> mockCall =
                request -> CompletableFuture.completedFuture(
                    AnthropicResponse.builder()
                        .id("msg-456")
                        .model("claude-3-opus-20240229")
                        .content(List.of(AnthropicContentBlock.text("Async response")))
                        .usage(new AnthropicResponse.Usage(5, 15))
                        .build()
                );

            // Create request
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-opus-20240229")
                .maxTokens(1024)
                .addUserMessage("Async test")
                .build();

            // Execute wrapped async call
            AnthropicResponse response = interceptor.wrapAsync(mockCall)
                .apply(request)
                .get();

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("msg-456");
            assertThat(response.getSummary()).isEqualTo("Async response");
        }

        @Test
        @DisplayName("wrapAsync should throw when blocked by policy")
        void testWrapAsyncBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Policy violation detected\"}")));

            // Create mock async Anthropic call (should not be called)
            Function<AnthropicRequest, CompletableFuture<AnthropicResponse>> mockCall =
                request -> {
                    fail("Anthropic call should not be made when blocked");
                    return null;
                };

            // Create request
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-sonnet-20240229")
                .maxTokens(1024)
                .addUserMessage("Blocked")
                .build();

            // Execute wrapped async call
            CompletableFuture<AnthropicResponse> future = interceptor.wrapAsync(mockCall)
                .apply(request);

            assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PolicyViolationException.class);
        }

        @Test
        @DisplayName("static wrapMessage should work")
        void testStaticWrapperMethod() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-789\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true}")));

            // Create mock Anthropic call
            Function<AnthropicRequest, AnthropicResponse> mockCall = request ->
                AnthropicResponse.builder()
                    .id("msg-789")
                    .model("claude-3-haiku-20240307")
                    .build();

            // Use static wrapper
            AnthropicRequest request = AnthropicRequest.builder()
                .model("claude-3-haiku-20240307")
                .maxTokens(512)
                .addUserMessage("Static test")
                .build();

            AnthropicResponse response = AnthropicInterceptor.wrapMessage(
                axonflow, "user-token", mockCall
            ).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("msg-789");
        }
    }
}
