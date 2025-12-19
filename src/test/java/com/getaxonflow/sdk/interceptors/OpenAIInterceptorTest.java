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

@DisplayName("OpenAI Interceptor")
class OpenAIInterceptorTest {

    @Nested
    @DisplayName("Type Tests")
    class TypeTests {

        @Test
        @DisplayName("ChatCompletionRequest builder should work correctly")
        void testChatCompletionRequestBuilder() {
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4-turbo")
                .addSystemMessage("You are a helpful assistant.")
                .addUserMessage("What is 2+2?")
                .temperature(0.5)
                .maxTokens(100)
                .topP(0.9)
                .n(1)
                .stream(false)
                .stop(List.of("\n"))
                .build();

            assertThat(request.getModel()).isEqualTo("gpt-4-turbo");
            assertThat(request.getMessages()).hasSize(2);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
            assertThat(request.getMessages().get(1).getRole()).isEqualTo("user");
            assertThat(request.getTemperature()).isEqualTo(0.5);
            assertThat(request.getMaxTokens()).isEqualTo(100);
            assertThat(request.getTopP()).isEqualTo(0.9);
            assertThat(request.getN()).isEqualTo(1);
            assertThat(request.getStream()).isFalse();
            assertThat(request.getStop()).containsExactly("\n");
        }

        @Test
        @DisplayName("ChatCompletionRequest extractPrompt should concatenate messages")
        void testChatCompletionRequestExtractPrompt() {
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addSystemMessage("System message")
                .addUserMessage("User message")
                .build();

            String prompt = request.extractPrompt();
            assertThat(prompt).contains("System message");
            assertThat(prompt).contains("User message");
        }

        @Test
        @DisplayName("ChatCompletionRequest should require model")
        void testChatCompletionRequestRequiresModel() {
            assertThatThrownBy(() -> ChatCompletionRequest.builder()
                .addUserMessage("Test")
                .build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ChatCompletionResponse builder should work correctly")
        void testChatCompletionResponseBuilder() {
            ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("cmpl-123")
                .object("chat.completion")
                .created(1234567890L)
                .model("gpt-4")
                .choices(List.of(
                    new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant("Test response"),
                        "stop"
                    )
                ))
                .usage(new ChatCompletionResponse.Usage(10, 5, 15))
                .build();

            assertThat(response.getId()).isEqualTo("cmpl-123");
            assertThat(response.getObject()).isEqualTo("chat.completion");
            assertThat(response.getCreated()).isEqualTo(1234567890L);
            assertThat(response.getModel()).isEqualTo("gpt-4");
            assertThat(response.getChoices()).hasSize(1);
            assertThat(response.getContent()).isEqualTo("Test response");
            assertThat(response.getUsage().getPromptTokens()).isEqualTo(10);
            assertThat(response.getUsage().getCompletionTokens()).isEqualTo(5);
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(15);
        }

        @Test
        @DisplayName("ChatCompletionResponse getSummary should truncate long content")
        void testChatCompletionResponseGetSummary() {
            String longContent = "A".repeat(200);
            ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(List.of(
                    new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant(longContent),
                        "stop"
                    )
                ))
                .build();

            assertThat(response.getSummary()).hasSize(100);
        }

        @Test
        @DisplayName("ChatMessage factory methods should work correctly")
        void testChatMessageFactory() {
            ChatMessage system = ChatMessage.system("System prompt");
            assertThat(system.getRole()).isEqualTo("system");
            assertThat(system.getContent()).isEqualTo("System prompt");

            ChatMessage user = ChatMessage.user("User message");
            assertThat(user.getRole()).isEqualTo("user");
            assertThat(user.getContent()).isEqualTo("User message");

            ChatMessage assistant = ChatMessage.assistant("Assistant reply");
            assertThat(assistant.getRole()).isEqualTo("assistant");
            assertThat(assistant.getContent()).isEqualTo("Assistant reply");
        }

