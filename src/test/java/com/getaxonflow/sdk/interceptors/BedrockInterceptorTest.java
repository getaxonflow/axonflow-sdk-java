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
import com.getaxonflow.sdk.interceptors.BedrockInterceptor.*;
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

@DisplayName("Bedrock Interceptor")
class BedrockInterceptorTest {

    @Nested
    @DisplayName("Type Tests")
    class TypeTests {

        @Test
        @DisplayName("Model ID constants should be defined")
        void testModelIdConstants() {
            assertThat(BedrockInterceptor.CLAUDE_3_OPUS).isEqualTo("anthropic.claude-3-opus-20240229-v1:0");
            assertThat(BedrockInterceptor.CLAUDE_3_SONNET).isEqualTo("anthropic.claude-3-sonnet-20240229-v1:0");
            assertThat(BedrockInterceptor.CLAUDE_3_HAIKU).isEqualTo("anthropic.claude-3-haiku-20240307-v1:0");
            assertThat(BedrockInterceptor.CLAUDE_2).isEqualTo("anthropic.claude-v2:1");
            assertThat(BedrockInterceptor.TITAN_TEXT_EXPRESS).isEqualTo("amazon.titan-text-express-v1");
            assertThat(BedrockInterceptor.TITAN_TEXT_LITE).isEqualTo("amazon.titan-text-lite-v1");
            assertThat(BedrockInterceptor.LLAMA2_70B).isEqualTo("meta.llama2-70b-chat-v1");
            assertThat(BedrockInterceptor.LLAMA3_70B).isEqualTo("meta.llama3-70b-instruct-v1:0");
        }

        @Test
        @DisplayName("ClaudeMessage factory methods should work correctly")
        void testClaudeMessageFactory() {
            ClaudeMessage user = ClaudeMessage.user("User message");
            assertThat(user.getRole()).isEqualTo("user");
            assertThat(user.getContent()).isEqualTo("User message");

            ClaudeMessage assistant = ClaudeMessage.assistant("Assistant reply");
            assertThat(assistant.getRole()).isEqualTo("assistant");
            assertThat(assistant.getContent()).isEqualTo("Assistant reply");
        }

        @Test
        @DisplayName("ClaudeMessage constructor and setters")
        void testClaudeMessageConstructorSetters() {
            ClaudeMessage message = new ClaudeMessage();
            message.setRole("user");
            message.setContent("Hello");

            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isEqualTo("Hello");

            // Test constructor with role and content
            ClaudeMessage message2 = new ClaudeMessage("assistant", "Response");
            assertThat(message2.getRole()).isEqualTo("assistant");
            assertThat(message2.getContent()).isEqualTo("Response");
        }

        @Test
        @DisplayName("BedrockInvokeRequest forClaude should work correctly")
        void testBedrockInvokeRequestForClaude() {
            List<ClaudeMessage> messages = List.of(
                ClaudeMessage.user("Hello"),
                ClaudeMessage.assistant("Hi there!")
            );

            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_3_SONNET,
                messages,
                1024
            );

            assertThat(request.getModelId()).isEqualTo("anthropic.claude-3-sonnet-20240229-v1:0");
            assertThat(request.getMessages()).hasSize(2);
        }

        @Test
        @DisplayName("BedrockInvokeRequest forTitan should work correctly")
        void testBedrockInvokeRequestForTitan() {
            BedrockInvokeRequest request = BedrockInvokeRequest.forTitan(
                BedrockInterceptor.TITAN_TEXT_EXPRESS,
                "Generate some text"
            );

            assertThat(request.getModelId()).isEqualTo("amazon.titan-text-express-v1");
            assertThat(request.getInputText()).isEqualTo("Generate some text");
        }

        @Test
        @DisplayName("BedrockInvokeRequest extractPrompt should handle Claude messages")
        void testBedrockInvokeRequestExtractPromptClaude() {
            List<ClaudeMessage> messages = List.of(
                ClaudeMessage.user("First message"),
                ClaudeMessage.user("Second message")
            );

            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_3_HAIKU,
                messages,
                500
            );

