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
import com.getaxonflow.sdk.interceptors.OllamaInterceptor.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Ollama Interceptor")
class OllamaInterceptorTest {

    @Nested
    @DisplayName("Type Tests")
    class TypeTests {

        @Test
        @DisplayName("OllamaMessage factory methods should work correctly")
        void testOllamaMessageFactory() {
            OllamaMessage user = OllamaMessage.user("User message");
            assertThat(user.getRole()).isEqualTo("user");
            assertThat(user.getContent()).isEqualTo("User message");

            OllamaMessage assistant = OllamaMessage.assistant("Assistant reply");
            assertThat(assistant.getRole()).isEqualTo("assistant");
            assertThat(assistant.getContent()).isEqualTo("Assistant reply");

            OllamaMessage system = OllamaMessage.system("System prompt");
            assertThat(system.getRole()).isEqualTo("system");
            assertThat(system.getContent()).isEqualTo("System prompt");
        }

        @Test
        @DisplayName("OllamaMessage constructor and setters")
        void testOllamaMessageConstructorSetters() {
            OllamaMessage message = new OllamaMessage();
            message.setRole("user");
            message.setContent("Hello");
            message.setImages(List.of("base64image"));

            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isEqualTo("Hello");
            assertThat(message.getImages()).containsExactly("base64image");

            // Test constructor with role and content
            OllamaMessage message2 = new OllamaMessage("assistant", "Response");
            assertThat(message2.getRole()).isEqualTo("assistant");
            assertThat(message2.getContent()).isEqualTo("Response");
        }

        @Test
        @DisplayName("OllamaChatRequest create should work correctly")
        void testOllamaChatRequestCreate() {
            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Hello!");

            assertThat(request.getModel()).isEqualTo("llama2");
            assertThat(request.getMessages()).hasSize(1);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("user");
            assertThat(request.getMessages().get(0).getContent()).isEqualTo("Hello!");
        }

        @Test
        @DisplayName("OllamaChatRequest extractPrompt should concatenate messages")
        void testOllamaChatRequestExtractPrompt() {
            OllamaChatRequest request = new OllamaChatRequest();
            request.setModel("llama2");

            List<OllamaMessage> messages = new ArrayList<>();
            messages.add(OllamaMessage.system("You are helpful"));
            messages.add(OllamaMessage.user("Hello"));
            request.setMessages(messages);

            String prompt = request.extractPrompt();
            assertThat(prompt).contains("You are helpful");
            assertThat(prompt).contains("Hello");
        }

        @Test
        @DisplayName("OllamaChatRequest extractPrompt should handle empty messages")
        void testOllamaChatRequestExtractPromptEmpty() {
            OllamaChatRequest request = new OllamaChatRequest();
            assertThat(request.extractPrompt()).isEmpty();

            request.setMessages(null);
            assertThat(request.extractPrompt()).isEmpty();
        }

