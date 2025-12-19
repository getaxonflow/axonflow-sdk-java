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
import com.getaxonflow.sdk.interceptors.GeminiInterceptor.*;
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

@DisplayName("Gemini Interceptor")
class GeminiInterceptorTest {

    @Nested
    @DisplayName("Type Tests")
    class TypeTests {

        @Test
        @DisplayName("GeminiRequest create should work correctly")
        void testGeminiRequestCreate() {
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Hello, world!");

            assertThat(request.getModel()).isEqualTo("gemini-pro");
            assertThat(request.getContents()).hasSize(1);
            assertThat(request.extractPrompt()).isEqualTo("Hello, world!");
        }

        @Test
        @DisplayName("GeminiRequest extractPrompt should handle empty contents")
        void testGeminiRequestExtractPromptEmpty() {
            GeminiRequest request = new GeminiRequest();
            assertThat(request.extractPrompt()).isEmpty();
        }

        @Test
        @DisplayName("GeminiRequest extractPrompt should concatenate multiple parts")
        void testGeminiRequestExtractPromptMultipleParts() {
            GeminiRequest request = new GeminiRequest();
            request.setModel("gemini-pro");

            List<Content> contents = new ArrayList<>();
            contents.add(Content.text("First part"));
            contents.add(Content.text("Second part"));
            request.setContents(contents);

            String prompt = request.extractPrompt();
            assertThat(prompt).contains("First part");
            assertThat(prompt).contains("Second part");
        }

        @Test
        @DisplayName("GeminiRequest with GenerationConfig")
        void testGeminiRequestWithGenerationConfig() {
            GeminiRequest request = new GeminiRequest();
            request.setModel("gemini-pro");

            GenerationConfig config = new GenerationConfig();
            config.setTemperature(0.7);
            config.setTopP(0.9);
            config.setTopK(40);
            config.setMaxOutputTokens(1024);
            config.setStopSequences(List.of("END"));
            request.setGenerationConfig(config);

            assertThat(request.getGenerationConfig()).isNotNull();
            assertThat(request.getGenerationConfig().getTemperature()).isEqualTo(0.7);
            assertThat(request.getGenerationConfig().getTopP()).isEqualTo(0.9);
            assertThat(request.getGenerationConfig().getTopK()).isEqualTo(40);
            assertThat(request.getGenerationConfig().getMaxOutputTokens()).isEqualTo(1024);
            assertThat(request.getGenerationConfig().getStopSequences()).containsExactly("END");
        }

        @Test
        @DisplayName("Content text factory method should work correctly")
        void testContentTextFactory() {
            Content content = Content.text("Test message");

            assertThat(content.getRole()).isEqualTo("user");
            assertThat(content.getParts()).hasSize(1);
            assertThat(content.getParts().get(0).getText()).isEqualTo("Test message");
        }

        @Test
        @DisplayName("Content setters should work correctly")
        void testContentSetters() {
            Content content = new Content();
            content.setRole("assistant");

            List<Part> parts = new ArrayList<>();
            parts.add(Part.text("Response"));
            content.setParts(parts);

            assertThat(content.getRole()).isEqualTo("assistant");
            assertThat(content.getParts()).hasSize(1);
        }

        @Test
        @DisplayName("Part text factory method should work correctly")
        void testPartTextFactory() {
            Part part = Part.text("Hello");
            assertThat(part.getText()).isEqualTo("Hello");
            assertThat(part.getInlineData()).isNull();
        }

        @Test
        @DisplayName("Part with InlineData")
        void testPartWithInlineData() {
            Part part = new Part();
            InlineData inlineData = new InlineData();
            inlineData.setMimeType("image/png");
            inlineData.setData("base64data");
            part.setInlineData(inlineData);
            part.setText(null);

            assertThat(part.getText()).isNull();
            assertThat(part.getInlineData()).isNotNull();
            assertThat(part.getInlineData().getMimeType()).isEqualTo("image/png");
            assertThat(part.getInlineData().getData()).isEqualTo("base64data");
        }