            String prompt = request.extractPrompt();
            assertThat(prompt).contains("First message");
            assertThat(prompt).contains("Second message");
        }

        @Test
        @DisplayName("BedrockInvokeRequest extractPrompt should handle Titan inputText")
        void testBedrockInvokeRequestExtractPromptTitan() {
            BedrockInvokeRequest request = BedrockInvokeRequest.forTitan(
                BedrockInterceptor.TITAN_TEXT_LITE,
                "Titan prompt"
            );

            assertThat(request.extractPrompt()).isEqualTo("Titan prompt");
        }

        @Test
        @DisplayName("BedrockInvokeRequest extractPrompt should handle empty state")
        void testBedrockInvokeRequestExtractPromptEmpty() {
            BedrockInvokeRequest request = new BedrockInvokeRequest();
            assertThat(request.extractPrompt()).isEmpty();
        }

        @Test
        @DisplayName("BedrockInvokeRequest setters should work correctly")
        void testBedrockInvokeRequestSetters() {
            BedrockInvokeRequest request = new BedrockInvokeRequest();
            request.setModelId("test-model");
            request.setBody("{\"test\":true}");
            request.setContentType("application/json");
            request.setAccept("application/json");
            request.setMessages(List.of(ClaudeMessage.user("Test")));
            request.setInputText("Test input");

            assertThat(request.getModelId()).isEqualTo("test-model");
            assertThat(request.getBody()).isEqualTo("{\"test\":true}");
            assertThat(request.getContentType()).isEqualTo("application/json");
            assertThat(request.getAccept()).isEqualTo("application/json");
            assertThat(request.getMessages()).hasSize(1);
            assertThat(request.getInputText()).isEqualTo("Test input");
        }

        @Test
        @DisplayName("BedrockInvokeResponse getSummary should work correctly")
        void testBedrockInvokeResponseGetSummary() {
            BedrockInvokeResponse response = new BedrockInvokeResponse();
            response.setResponseText("Short response");

            assertThat(response.getSummary()).isEqualTo("Short response");
        }

        @Test
        @DisplayName("BedrockInvokeResponse getSummary should truncate long text")
        void testBedrockInvokeResponseGetSummaryTruncate() {
            BedrockInvokeResponse response = new BedrockInvokeResponse();
            response.setResponseText("A".repeat(150));

            String summary = response.getSummary();
            assertThat(summary).hasSize(103); // 100 + "..."
            assertThat(summary).endsWith("...");
        }

        @Test
        @DisplayName("BedrockInvokeResponse getSummary should handle empty/null")
        void testBedrockInvokeResponseGetSummaryEmpty() {
            BedrockInvokeResponse response = new BedrockInvokeResponse();
            assertThat(response.getSummary()).isEmpty();

            response.setResponseText("");
            assertThat(response.getSummary()).isEmpty();
        }

        @Test
        @DisplayName("BedrockInvokeResponse setters should work correctly")
        void testBedrockInvokeResponseSetters() {
            BedrockInvokeResponse response = new BedrockInvokeResponse();
            response.setBody(new byte[]{1, 2, 3});
            response.setContentType("application/json");
            response.setResponseText("Response text");
            response.setInputTokens(100);
            response.setOutputTokens(50);

            assertThat(response.getBody()).hasSize(3);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getResponseText()).isEqualTo("Response text");
            assertThat(response.getInputTokens()).isEqualTo(100);
            assertThat(response.getOutputTokens()).isEqualTo(50);
        }
    }

    @Nested
    @WireMockTest
    @DisplayName("Integration Tests")
    class IntegrationTests {

        private AxonFlow axonflow;
        private BedrockInterceptor interceptor;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
            interceptor = new BedrockInterceptor(axonflow, "test-user");
        }

        @Test
        @DisplayName("Constructor should reject null AxonFlow")
        void testConstructorRejectsNullAxonFlow() {
            assertThatThrownBy(() -> new BedrockInterceptor(null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("axonflow cannot be null");
        }

        @Test
        @DisplayName("Constructor should reject null userToken")
        void testConstructorRejectsNullUserToken() {
            assertThatThrownBy(() -> new BedrockInterceptor(axonflow, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userToken cannot be null or empty");
        }

        @Test
        @DisplayName("Constructor should reject empty userToken")
        void testConstructorRejectsEmptyUserToken() {
            assertThatThrownBy(() -> new BedrockInterceptor(axonflow, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userToken cannot be null or empty");
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
                    .withBody("{\"success\":true}")));

            // Create mock Bedrock call
            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> {
                BedrockInvokeResponse response = new BedrockInvokeResponse();
                response.setResponseText("Hello from Bedrock!");
                response.setInputTokens(10);
                response.setOutputTokens(5);
                return response;
            };

            // Create request
            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_3_SONNET,
                List.of(ClaudeMessage.user("Hello!")),
                1024
            );

            // Execute wrapped call
            BedrockInvokeResponse response = interceptor.wrap(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getResponseText()).isEqualTo("Hello from Bedrock!");

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
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Policy violation: no-pii\"}")));

            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> {
                fail("Bedrock call should not be made when blocked");
                return null;
            };

            BedrockInvokeRequest request = BedrockInvokeRequest.forTitan(
                BedrockInterceptor.TITAN_TEXT_EXPRESS,
                "Blocked content"
            );

            assertThatThrownBy(() -> interceptor.wrap(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("no-pii");
        }

        @Test
        @DisplayName("wrap should work with Titan model")
        void testWrapWithTitanModel() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-titan\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> {
                BedrockInvokeResponse response = new BedrockInvokeResponse();
                response.setResponseText("Titan response");
                return response;
            };

            BedrockInvokeRequest request = BedrockInvokeRequest.forTitan(
                BedrockInterceptor.TITAN_TEXT_EXPRESS,
                "Hello Titan"
            );

            BedrockInvokeResponse response = interceptor.wrap(mockCall).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getResponseText()).isEqualTo("Titan response");
        }

        @Test
        @DisplayName("wrapAsync should allow request when not blocked")
        void testWrapAsyncAllowedRequest() throws Exception {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-async\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            // Create mock async Bedrock call
            Function<BedrockInvokeRequest, CompletableFuture<BedrockInvokeResponse>> mockCall = request -> {
                BedrockInvokeResponse response = new BedrockInvokeResponse();
                response.setResponseText("Async Bedrock response");
                response.setInputTokens(15);
                response.setOutputTokens(25);
                return CompletableFuture.completedFuture(response);
            };

            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_3_HAIKU,
                List.of(ClaudeMessage.user("Async test")),
                512
            );

            BedrockInvokeResponse response = interceptor.wrapAsync(mockCall)
                .apply(request)
                .get();

            assertThat(response).isNotNull();
            assertThat(response.getResponseText()).isEqualTo("Async Bedrock response");
        }

        @Test
        @DisplayName("wrapAsync should throw when blocked by policy")
        void testWrapAsyncBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Async policy violation\"}")));

            Function<BedrockInvokeRequest, CompletableFuture<BedrockInvokeResponse>> mockCall = request -> {
                fail("Bedrock call should not be made when blocked");
                return null;
            };

            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_3_OPUS,
                List.of(ClaudeMessage.user("Blocked async")),
                1024
            );

            // Execute wrapped async call - should return failed future or throw
            try {
                CompletableFuture<BedrockInvokeResponse> future = interceptor.wrapAsync(mockCall)
                    .apply(request);

                // If we get a future, it should be failed
                assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(PolicyViolationException.class);
            } catch (PolicyViolationException e) {
                // Some implementations may throw directly
                assertThat(e.getMessage()).contains("Async policy violation");
            }
        }

        @Test
        @DisplayName("wrap should handle null response")
        void testWrapNullResponse() {
            // Stub policy check - allowed with no plan_id
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> null;

            BedrockInvokeRequest request = BedrockInvokeRequest.forTitan(
                BedrockInterceptor.TITAN_TEXT_LITE,
                "Test"
            );

            BedrockInvokeResponse response = interceptor.wrap(mockCall).apply(request);
            assertThat(response).isNull();
        }

        @Test
        @DisplayName("wrap should handle response with long summary")
        void testWrapLongResponseSummary() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-long\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> {
                BedrockInvokeResponse response = new BedrockInvokeResponse();
                response.setResponseText("X".repeat(200));
                return response;
            };

            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.CLAUDE_2,
                List.of(ClaudeMessage.user("Test")),
                100
            );

            BedrockInvokeResponse response = interceptor.wrap(mockCall).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getResponseText()).hasSize(200);
            assertThat(response.getSummary()).hasSize(103); // truncated
        }

        @Test
        @DisplayName("wrap should work with Llama models")
        void testWrapWithLlamaModel() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-llama\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            Function<BedrockInvokeRequest, BedrockInvokeResponse> mockCall = request -> {
                BedrockInvokeResponse response = new BedrockInvokeResponse();
                response.setResponseText("Llama response");
                return response;
            };

            // Llama uses same message format as Claude in Bedrock
            BedrockInvokeRequest request = BedrockInvokeRequest.forClaude(
                BedrockInterceptor.LLAMA3_70B,
                List.of(ClaudeMessage.user("Hello Llama")),
                1024
            );

            BedrockInvokeResponse response = interceptor.wrap(mockCall).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getResponseText()).isEqualTo("Llama response");
        }
    }
}
