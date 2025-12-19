package com.getaxonflow.sdk.interceptors;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.types.RequestType;
import com.getaxonflow.sdk.types.TokenUsage;
import com.getaxonflow.sdk.types.AuditOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interceptor for AWS Bedrock API calls with automatic governance.
 *
 * <p>Bedrock uses AWS IAM authentication (no API keys required).
 * Supports multiple model providers: Anthropic Claude, Amazon Titan, Meta Llama, etc.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * AxonFlow axonflow = new AxonFlow(config);
 * BedrockInterceptor interceptor = new BedrockInterceptor(axonflow, "user-123");
 *
 * // Wrap your Bedrock call
 * Function<BedrockInvokeRequest, BedrockInvokeResponse> wrapped = interceptor.wrap(
 *     request -> bedrockClient.invokeModel(request)
 * );
 *
 * // Use normally - governance is automatic
 * BedrockInvokeResponse response = wrapped.apply(request);
 * }</pre>
 */
public class BedrockInterceptor {

    private final AxonFlow axonflow;
    private final String userToken;

    // Common Bedrock model IDs
    public static final String CLAUDE_3_OPUS = "anthropic.claude-3-opus-20240229-v1:0";
    public static final String CLAUDE_3_SONNET = "anthropic.claude-3-sonnet-20240229-v1:0";
    public static final String CLAUDE_3_HAIKU = "anthropic.claude-3-haiku-20240307-v1:0";
    public static final String CLAUDE_2 = "anthropic.claude-v2:1";
    public static final String TITAN_TEXT_EXPRESS = "amazon.titan-text-express-v1";
    public static final String TITAN_TEXT_LITE = "amazon.titan-text-lite-v1";
    public static final String LLAMA2_70B = "meta.llama2-70b-chat-v1";
    public static final String LLAMA3_70B = "meta.llama3-70b-instruct-v1:0";

    /**
     * Creates a new BedrockInterceptor.
     *
     * @param axonflow the AxonFlow client for governance
     * @param userToken the user token for policy evaluation
     */
    public BedrockInterceptor(AxonFlow axonflow, String userToken) {
        if (axonflow == null) {
            throw new IllegalArgumentException("axonflow cannot be null");
        }
        if (userToken == null || userToken.isEmpty()) {
            throw new IllegalArgumentException("userToken cannot be null or empty");
        }
        this.axonflow = axonflow;
        this.userToken = userToken;
    }