        @Test
        @DisplayName("GeminiResponse getText should extract first candidate text")
        void testGeminiResponseGetText() {
            GeminiResponse response = new GeminiResponse();

            Candidate candidate = new Candidate();
            Content content = new Content();
            List<Part> parts = new ArrayList<>();
            parts.add(Part.text("Response text"));
            content.setParts(parts);
            candidate.setContent(content);
            candidate.setFinishReason("STOP");

            response.setCandidates(List.of(candidate));

            assertThat(response.getText()).isEqualTo("Response text");
        }

        @Test
        @DisplayName("GeminiResponse getText should handle empty candidates")
        void testGeminiResponseGetTextEmpty() {
            GeminiResponse response = new GeminiResponse();
            assertThat(response.getText()).isEmpty();

            response.setCandidates(new ArrayList<>());
            assertThat(response.getText()).isEmpty();
        }

        @Test
        @DisplayName("GeminiResponse getText should handle null content")
        void testGeminiResponseGetTextNullContent() {
            GeminiResponse response = new GeminiResponse();
            Candidate candidate = new Candidate();
            candidate.setContent(null);
            response.setCandidates(List.of(candidate));

            assertThat(response.getText()).isEmpty();
        }

        @Test
        @DisplayName("GeminiResponse getSummary should truncate long text")
        void testGeminiResponseGetSummary() {
            GeminiResponse response = new GeminiResponse();

            Candidate candidate = new Candidate();
            Content content = new Content();
            List<Part> parts = new ArrayList<>();
            parts.add(Part.text("A".repeat(150)));
            content.setParts(parts);
            candidate.setContent(content);
            response.setCandidates(List.of(candidate));

            String summary = response.getSummary();
            assertThat(summary).hasSize(103); // 100 + "..."
            assertThat(summary).endsWith("...");
        }

        @Test
        @DisplayName("GeminiResponse with UsageMetadata")
        void testGeminiResponseWithUsageMetadata() {
            GeminiResponse response = new GeminiResponse();

            UsageMetadata metadata = new UsageMetadata();
            metadata.setPromptTokenCount(100);
            metadata.setCandidatesTokenCount(50);
            metadata.setTotalTokenCount(150);
            response.setUsageMetadata(metadata);

            assertThat(response.getPromptTokenCount()).isEqualTo(100);
            assertThat(response.getCandidatesTokenCount()).isEqualTo(50);
            assertThat(response.getTotalTokenCount()).isEqualTo(150);
        }

        @Test
        @DisplayName("GeminiResponse token counts should be 0 when no metadata")
        void testGeminiResponseNoMetadata() {
            GeminiResponse response = new GeminiResponse();

            assertThat(response.getPromptTokenCount()).isZero();
            assertThat(response.getCandidatesTokenCount()).isZero();
            assertThat(response.getTotalTokenCount()).isZero();
        }

        @Test
        @DisplayName("Candidate getters and setters")
        void testCandidateGettersSetters() {
            Candidate candidate = new Candidate();
            Content content = Content.text("Test");
            candidate.setContent(content);
            candidate.setFinishReason("STOP");

            assertThat(candidate.getContent()).isEqualTo(content);
            assertThat(candidate.getFinishReason()).isEqualTo("STOP");
        }
    }

    @Nested
    @WireMockTest
    @DisplayName("Integration Tests")
    class IntegrationTests {

        private AxonFlow axonflow;
        private GeminiInterceptor interceptor;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
            interceptor = new GeminiInterceptor(axonflow, "test-user");
        }

