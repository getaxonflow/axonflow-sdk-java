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

/**
 * Interceptor for Google Gemini API calls with automatic governance.
 *
 * <p>Wraps Gemini GenerativeModel calls with AxonFlow policy checking and audit logging.
 * Works with the google-cloud-vertexai SDK or any compatible client.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * AxonFlow axonflow = new AxonFlow(axonflowConfig);
 * GeminiInterceptor interceptor = new GeminiInterceptor(axonflow, "user-123");
 *
 * // Wrap your Gemini call
 * Function<GeminiRequest, GeminiResponse> wrapped = interceptor.wrap(
 *     request -> geminiModel.generateContent(request)
 * );
 *
 * // Use normally - governance is automatic
 * GeminiResponse response = wrapped.apply(request);
 * }</pre>
 */
public class GeminiInterceptor {

    private final AxonFlow axonflow;
    private final String userToken;

    /**
     * Creates a new GeminiInterceptor.
     *
     * @param axonflow the AxonFlow client for governance
     * @param userToken the user token for policy evaluation
     */
    public GeminiInterceptor(AxonFlow axonflow, String userToken) {
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
     * Wraps a synchronous Gemini generateContent call with governance.
     *
     * @param geminiCall the original Gemini call function
     * @return a wrapped function that applies governance
     */
    public Function<GeminiRequest, GeminiResponse> wrap(
            Function<GeminiRequest, GeminiResponse> geminiCall) {

        return request -> {
            String prompt = request.extractPrompt();

            // Build context for policy evaluation
            Map<String, Object> context = new HashMap<>();
            context.put("provider", "gemini");
            context.put("model", request.getModel());
            if (request.getGenerationConfig() != null) {
                context.put("temperature", request.getGenerationConfig().getTemperature());
                context.put("maxOutputTokens", request.getGenerationConfig().getMaxOutputTokens());
            }

            // Pre-check with AxonFlow
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

            // Execute the actual LLM call
            long startTime = System.currentTimeMillis();
            GeminiResponse result = geminiCall.apply(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            // Audit the call
            if (axonResponse.getPlanId() != null) {
                auditCall(axonResponse.getPlanId(), result, request.getModel(), latencyMs);
            }

            return result;
        };
    }

    /**
     * Wraps an asynchronous Gemini generateContent call with governance.
     *
     * @param geminiCall the original async Gemini call function
     * @return a wrapped function that applies governance
     */
    public Function<GeminiRequest, CompletableFuture<GeminiResponse>> wrapAsync(
            Function<GeminiRequest, CompletableFuture<GeminiResponse>> geminiCall) {

        return request -> {
            String prompt = request.extractPrompt();

            Map<String, Object> context = new HashMap<>();
            context.put("provider", "gemini");
            context.put("model", request.getModel());

            // Pre-check (synchronous for now)
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

            return geminiCall.apply(request)
                .thenApply(result -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    if (planId != null) {
                        auditCall(planId, result, model, latencyMs);
                    }
                    return result;
                });
        };
    }

    private void auditCall(String contextId, GeminiResponse response, String model, long latencyMs) {
        try {
            String summary = response != null ? response.getSummary() : "";

            TokenUsage usage = TokenUsage.builder()
                .promptTokens(response != null ? response.getPromptTokenCount() : 0)
                .completionTokens(response != null ? response.getCandidatesTokenCount() : 0)
                .totalTokens(response != null ? response.getTotalTokenCount() : 0)
                .build();

            AuditOptions auditOptions = AuditOptions.builder()
                .contextId(contextId)
                .responseSummary(summary)
                .provider("gemini")
                .model(model)
                .tokenUsage(usage)
                .latencyMs(latencyMs)
                .build();

            axonflow.auditLLMCall(auditOptions);
        } catch (Exception e) {
            // Log but don't fail the request
        }
    }

    // ==================== Gemini Request/Response Types ====================

    /**
     * Represents a Gemini GenerateContent request.
     */
    public static class GeminiRequest {
        private String model;
        private List<Content> contents;
        private GenerationConfig generationConfig;

        public GeminiRequest() {
            this.contents = new ArrayList<>();
        }

        public static GeminiRequest create(String model, String prompt) {
            GeminiRequest request = new GeminiRequest();
            request.model = model;
            request.contents.add(Content.text(prompt));
            return request;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<Content> getContents() {
            return contents;
        }

        public void setContents(List<Content> contents) {
            this.contents = contents;
        }

        public GenerationConfig getGenerationConfig() {
            return generationConfig;
        }

        public void setGenerationConfig(GenerationConfig generationConfig) {
            this.generationConfig = generationConfig;
        }

        /**
         * Extracts the prompt text from all content parts.
         */
        public String extractPrompt() {
            if (contents == null || contents.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Content content : contents) {
                if (content.getParts() != null) {
                    for (Part part : content.getParts()) {
                        if (part.getText() != null) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(part.getText());
                        }
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Represents content in a Gemini request.
     */
    public static class Content {
        private String role;
        private List<Part> parts;

        public Content() {
            this.parts = new ArrayList<>();
        }

        public static Content text(String text) {
            Content content = new Content();
            content.role = "user";
            content.parts.add(Part.text(text));
            return content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    /**
     * Represents a part of content (text or inline data).
     */
    public static class Part {
        private String text;
        private InlineData inlineData;

        public static Part text(String text) {
            Part part = new Part();
            part.text = text;
            return part;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public InlineData getInlineData() {
            return inlineData;
        }

        public void setInlineData(InlineData inlineData) {
            this.inlineData = inlineData;
        }
    }

    /**
     * Represents inline binary data (images, etc.).
     */
    public static class InlineData {
        private String mimeType;
        private String data;

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    /**
     * Generation configuration parameters.
     */
    public static class GenerationConfig {
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxOutputTokens;
        private List<String> stopSequences;

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public List<String> getStopSequences() {
            return stopSequences;
        }

        public void setStopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
        }
    }

    /**
     * Represents a Gemini GenerateContent response.
     */
    public static class GeminiResponse {
        private List<Candidate> candidates;
        private UsageMetadata usageMetadata;

        public List<Candidate> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<Candidate> candidates) {
            this.candidates = candidates;
        }

        public UsageMetadata getUsageMetadata() {
            return usageMetadata;
        }

        public void setUsageMetadata(UsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
        }

        /**
         * Gets a summary of the response (first 100 characters of first candidate).
         */
        public String getSummary() {
            String text = getText();
            if (text == null || text.isEmpty()) {
                return "";
            }
            return text.length() > 100 ? text.substring(0, 100) + "..." : text;
        }

        /**
         * Gets the text content from the first candidate.
         */
        public String getText() {
            if (candidates == null || candidates.isEmpty()) {
                return "";
            }
            Candidate first = candidates.get(0);
            if (first.getContent() == null || first.getContent().getParts() == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Part part : first.getContent().getParts()) {
                if (part.getText() != null) {
                    sb.append(part.getText());
                }
            }
            return sb.toString();
        }

        public int getPromptTokenCount() {
            return usageMetadata != null ? usageMetadata.getPromptTokenCount() : 0;
        }

        public int getCandidatesTokenCount() {
            return usageMetadata != null ? usageMetadata.getCandidatesTokenCount() : 0;
        }

        public int getTotalTokenCount() {
            return usageMetadata != null ? usageMetadata.getTotalTokenCount() : 0;
        }
    }

    /**
     * Represents a response candidate.
     */
    public static class Candidate {
        private Content content;
        private String finishReason;

        public Content getContent() {
            return content;
        }

        public void setContent(Content content) {
            this.content = content;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    /**
     * Token usage metadata.
     */
    public static class UsageMetadata {
        private int promptTokenCount;
        private int candidatesTokenCount;
        private int totalTokenCount;

        public int getPromptTokenCount() {
            return promptTokenCount;
        }

        public void setPromptTokenCount(int promptTokenCount) {
            this.promptTokenCount = promptTokenCount;
        }

        public int getCandidatesTokenCount() {
            return candidatesTokenCount;
        }

        public void setCandidatesTokenCount(int candidatesTokenCount) {
            this.candidatesTokenCount = candidatesTokenCount;
        }

        public int getTotalTokenCount() {
            return totalTokenCount;
        }

        public void setTotalTokenCount(int totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
        }
    }
}