        @Test
        @DisplayName("OllamaChatRequest setters should work correctly")
        void testOllamaChatRequestSetters() {
            OllamaChatRequest request = new OllamaChatRequest();
            request.setModel("mistral");
            request.setStream(true);
            request.setFormat("json");

            OllamaOptions options = new OllamaOptions();
            options.setTemperature(0.8);
            request.setOptions(options);

            assertThat(request.getModel()).isEqualTo("mistral");
            assertThat(request.isStream()).isTrue();
            assertThat(request.getFormat()).isEqualTo("json");
            assertThat(request.getOptions()).isNotNull();
            assertThat(request.getOptions().getTemperature()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("OllamaOptions setters should work correctly")
        void testOllamaOptionsSetters() {
            OllamaOptions options = new OllamaOptions();
            options.setTemperature(0.7);
            options.setTopP(0.9);
            options.setTopK(40);
            options.setNumPredict(100);
            options.setStop(List.of("END", "STOP"));

            assertThat(options.getTemperature()).isEqualTo(0.7);
            assertThat(options.getTopP()).isEqualTo(0.9);
            assertThat(options.getTopK()).isEqualTo(40);
            assertThat(options.getNumPredict()).isEqualTo(100);
            assertThat(options.getStop()).containsExactly("END", "STOP");
        }

        @Test
        @DisplayName("OllamaChatResponse setters should work correctly")
        void testOllamaChatResponseSetters() {
            OllamaChatResponse response = new OllamaChatResponse();
            response.setModel("llama2");
            response.setCreatedAt("2024-01-15T10:30:00Z");
            response.setMessage(OllamaMessage.assistant("Hello!"));
            response.setDone(true);
            response.setTotalDuration(1000000000L);
            response.setLoadDuration(100000000L);
            response.setPromptEvalCount(10);
            response.setPromptEvalDuration(50000000L);
            response.setEvalCount(20);
            response.setEvalDuration(500000000L);

            assertThat(response.getModel()).isEqualTo("llama2");
            assertThat(response.getCreatedAt()).isEqualTo("2024-01-15T10:30:00Z");
            assertThat(response.getMessage().getContent()).isEqualTo("Hello!");
            assertThat(response.isDone()).isTrue();
            assertThat(response.getTotalDuration()).isEqualTo(1000000000L);
            assertThat(response.getLoadDuration()).isEqualTo(100000000L);
            assertThat(response.getPromptEvalCount()).isEqualTo(10);
            assertThat(response.getPromptEvalDuration()).isEqualTo(50000000L);
            assertThat(response.getEvalCount()).isEqualTo(20);
            assertThat(response.getEvalDuration()).isEqualTo(500000000L);
        }

        @Test
        @DisplayName("OllamaGenerateRequest create should work correctly")
        void testOllamaGenerateRequestCreate() {
            OllamaGenerateRequest request = OllamaGenerateRequest.create("llama2", "Tell me a joke");

            assertThat(request.getModel()).isEqualTo("llama2");
            assertThat(request.getPrompt()).isEqualTo("Tell me a joke");
        }

        @Test
        @DisplayName("OllamaGenerateRequest setters should work correctly")
        void testOllamaGenerateRequestSetters() {
            OllamaGenerateRequest request = new OllamaGenerateRequest();
            request.setModel("codellama");
            request.setPrompt("Write a function");
            request.setStream(false);
            request.setFormat("json");

            OllamaOptions options = new OllamaOptions();
            options.setTemperature(0.2);
            request.setOptions(options);

            assertThat(request.getModel()).isEqualTo("codellama");
            assertThat(request.getPrompt()).isEqualTo("Write a function");
            assertThat(request.isStream()).isFalse();
            assertThat(request.getFormat()).isEqualTo("json");
            assertThat(request.getOptions().getTemperature()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("OllamaGenerateResponse setters should work correctly")
        void testOllamaGenerateResponseSetters() {
            OllamaGenerateResponse response = new OllamaGenerateResponse();
            response.setModel("llama2");
            response.setCreatedAt("2024-01-15T10:30:00Z");
            response.setResponse("Here is your response");
            response.setDone(true);
            response.setTotalDuration(2000000000L);
            response.setLoadDuration(200000000L);
            response.setPromptEvalCount(15);
            response.setPromptEvalDuration(100000000L);
            response.setEvalCount(30);
            response.setEvalDuration(800000000L);

            assertThat(response.getModel()).isEqualTo("llama2");
            assertThat(response.getCreatedAt()).isEqualTo("2024-01-15T10:30:00Z");
            assertThat(response.getResponse()).isEqualTo("Here is your response");
            assertThat(response.isDone()).isTrue();
            assertThat(response.getTotalDuration()).isEqualTo(2000000000L);
            assertThat(response.getLoadDuration()).isEqualTo(200000000L);
            assertThat(response.getPromptEvalCount()).isEqualTo(15);
            assertThat(response.getPromptEvalDuration()).isEqualTo(100000000L);
            assertThat(response.getEvalCount()).isEqualTo(30);
            assertThat(response.getEvalDuration()).isEqualTo(800000000L);
        }
    }

    @Nested
    @WireMockTest
    @DisplayName("Integration Tests")
    class IntegrationTests {

        private AxonFlow axonflow;
        private OllamaInterceptor interceptor;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
            interceptor = new OllamaInterceptor(axonflow, "test-user");
        }

        @Test
        @DisplayName("Constructor should reject null AxonFlow")
        void testConstructorRejectsNullAxonFlow() {
            assertThatThrownBy(() -> new OllamaInterceptor(null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("axonflow cannot be null");
        }

        @Test
        @DisplayName("Constructor should reject null userToken")
        void testConstructorRejectsNullUserToken() {
            assertThatThrownBy(() -> new OllamaInterceptor(axonflow, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userToken cannot be null or empty");
        }

        @Test
        @DisplayName("Constructor should reject empty userToken")
        void testConstructorRejectsEmptyUserToken() {
            assertThatThrownBy(() -> new OllamaInterceptor(axonflow, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userToken cannot be null or empty");
        }

        @Test
        @DisplayName("wrapChat should allow request when not blocked")
        void testWrapChatAllowedRequest() {
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

            // Create mock Ollama call
            Function<OllamaChatRequest, OllamaChatResponse> mockCall = request -> {
                OllamaChatResponse response = new OllamaChatResponse();
                response.setModel("llama2");
                response.setMessage(OllamaMessage.assistant("Hello from Ollama!"));
                response.setDone(true);
                response.setPromptEvalCount(10);
                response.setEvalCount(15);
                return response;
            };

            // Create request
            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Hello!");

            // Execute wrapped call
            OllamaChatResponse response = interceptor.wrapChat(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getMessage().getContent()).isEqualTo("Hello from Ollama!");

            verify(postRequestedFor(urlEqualTo("/api/request")));
        }

        @Test
        @DisplayName("wrapChat should throw when blocked by policy")
        void testWrapChatBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Policy violation\"}")));

            Function<OllamaChatRequest, OllamaChatResponse> mockCall = request -> {
                fail("Ollama call should not be made when blocked");
                return null;
            };

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Blocked content");

            assertThatThrownBy(() -> interceptor.wrapChat(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Policy violation");
        }

        @Test
        @DisplayName("wrapGenerate should allow request when not blocked")
        void testWrapGenerateAllowedRequest() {
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
                    .withBody("{\"success\":true}")));

            // Create mock Ollama generate call
            Function<OllamaGenerateRequest, OllamaGenerateResponse> mockCall = request -> {
                OllamaGenerateResponse response = new OllamaGenerateResponse();
                response.setModel("llama2");
                response.setResponse("Generated text");
                response.setDone(true);
                response.setPromptEvalCount(5);
                response.setEvalCount(10);
                return response;
            };

            // Create request
            OllamaGenerateRequest request = OllamaGenerateRequest.create("llama2", "Generate something");

            // Execute wrapped call
            OllamaGenerateResponse response = interceptor.wrapGenerate(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getResponse()).isEqualTo("Generated text");

            verify(postRequestedFor(urlEqualTo("/api/request")));
        }

        @Test
        @DisplayName("wrapGenerate should throw when blocked by policy")
        void testWrapGenerateBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Content blocked\"}")));

            Function<OllamaGenerateRequest, OllamaGenerateResponse> mockCall = request -> {
                fail("Ollama call should not be made when blocked");
                return null;
            };

            OllamaGenerateRequest request = OllamaGenerateRequest.create("llama2", "Blocked prompt");

            assertThatThrownBy(() -> interceptor.wrapGenerate(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Content blocked");
        }

        @Test
        @DisplayName("wrapChatAsync should allow request when not blocked")
        void testWrapChatAsyncAllowedRequest() throws Exception {
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
                    .withBody("{\"success\":true}")));

            // Create mock async Ollama call
            Function<OllamaChatRequest, CompletableFuture<OllamaChatResponse>> mockCall = request -> {
                OllamaChatResponse response = new OllamaChatResponse();
                response.setModel("llama2");
                response.setMessage(OllamaMessage.assistant("Async response"));
                response.setDone(true);
                return CompletableFuture.completedFuture(response);
            };

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Async test");

            OllamaChatResponse response = interceptor.wrapChatAsync(mockCall)
                .apply(request)
                .get();

            assertThat(response).isNotNull();
            assertThat(response.getMessage().getContent()).isEqualTo("Async response");
        }

        @Test
        @DisplayName("wrapChatAsync should throw when blocked by policy")
        void testWrapChatAsyncBlockedRequest() {
            // Stub policy check - blocked
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"blocked\":true,\"block_reason\":\"Async policy violation\"}")));