        @Test
        @DisplayName("Constructor should reject null AxonFlow")
        void testConstructorRejectsNullAxonFlow() {
            assertThatThrownBy(() -> new GeminiInterceptor(null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("axonflow cannot be null");
        }

        @Test
        @DisplayName("Constructor should reject null userToken")
        void testConstructorRejectsNullUserToken() {
            assertThatThrownBy(() -> new GeminiInterceptor(axonflow, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userToken cannot be null or empty");
        }

        @Test
        @DisplayName("Constructor should reject empty userToken")
        void testConstructorRejectsEmptyUserToken() {
            assertThatThrownBy(() -> new GeminiInterceptor(axonflow, ""))
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
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true}")));

            // Create mock Gemini call
            Function<GeminiRequest, GeminiResponse> mockCall = request -> {
                GeminiResponse response = new GeminiResponse();
                Candidate candidate = new Candidate();
                Content content = Content.text("Hello from Gemini!");
                candidate.setContent(content);
                response.setCandidates(List.of(candidate));

                UsageMetadata metadata = new UsageMetadata();
                metadata.setPromptTokenCount(10);
                metadata.setCandidatesTokenCount(5);
                response.setUsageMetadata(metadata);

                return response;
            };

            // Create request
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Hello!");

            // Execute wrapped call
            GeminiResponse response = interceptor.wrap(mockCall).apply(request);

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getText()).isEqualTo("Hello from Gemini!");

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

            // Create mock Gemini call (should not be called)
            Function<GeminiRequest, GeminiResponse> mockCall = request -> {
                fail("Gemini call should not be made when blocked");
                return null;
            };

            // Create request
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Tell me about SSN");

            // Execute wrapped call
            assertThatThrownBy(() -> interceptor.wrap(mockCall).apply(request))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("no-pii");
        }

        @Test
        @DisplayName("wrap should include generation config in context")
        void testWrapWithGenerationConfig() {
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

            // Create request with generation config
            GeminiRequest request = new GeminiRequest();
            request.setModel("gemini-pro");
            request.setContents(List.of(Content.text("Hello")));

            GenerationConfig config = new GenerationConfig();
            config.setTemperature(0.5);
            config.setMaxOutputTokens(500);
            request.setGenerationConfig(config);

            // Create mock call
            Function<GeminiRequest, GeminiResponse> mockCall = req -> new GeminiResponse();

            // Execute
            GeminiResponse response = interceptor.wrap(mockCall).apply(request);

            assertThat(response).isNotNull();
            verify(postRequestedFor(urlEqualTo("/api/request")));
        }

        @Test
        @DisplayName("wrapAsync should allow request when not blocked")
        void testWrapAsyncAllowedRequest() throws Exception {
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

            // Create mock async Gemini call
            Function<GeminiRequest, CompletableFuture<GeminiResponse>> mockCall = request -> {
                GeminiResponse response = new GeminiResponse();
                Candidate candidate = new Candidate();
                Content content = Content.text("Async response");
                candidate.setContent(content);
                response.setCandidates(List.of(candidate));
                return CompletableFuture.completedFuture(response);
            };

            // Create request
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Async test");

            // Execute wrapped async call
            GeminiResponse response = interceptor.wrapAsync(mockCall)
                .apply(request)
                .get();

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getText()).isEqualTo("Async response");
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

            // Create mock async Gemini call (should not be called)
            Function<GeminiRequest, CompletableFuture<GeminiResponse>> mockCall = request -> {
                fail("Gemini call should not be made when blocked");
                return null;
            };

            // Create request
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Blocked content");

            // Execute wrapped async call - should return failed future or throw
            try {
                CompletableFuture<GeminiResponse> future = interceptor.wrapAsync(mockCall)
                    .apply(request);

                // If we get a future, it should be failed
                assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(PolicyViolationException.class);
            } catch (PolicyViolationException e) {
                // Some implementations may throw directly
                assertThat(e.getMessage()).contains("Content policy violation");
            }
        }

        @Test
        @DisplayName("wrap should handle null response from LLM")
        void testWrapWithNullResponse() {
            // Stub policy check - allowed with no plan_id
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

            // Create mock call that returns null
            Function<GeminiRequest, GeminiResponse> mockCall = request -> null;

            // Create request
            GeminiRequest request = GeminiRequest.create("gemini-pro", "Test");

            // Execute - should not throw
            GeminiResponse response = interceptor.wrap(mockCall).apply(request);
            assertThat(response).isNull();
        }
    }
}
