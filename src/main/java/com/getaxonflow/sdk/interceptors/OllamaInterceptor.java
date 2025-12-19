package com.getaxonflow.sdk.interceptors;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.PolicyViolationException;
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
 * Interceptor for Ollama API calls with automatic governance.
 *
 * <p>Ollama is a local LLM server that runs on localhost:11434 by default.
 * No authentication is required.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * AxonFlow axonflow = new AxonFlow(config);
 * OllamaInterceptor interceptor = new OllamaInterceptor(axonflow, "user-123");
 *
 * // Wrap your Ollama call
 * Function<OllamaChatRequest, OllamaChatResponse> wrapped = interceptor.wrapChat(
 *     request -> ollamaClient.chat(request)
 * );
 *
 * // Use normally - governance is automatic
 * OllamaChatResponse response = wrapped.apply(request);
 * }</pre>
 */
public class OllamaInterceptor {

    private final AxonFlow axonflow;
    private final String userToken;

    /**
     * Creates a new OllamaInterceptor.
     *
     * @param axonflow the AxonFlow client for governance
     * @param userToken the user token for policy evaluation
     */
    public OllamaInterceptor(AxonFlow axonflow, String userToken) {
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
     * Wraps a synchronous Ollama chat call with governance.
     *
     * @param ollamaCall the original Ollama call function
     * @return a wrapped function that applies governance
     */
    public Function<OllamaChatRequest, OllamaChatResponse> wrapChat(
            Function<OllamaChatRequest, OllamaChatResponse> ollamaCall) {

        return request -> {
            String prompt = request.extractPrompt();

            Map<String, Object> context = new HashMap<>();
            context.put("provider", "ollama");
            context.put("model", request.getModel());

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
            OllamaChatResponse result = ollamaCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            if (axonResponse.getPlanId() != null) {
                auditChatCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps a synchronous Ollama generate call with governance.
     *
     * @param ollamaCall the original Ollama generate function
     * @return a wrapped function that applies governance
     */
    public Function<OllamaGenerateRequest, OllamaGenerateResponse> wrapGenerate(
            Function<OllamaGenerateRequest, OllamaGenerateResponse> ollamaCall) {

        return request -> {
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "ollama");
            context.put("model", request.getModel());

            ClientResponse axonResponse = axonflow.executeQuery(
                ClientRequest.builder()
                    .query(request.getPrompt())
                    .userToken(userToken)
                    .requestType(RequestType.CHAT)
                    .context(context)
                    .build()
            );

            if (axonResponse.isBlocked()) {
                throw new PolicyViolationException(axonResponse.getBlockReason());
            }

            long startTime = System.currentTimeMillis();
            OllamaGenerateResponse result = ollamaCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            if (axonResponse.getPlanId() != null) {
                auditGenerateCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps an asynchronous Ollama chat call with governance.
     */
    public Function<OllamaChatRequest, CompletableFuture<OllamaChatResponse>> wrapChatAsync(
            Function<OllamaChatRequest, CompletableFuture<OllamaChatResponse>> ollamaCall) {

        return request -> {
            String prompt = request.extractPrompt();

            Map<String, Object> context = new HashMap<>();
            context.put("provider", "ollama");
            context.put("model", request.getModel());

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
            String model = request.getModel();

            return ollamaCall.apply(request)
                .thenApply(result -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    if (planId != null) {
                        auditChatCall(planId, result, model, latencyMs);
                    }
                    return result;
                });
        };
    }

    private void auditChatCall(String contextId, OllamaChatResponse response, String model, long latencyMs) {
        try {
            String summary = response != null && response.getMessage() != null
                ? response.getMessage().getContent()
                : "";
            if (summary.length() > 100) {
                summary = summary.substring(0, 100) + "...";
            }

            TokenUsage usage = TokenUsage.builder()
                .promptTokens(response != null ? response.getPromptEvalCount() : 0)
                .completionTokens(response != null ? response.getEvalCount() : 0)
                .totalTokens(response != null ? response.getPromptEvalCount() + response.getEvalCount() : 0)
                .build();

            AuditOptions auditOptions = AuditOptions.builder()
                .contextId(contextId)
                .responseSummary(summary)
                .provider("ollama")
                .model(model)
                .tokenUsage(usage)
                .latencyMs(latencyMs)
                .build();

            axonflow.auditLLMCall(auditOptions);
        } catch (Exception e) {
            // Log but don't fail the request
        }
    }

    private void auditGenerateCall(String contextId, OllamaGenerateResponse response, String model, long latencyMs) {
        try {
            String summary = response != null ? response.getResponse() : "";
            if (summary.length() > 100) {
                summary = summary.substring(0, 100) + "...";
            }

            TokenUsage usage = TokenUsage.builder()
                .promptTokens(response != null ? response.getPromptEvalCount() : 0)
                .completionTokens(response != null ? response.getEvalCount() : 0)
                .totalTokens(response != null ? response.getPromptEvalCount() + response.getEvalCount() : 0)
                .build();

            AuditOptions auditOptions = AuditOptions.builder()
                .contextId(contextId)
                .responseSummary(summary)
                .provider("ollama")
                .model(model)
                .tokenUsage(usage)
                .latencyMs(latencyMs)
                .build();

            axonflow.auditLLMCall(auditOptions);
        } catch (Exception e) {
            // Log but don't fail
        }
    }

    // ==================== Ollama Request/Response Types ====================

    /**
     * Ollama chat message.
     */
    public static class OllamaMessage {
        private String role;
        private String content;
        private List<String> images;

        public OllamaMessage() {}

        public OllamaMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static OllamaMessage user(String content) {
            return new OllamaMessage("user", content);
        }

        public static OllamaMessage assistant(String content) {
            return new OllamaMessage("assistant", content);
        }

        public static OllamaMessage system(String content) {
            return new OllamaMessage("system", content);
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }
    }

    /**
     * Ollama chat request.
     */
    public static class OllamaChatRequest {
        private String model;
        private List<OllamaMessage> messages;
        private boolean stream;
        private String format;
        private OllamaOptions options;

        public OllamaChatRequest() {
            this.messages = new ArrayList<>();
        }

        public static OllamaChatRequest create(String model, String userMessage) {
            OllamaChatRequest req = new OllamaChatRequest();
            req.model = model;
            req.messages.add(OllamaMessage.user(userMessage));
            return req;
        }

        public String extractPrompt() {
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            return messages.stream()
                .map(OllamaMessage::getContent)
                .collect(Collectors.joining(" "));
        }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<OllamaMessage> getMessages() { return messages; }
        public void setMessages(List<OllamaMessage> messages) { this.messages = messages; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public OllamaOptions getOptions() { return options; }
        public void setOptions(OllamaOptions options) { this.options = options; }
    }

    /**
     * Ollama generation options.
     */
    public static class OllamaOptions {
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer numPredict;
        private List<String> stop;

        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        public Double getTopP() { return topP; }
        public void setTopP(Double topP) { this.topP = topP; }
        public Integer getTopK() { return topK; }
        public void setTopK(Integer topK) { this.topK = topK; }
        public Integer getNumPredict() { return numPredict; }
        public void setNumPredict(Integer numPredict) { this.numPredict = numPredict; }
        public List<String> getStop() { return stop; }
        public void setStop(List<String> stop) { this.stop = stop; }
    }

    /**
     * Ollama chat response.
     */
    public static class OllamaChatResponse {
        private String model;
        private String createdAt;
        private OllamaMessage message;
        private boolean done;
        private long totalDuration;
        private long loadDuration;
        private int promptEvalCount;
        private long promptEvalDuration;
        private int evalCount;
        private long evalDuration;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public OllamaMessage getMessage() { return message; }
        public void setMessage(OllamaMessage message) { this.message = message; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
        public long getTotalDuration() { return totalDuration; }
        public void setTotalDuration(long totalDuration) { this.totalDuration = totalDuration; }
        public long getLoadDuration() { return loadDuration; }
        public void setLoadDuration(long loadDuration) { this.loadDuration = loadDuration; }
        public int getPromptEvalCount() { return promptEvalCount; }
        public void setPromptEvalCount(int promptEvalCount) { this.promptEvalCount = promptEvalCount; }
        public long getPromptEvalDuration() { return promptEvalDuration; }
        public void setPromptEvalDuration(long promptEvalDuration) { this.promptEvalDuration = promptEvalDuration; }
        public int getEvalCount() { return evalCount; }
        public void setEvalCount(int evalCount) { this.evalCount = evalCount; }
        public long getEvalDuration() { return evalDuration; }
        public void setEvalDuration(long evalDuration) { this.evalDuration = evalDuration; }
    }

    /**
     * Ollama generate request.
     */
    public static class OllamaGenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;
        private String format;
        private OllamaOptions options;

        public static OllamaGenerateRequest create(String model, String prompt) {
            OllamaGenerateRequest req = new OllamaGenerateRequest();
            req.model = model;
            req.prompt = prompt;
            return req;
        }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public OllamaOptions getOptions() { return options; }
        public void setOptions(OllamaOptions options) { this.options = options; }
    }

    /**
     * Ollama generate response.
     */
    public static class OllamaGenerateResponse {
        private String model;
        private String createdAt;
        private String response;
        private boolean done;
        private long totalDuration;
        private long loadDuration;
        private int promptEvalCount;
        private long promptEvalDuration;
        private int evalCount;
        private long evalDuration;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
        public long getTotalDuration() { return totalDuration; }
        public void setTotalDuration(long totalDuration) { this.totalDuration = totalDuration; }
        public long getLoadDuration() { return loadDuration; }
        public void setLoadDuration(long loadDuration) { this.loadDuration = loadDuration; }
        public int getPromptEvalCount() { return promptEvalCount; }
        public void setPromptEvalCount(int promptEvalCount) { this.promptEvalCount = promptEvalCount; }
        public long getPromptEvalDuration() { return promptEvalDuration; }
        public void setPromptEvalDuration(long promptEvalDuration) { this.promptEvalDuration = promptEvalDuration; }
        public int getEvalCount() { return evalCount; }
        public void setEvalCount(int evalCount) { this.evalCount = evalCount; }
        public long getEvalDuration() { return evalDuration; }
        public void setEvalDuration(long evalDuration) { this.evalDuration = evalDuration; }
    }
}