        @Test
        @DisplayName("Usage static factory should calculate total tokens")
        void testUsageStaticFactory() {
            ChatCompletionResponse.Usage usage = ChatCompletionResponse.Usage.of(100, 50);
            assertThat(usage.getPromptTokens()).isEqualTo(100);
            assertThat(usage.getCompletionTokens()).isEqualTo(50);
            assertThat(usage.getTotalTokens()).isEqualTo(150);
        }
    }

    @Nested
    @WireMockTest
    @DisplayName("Integration Tests")
    class IntegrationTests {

        private AxonFlow axonflow;
        private OpenAIInterceptor interceptor;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
            interceptor = OpenAIInterceptor.builder()
                .axonflow(axonflow)
                .userToken("test-user")
                .asyncAudit(false)
                .build();
        }

        @Test
        @DisplayName("Builder should require AxonFlow")
        void testBuilderRequiresAxonFlow() {
            assertThatThrownBy(() -> OpenAIInterceptor.builder().build())
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

            // Create mock OpenAI call
            Function<ChatCompletionRequest, ChatCompletionResponse> mockCall = request ->
                ChatCompletionResponse.builder()
                    .id("chatcmpl-123")
                    .model("gpt-4")
                    .choices(List.of(new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant("Hello! How can I help you?"),
                        "stop"
                    )))
                    .usage(ChatCompletionResponse.Usage.of(10, 20))
                    .build();

            // Create request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addUserMessage("Hello!")
                .temperature(0.7)
                .maxTokens(1024)
                .build();

            // Execute wrapped call
            ChatCompletionResponse response = interceptor.wrap(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("chatcmpl-123");
            assertThat(response.getContent()).isEqualTo("Hello! How can I help you?");

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
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Request blocked by policy: no-pii\"}")));

            // Create mock OpenAI call (should not be called)
            Function<ChatCompletionRequest, ChatCompletionResponse> mockCall = request -> {
                fail("OpenAI call should not be made when blocked");
                return null;
            };

            // Create request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addUserMessage("Tell me about John's SSN")
                .build();

            // Execute wrapped call
            assertThatThrownBy(() -> interceptor.wrap(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("no-pii");
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

            // Create mock async OpenAI call
            Function<ChatCompletionRequest, CompletableFuture<ChatCompletionResponse>> mockCall =
                request -> CompletableFuture.completedFuture(
                    ChatCompletionResponse.builder()
                        .id("chatcmpl-456")
                        .model("gpt-4")
                        .choices(List.of(new ChatCompletionResponse.Choice(
                            0,
                            ChatMessage.assistant("Async response"),
                            "stop"
                        )))
                        .usage(ChatCompletionResponse.Usage.of(5, 15))
                        .build()
                );

            // Create request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addUserMessage("Async test")
                .build();

            // Execute wrapped async call
            ChatCompletionResponse response = interceptor.wrapAsync(mockCall)
                .apply(request)
                .get();

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("chatcmpl-456");
            assertThat(response.getContent()).isEqualTo("Async response");
        }

        @Test
        @DisplayName("wrapAsync should throw when blocked by policy")
        void testWrapAsyncBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Content policy violation\"}")));

            // Create mock async OpenAI call (should not be called)
            Function<ChatCompletionRequest, CompletableFuture<ChatCompletionResponse>> mockCall =
                request -> {
                    fail("OpenAI call should not be made when blocked");
                    return null;
                };

            // Create request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addUserMessage("Blocked content")
                .build();

            // Execute wrapped async call
            CompletableFuture<ChatCompletionResponse> future = interceptor.wrapAsync(mockCall)
                .apply(request);

            assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PolicyViolationException.class);
        }

        @Test
        @DisplayName("static wrapChatCompletion should work")
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

            // Create mock OpenAI call
            Function<ChatCompletionRequest, ChatCompletionResponse> mockCall = request ->
                ChatCompletionResponse.builder()
                    .id("chatcmpl-789")
                    .model("gpt-4")
                    .build();

            // Use static wrapper
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .addUserMessage("Static test")
                .build();

            ChatCompletionResponse response = OpenAIInterceptor.wrapChatCompletion(
                axonflow, "user-token", mockCall
            ).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("chatcmpl-789");
        }
    }
}