            Function<OllamaChatRequest, CompletableFuture<OllamaChatResponse>> mockCall = request -> {
                fail("Ollama call should not be made when blocked");
                return null;
            };

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Blocked async");

            // Execute wrapped async call - should return failed future or throw
            try {
                CompletableFuture<OllamaChatResponse> future = interceptor.wrapChatAsync(mockCall)
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
        @DisplayName("wrapChat should handle long response summaries")
        void testWrapChatLongResponseSummary() {
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

            // Create mock call with long response
            Function<OllamaChatRequest, OllamaChatResponse> mockCall = request -> {
                OllamaChatResponse response = new OllamaChatResponse();
                response.setModel("llama2");
                response.setMessage(OllamaMessage.assistant("A".repeat(200)));
                return response;
            };

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Test");

            // Execute - summary truncation happens in audit
            OllamaChatResponse response = interceptor.wrapChat(mockCall).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage().getContent()).hasSize(200);
        }

        @Test
        @DisplayName("wrapGenerate should handle long response summaries")
        void testWrapGenerateLongResponseSummary() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-long-gen\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            // Create mock call with long response
            Function<OllamaGenerateRequest, OllamaGenerateResponse> mockCall = request -> {
                OllamaGenerateResponse response = new OllamaGenerateResponse();
                response.setModel("llama2");
                response.setResponse("B".repeat(200));
                return response;
            };

            OllamaGenerateRequest request = OllamaGenerateRequest.create("llama2", "Test");

            OllamaGenerateResponse response = interceptor.wrapGenerate(mockCall).apply(request);

            assertThat(response).isNotNull();
            assertThat(response.getResponse()).hasSize(200);
        }

        @Test
        @DisplayName("wrapChat should handle null response")
        void testWrapChatNullResponse() {
            // Stub policy check - allowed with no plan_id
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

            Function<OllamaChatRequest, OllamaChatResponse> mockCall = request -> null;

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Test");

            OllamaChatResponse response = interceptor.wrapChat(mockCall).apply(request);
            assertThat(response).isNull();
        }

        @Test
        @DisplayName("wrapChat should handle null message in response")
        void testWrapChatNullMessage() {
            // Stub policy check - allowed
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false,\"plan_id\":\"plan-null\"}")));

            // Stub audit call
            stubFor(post(urlEqualTo("/api/audit"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\"success\":true}")));

            Function<OllamaChatRequest, OllamaChatResponse> mockCall = request -> {
                OllamaChatResponse response = new OllamaChatResponse();
                response.setModel("llama2");
                response.setMessage(null);
                return response;
            };

            OllamaChatRequest request = OllamaChatRequest.create("llama2", "Test");

            OllamaChatResponse response = interceptor.wrapChat(mockCall).apply(request);
            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isNull();
        }
    }
}
