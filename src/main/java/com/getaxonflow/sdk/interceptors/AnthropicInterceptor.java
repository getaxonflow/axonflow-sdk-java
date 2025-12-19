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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interceptor for wrapping Anthropic API calls with AxonFlow governance.
 *
 * <p>This interceptor automatically applies policy checks and audit logging
 * to Anthropic API calls without requiring changes to application code.
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
 * AnthropicInterceptor interceptor = AnthropicInterceptor.builder()
 *     .axonflow(axonflow)
 *     .userToken("user-123")
 *     .build();
 *
 * // Wrap your Anthropic call
 * AnthropicResponse response = interceptor.wrap(req -> {
 *     // Your actual Anthropic SDK call here
 *     return yourAnthropicClient.createMessage(req);
 * }).apply(AnthropicRequest.builder()
 *     .model("claude-3-sonnet-20240229")
 *     .maxTokens(1024)
 *     .addUserMessage("Hello, Claude!")
 *     .build());
 * }</pre>
 *
 * @see AxonFlow
 */
public final class AnthropicInterceptor {
    private final AxonFlow axonflow;
    private final String userToken;
    private final boolean asyncAudit;

    private AnthropicInterceptor(Builder builder) {
        this.axonflow = Objects.requireNonNull(builder.axonflow, "axonflow must not be null");
        this.userToken = builder.userToken != null ? builder.userToken : "";
        this.asyncAudit = builder.asyncAudit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wraps an Anthropic message creation function with governance.
     *
     * @param anthropicCall the function that makes the actual Anthropic API call
     * @return a wrapped function that applies governance before/after the call
     */
    public Function<AnthropicRequest, AnthropicResponse> wrap(
            Function<AnthropicRequest, AnthropicResponse> anthropicCall) {
        return request -> {
            // Extract prompt from messages
            String prompt = request.extractPrompt();

            // Build context for policy evaluation
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "anthropic");
            context.put("model", request.getModel());
            if (request.getTemperature() != null) {
                context.put("temperature", request.getTemperature());
            }
            context.put("max_tokens", request.getMaxTokens());

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

            // Make the actual Anthropic call
            AnthropicResponse result = anthropicCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            // Audit the call
            if (axonResponse.getPlanId() != null) {
                auditCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps an async Anthropic message creation function with governance.
     *
     * @param anthropicCall the function that makes the actual Anthropic API call
     * @return a wrapped function that applies governance before/after the call
     */
    public Function<AnthropicRequest, CompletableFuture<AnthropicResponse>> wrapAsync(
            Function<AnthropicRequest, CompletableFuture<AnthropicResponse>> anthropicCall) {
        return request -> {
            // Extract prompt from messages
            String prompt = request.extractPrompt();

            // Build context for policy evaluation
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "anthropic");
            context.put("model", request.getModel());
            if (request.getTemperature() != null) {
                context.put("temperature", request.getTemperature());
            }
            context.put("max_tokens", request.getMaxTokens());

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
                    CompletableFuture<AnthropicResponse> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new PolicyViolationException(
                        axonResponse.getBlockReason()
                    ));
                    return failed;
                }

                // Make the actual Anthropic call
                return anthropicCall.apply(request).thenApply(result -> {
                    long latencyMs = System.currentTimeMillis() - startTime;

                    // Audit the call
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

    private void auditCall(String contextId, AnthropicResponse result, String model, long latencyMs) {
        try {
            AnthropicResponse.Usage usage = result.getUsage();
            TokenUsage tokenUsage = usage != null ?
                TokenUsage.of(usage.getInputTokens(), usage.getOutputTokens()) :
                TokenUsage.of(0, 0);

            axonflow.auditLLMCall(AuditOptions.builder()
                .contextId(contextId)
                .clientId(userToken)
                .responseSummary(result.getSummary())
                .provider("anthropic")
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
     * Creates a simple wrapper function for Anthropic messages.
     *
     * @param axonflow      the AxonFlow client
     * @param userToken     the user token for policy evaluation
     * @param anthropicCall the function that makes the actual Anthropic API call
     * @return a wrapped function
     */
    public static Function<AnthropicRequest, AnthropicResponse> wrapMessage(
            AxonFlow axonflow,
            String userToken,
            Function<AnthropicRequest, AnthropicResponse> anthropicCall) {
        return builder()
            .axonflow(axonflow)
            .userToken(userToken)
            .build()
            .wrap(anthropicCall);
    }

    /**
     * Request for Anthropic message creation.
     */
    public static final class AnthropicRequest {
        private final String model;
        private final int maxTokens;
        private final List<AnthropicMessage> messages;
        private final String system;
        private final Double temperature;
        private final Double topP;
        private final Integer topK;

        private AnthropicRequest(Builder builder) {
            this.model = Objects.requireNonNull(builder.model, "model must not be null");
            this.maxTokens = builder.maxTokens;
            this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
            this.system = builder.system;
            this.temperature = builder.temperature;
            this.topP = builder.topP;
            this.topK = builder.topK;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getModel() { return model; }
        public int getMaxTokens() { return maxTokens; }
        public List<AnthropicMessage> getMessages() { return messages; }
        public String getSystem() { return system; }
        public Double getTemperature() { return temperature; }
        public Double getTopP() { return topP; }
        public Integer getTopK() { return topK; }

        /**
         * Extracts the combined prompt from system and messages.
         */
        public String extractPrompt() {
            StringBuilder sb = new StringBuilder();
            if (system != null && !system.isEmpty()) {
                sb.append(system);
            }
            for (AnthropicMessage msg : messages) {
                for (AnthropicContentBlock block : msg.getContent()) {
                    if ("text".equals(block.getType()) && block.getText() != null) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(block.getText());
                    }
                }
            }
            return sb.toString();
        }

        public static final class Builder {
            private String model;
            private int maxTokens = 1024;
            private final List<AnthropicMessage> messages = new ArrayList<>();
            private String system;
            private Double temperature;
            private Double topP;
            private Integer topK;

            private Builder() {}

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder maxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder messages(List<AnthropicMessage> messages) {
                this.messages.clear();
                if (messages != null) {
                    this.messages.addAll(messages);
                }
                return this;
            }

            public Builder addMessage(AnthropicMessage message) {
                this.messages.add(message);
                return this;
            }

            public Builder addUserMessage(String text) {
                this.messages.add(AnthropicMessage.user(text));
                return this;
            }

            public Builder addAssistantMessage(String text) {
                this.messages.add(AnthropicMessage.assistant(text));
                return this;
            }

            public Builder system(String system) {
                this.system = system;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder topP(Double topP) {
                this.topP = topP;
                return this;
            }

            public Builder topK(Integer topK) {
                this.topK = topK;
                return this;
            }

            public AnthropicRequest build() {
                return new AnthropicRequest(this);
            }
        }
    }

    /**
     * Response from Anthropic message creation.
     */
    public static final class AnthropicResponse {
        private final String id;
        private final String type;
        private final String role;
        private final String model;
        private final List<AnthropicContentBlock> content;
        private final String stopReason;
        private final Usage usage;

        private AnthropicResponse(Builder builder) {
            this.id = builder.id;
            this.type = builder.type;
            this.role = builder.role;
            this.model = builder.model;
            this.content = builder.content != null ?
                Collections.unmodifiableList(new ArrayList<>(builder.content)) :
                Collections.emptyList();
            this.stopReason = builder.stopReason;
            this.usage = builder.usage;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getRole() { return role; }
        public String getModel() { return model; }
        public List<AnthropicContentBlock> getContent() { return content; }
        public String getStopReason() { return stopReason; }
        public Usage getUsage() { return usage; }

        /**
         * Gets a summary of the response (first 100 characters of text content).
         */
        public String getSummary() {
            for (AnthropicContentBlock block : content) {
                if ("text".equals(block.getType()) && block.getText() != null) {
                    String text = block.getText();
                    if (text.length() > 100) {
                        return text.substring(0, 100);
                    }
                    return text;
                }
            }
            return "";
        }

        public static final class Usage {
            private final int inputTokens;
            private final int outputTokens;

            public Usage(int inputTokens, int outputTokens) {
                this.inputTokens = inputTokens;
                this.outputTokens = outputTokens;
            }

            public int getInputTokens() { return inputTokens; }
            public int getOutputTokens() { return outputTokens; }
        }

        public static final class Builder {
            private String id;
            private String type = "message";
            private String role = "assistant";
            private String model;
            private List<AnthropicContentBlock> content;
            private String stopReason;
            private Usage usage;

            private Builder() {}

            public Builder id(String id) { this.id = id; return this; }
            public Builder type(String type) { this.type = type; return this; }
            public Builder role(String role) { this.role = role; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder content(List<AnthropicContentBlock> content) { this.content = content; return this; }
            public Builder stopReason(String stopReason) { this.stopReason = stopReason; return this; }
            public Builder usage(Usage usage) { this.usage = usage; return this; }

            public AnthropicResponse build() {
                return new AnthropicResponse(this);
            }
        }
    }

    /**
     * Anthropic message with content blocks.
     */
    public static final class AnthropicMessage {
        private final String role;
        private final List<AnthropicContentBlock> content;

        private AnthropicMessage(String role, List<AnthropicContentBlock> content) {
            this.role = Objects.requireNonNull(role);
            this.content = Collections.unmodifiableList(new ArrayList<>(content));
        }

        public static AnthropicMessage of(String role, List<AnthropicContentBlock> content) {
            return new AnthropicMessage(role, content);
        }

        public static AnthropicMessage user(String text) {
            return new AnthropicMessage("user", List.of(AnthropicContentBlock.text(text)));
        }

        public static AnthropicMessage assistant(String text) {
            return new AnthropicMessage("assistant", List.of(AnthropicContentBlock.text(text)));
        }

        public String getRole() { return role; }
        public List<AnthropicContentBlock> getContent() { return content; }
    }

    /**
     * Content block in an Anthropic message.
     */
    public static final class AnthropicContentBlock {
        private final String type;
        private final String text;

        private AnthropicContentBlock(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public static AnthropicContentBlock text(String text) {
            return new AnthropicContentBlock("text", text);
        }

        public String getType() { return type; }
        public String getText() { return text; }
    }

    public static final class Builder {
        private AxonFlow axonflow;
        private String userToken;
        private boolean asyncAudit = true;

        private Builder() {}

        public Builder axonflow(AxonFlow axonflow) {
            this.axonflow = axonflow;
            return this;
        }

        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        public Builder asyncAudit(boolean asyncAudit) {
            this.asyncAudit = asyncAudit;
            return this;
        }

        public AnthropicInterceptor build() {
            return new AnthropicInterceptor(this);
        }
    }
}
