/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.interceptors;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.AuditOptions;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.types.RequestType;
import com.getaxonflow.sdk.types.TokenUsage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interceptor for wrapping OpenAI API calls with AxonFlow governance.
 *
 * <p>This interceptor automatically applies policy checks and audit logging
 * to OpenAI API calls without requiring changes to application code.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create AxonFlow client
 * AxonFlow axonflow = AxonFlow.builder()
 *     .agentUrl("http://localhost:8080")
 *     .licenseKey("your-license-key")
 *     .build();
 *
 * // Create interceptor
 * OpenAIInterceptor interceptor = OpenAIInterceptor.builder()
 *     .axonflow(axonflow)
 *     .userToken("user-123")
 *     .build();
 *
 * // Wrap your OpenAI call
 * ChatCompletionResponse response = interceptor.wrap(req -> {
 *     // Your actual OpenAI SDK call here
 *     return yourOpenAIClient.createChatCompletion(req);
 * }).apply(ChatCompletionRequest.builder()
 *     .model("gpt-4")
 *     .addUserMessage("Hello, world!")
 *     .build());
 * }</pre>
 *
 * @see AxonFlow
 * @see ChatCompletionRequest
 * @see ChatCompletionResponse
 */
public final class OpenAIInterceptor {
    private final AxonFlow axonflow;
    private final String userToken;
    private final boolean asyncAudit;

    private OpenAIInterceptor(Builder builder) {
        this.axonflow = Objects.requireNonNull(builder.axonflow, "axonflow must not be null");
        this.userToken = builder.userToken != null ? builder.userToken : "";
        this.asyncAudit = builder.asyncAudit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wraps an OpenAI chat completion function with governance.
     *
     * @param openaiCall the function that makes the actual OpenAI API call
     * @return a wrapped function that applies governance before/after the call
     */
    public Function<ChatCompletionRequest, ChatCompletionResponse> wrap(
            Function<ChatCompletionRequest, ChatCompletionResponse> openaiCall) {
        return request -> {
            // Extract prompt from messages
            String prompt = request.extractPrompt();

            // Build context for policy evaluation
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "openai");
            context.put("model", request.getModel());
            if (request.getTemperature() != null) {
                context.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                context.put("max_tokens", request.getMaxTokens());
            }

            // Check with AxonFlow
            long startTime = System.currentTimeMillis();
            ClientResponse axonResponse = axonflow.executeQuery(
                ClientRequest.builder()
                    .query(prompt)
                    .userToken(userToken)
                    .requestType(RequestType.CHAT)
                    .context(context)
                    .build()
            );

            // Check if request was blocked
            if (axonResponse.isBlocked()) {
                throw new PolicyViolationException(axonResponse.getBlockReason());
            }

            // Make the actual OpenAI call
            ChatCompletionResponse result = openaiCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            // Audit the call
            if (axonResponse.getPlanId() != null) {
                auditCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps an async OpenAI chat completion function with governance.
     *
     * @param openaiCall the function that makes the actual OpenAI API call
     * @return a wrapped function that applies governance before/after the call
     */
    public Function<ChatCompletionRequest, CompletableFuture<ChatCompletionResponse>> wrapAsync(
            Function<ChatCompletionRequest, CompletableFuture<ChatCompletionResponse>> openaiCall) {
        return request -> {
            // Extract prompt from messages
            String prompt = request.extractPrompt();

            // Build context for policy evaluation
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "openai");
            context.put("model", request.getModel());
            if (request.getTemperature() != null) {
                context.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                context.put("max_tokens", request.getMaxTokens());
            }

            // Check with AxonFlow (async)
            long startTime = System.currentTimeMillis();

            return axonflow.executeQueryAsync(
                ClientRequest.builder()
                    .query(prompt)
                    .userToken(userToken)
                    .requestType(RequestType.CHAT)
                    .context(context)
                    .build()
            ).thenCompose(axonResponse -> {
                // Check if request was blocked
                if (axonResponse.isBlocked()) {
                    CompletableFuture<ChatCompletionResponse> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new PolicyViolationException(
                        axonResponse.getBlockReason()
                    ));
                    return failed;
                }

                // Make the actual OpenAI call
                return openaiCall.apply(request).thenApply(result -> {
                    long latencyMs = System.currentTimeMillis() - startTime;

                    // Audit the call (async/fire-and-forget)
                    if (axonResponse.getPlanId() != null) {
                        if (asyncAudit) {
                            CompletableFuture.runAsync(() ->
                                auditCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs)
                            );
                        } else {
                            auditCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
                        }
                    }

                    return result;
                });
            });
        };
    }

    private void auditCall(String contextId, ChatCompletionResponse result, String model, long latencyMs) {
        try {
            ChatCompletionResponse.Usage usage = result.getUsage();
            TokenUsage tokenUsage = usage != null ?
                TokenUsage.of(usage.getPromptTokens(), usage.getCompletionTokens()) :
                TokenUsage.of(0, 0);

            axonflow.auditLLMCall(AuditOptions.builder()
                .contextId(contextId)
                .clientId(userToken)
                .responseSummary(result.getSummary())
                .provider("openai")
                .model(model)
                .tokenUsage(tokenUsage)
                .latencyMs(latencyMs)
                .success(true)
                .build());
        } catch (Exception e) {
            // Best effort - don't fail the response if audit fails
        }
    }

    /**
     * Creates a simple wrapper function for chat completions.
     *
     * @param axonflow  the AxonFlow client
     * @param userToken the user token for policy evaluation
     * @param openaiCall the function that makes the actual OpenAI API call
     * @return a wrapped function
     */
    public static Function<ChatCompletionRequest, ChatCompletionResponse> wrapChatCompletion(
            AxonFlow axonflow,
            String userToken,
            Function<ChatCompletionRequest, ChatCompletionResponse> openaiCall) {
        return builder()
            .axonflow(axonflow)
            .userToken(userToken)
            .build()
            .wrap(openaiCall);
    }

    public static final class Builder {
        private AxonFlow axonflow;
        private String userToken;
        private boolean asyncAudit = true;

        private Builder() {}

        /**
         * Sets the AxonFlow client for governance.
         *
         * @param axonflow the AxonFlow client
         * @return this builder
         */
        public Builder axonflow(AxonFlow axonflow) {
            this.axonflow = axonflow;
            return this;
        }

        /**
         * Sets the user token for policy evaluation.
         *
         * @param userToken the user token
         * @return this builder
         */
        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        /**
         * Sets whether to perform audit logging asynchronously.
         * Default is true (fire-and-forget).
         *
         * @param asyncAudit true to audit asynchronously
         * @return this builder
         */
        public Builder asyncAudit(boolean asyncAudit) {
            this.asyncAudit = asyncAudit;
            return this;
        }

        public OpenAIInterceptor build() {
            return new OpenAIInterceptor(this);
        }
    }
}