    /**
     * Wraps a synchronous Bedrock InvokeModel call with governance.
     *
     * @param bedrockCall the original Bedrock call function
     * @return a wrapped function that applies governance
     */
    public Function<BedrockInvokeRequest, BedrockInvokeResponse> wrap(
            Function<BedrockInvokeRequest, BedrockInvokeResponse> bedrockCall) {

        return request -> {
            String prompt = request.extractPrompt();

            Map<String, Object> context = new HashMap<>();
            context.put("provider", "bedrock");
            context.put("model", request.getModelId());

            ClientResponse axonResponse = axonflow.executeQuery(
                ClientRequest.builder()
                    .query(prompt)
                    .userToken(userToken)
                    .requestType(RequestType.CHAT)
                    .context(context)
                    .build()
            );

            if (axonResponse.isBlocked()) {
                throw new PolicyViolationException(axonResponse.getBlockReason());
            }

            long startTime = System.currentTimeMillis();
            BedrockInvokeResponse result = bedrockCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            if (axonResponse.getPlanId() != null) {
                auditCall(axonResponse.getPlanId(), result, request.getModelId(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps an asynchronous Bedrock InvokeModel call with governance.
     */
    public Function<BedrockInvokeRequest, CompletableFuture<BedrockInvokeResponse>> wrapAsync(
            Function<BedrockInvokeRequest, CompletableFuture<BedrockInvokeResponse>> bedrockCall) {

        return request -> {
            String prompt = request.extractPrompt();

            Map<String, Object> context = new HashMap<>();
            context.put("provider", "bedrock");
            context.put("model", request.getModelId());

            ClientResponse axonResponse = axonflow.executeQuery(
                ClientRequest.builder()
                    .query(prompt)
                    .userToken(userToken)
                    .requestType(RequestType.CHAT)
                    .context(context)
                    .build()
            );

            if (axonResponse.isBlocked()) {
                return CompletableFuture.failedFuture(
                    new PolicyViolationException(axonResponse.getBlockReason())
                );
            }

            long startTime = System.currentTimeMillis();
            String planId = axonResponse.getPlanId();
            String modelId = request.getModelId();

            return bedrockCall.apply(request)
                .thenApply(result -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    if (planId != null) {
                        auditCall(planId, result, modelId, latencyMs);
                    }
                    return result;
                });
        };
    }

    private void auditCall(String contextId, BedrockInvokeResponse response, String modelId, long latencyMs) {
        try {
            String summary = response != null ? response.getSummary() : "";

            int promptTokens = response != null ? response.getInputTokens() : 0;
            int completionTokens = response != null ? response.getOutputTokens() : 0;
            TokenUsage usage = TokenUsage.of(promptTokens, completionTokens);

            AuditOptions auditOptions = AuditOptions.builder()
                .contextId(contextId)
                .responseSummary(summary)
                .provider("bedrock")
                .model(modelId)
                .tokenUsage(usage)
                .latencyMs(latencyMs)
                .build();

            axonflow.auditLLMCall(auditOptions);
        } catch (Exception e) {
            // Log but don't fail
        }
    }

    // ==================== Bedrock Request/Response Types ====================

    /**
     * Bedrock InvokeModel request.
     */
    public static class BedrockInvokeRequest {
        private String modelId;
        private String body;
        private String contentType = "application/json";
        private String accept = "application/json";

        // Parsed body fields (for convenience)
        private List<ClaudeMessage> messages;
        private String inputText; // For Titan

        public static BedrockInvokeRequest forClaude(String modelId, List<ClaudeMessage> messages, int maxTokens) {
            BedrockInvokeRequest req = new BedrockInvokeRequest();
            req.modelId = modelId;
            req.messages = messages;
            // Body would be built from messages in practice
            return req;
        }

        public static BedrockInvokeRequest forTitan(String modelId, String inputText) {
            BedrockInvokeRequest req = new BedrockInvokeRequest();
            req.modelId = modelId;
            req.inputText = inputText;
            return req;
        }

        public String extractPrompt() {
            if (messages != null && !messages.isEmpty()) {
                return messages.stream()
                    .map(ClaudeMessage::getContent)
                    .collect(Collectors.joining(" "));
            }
            if (inputText != null) {
                return inputText;
            }
            return "";
        }

        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getAccept() { return accept; }
        public void setAccept(String accept) { this.accept = accept; }
        public List<ClaudeMessage> getMessages() { return messages; }
        public void setMessages(List<ClaudeMessage> messages) { this.messages = messages; }
        public String getInputText() { return inputText; }
        public void setInputText(String inputText) { this.inputText = inputText; }
    }

    /**
     * Claude message format for Bedrock.
     */
    public static class ClaudeMessage {
        private String role;
        private String content;

        public ClaudeMessage() {}

        public ClaudeMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static ClaudeMessage user(String content) {
            return new ClaudeMessage("user", content);
        }

        public static ClaudeMessage assistant(String content) {
            return new ClaudeMessage("assistant", content);
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * Bedrock InvokeModel response.
     */
    public static class BedrockInvokeResponse {
        private byte[] body;
        private String contentType;

        // Parsed response fields
        private String responseText;
        private int inputTokens;
        private int outputTokens;

        public String getSummary() {
            if (responseText == null || responseText.isEmpty()) {
                return "";
            }
            return responseText.length() > 100
                ? responseText.substring(0, 100) + "..."
                : responseText;
        }

        public byte[] getBody() { return body; }
        public void setBody(byte[] body) { this.body = body; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getResponseText() { return responseText; }
        public void setResponseText(String responseText) { this.responseText = responseText; }
        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    }
}
