/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.interceptors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a chat completion request for OpenAI-compatible APIs.
 */
public final class ChatCompletionRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final Double temperature;
    private final Integer maxTokens;
    private final Double topP;
    private final Integer n;
    private final Boolean stream;
    private final List<String> stop;

    private ChatCompletionRequest(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "model must not be null");
        this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.n = builder.n;
        this.stream = builder.stream;
        this.stop = builder.stop != null ? Collections.unmodifiableList(new ArrayList<>(builder.stop)) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getN() {
        return n;
    }

    public Boolean getStream() {
        return stream;
    }

    public List<String> getStop() {
        return stop;
    }

    /**
     * Extracts the combined prompt from all messages.
     *
     * @return concatenated content of all messages
     */
    public String extractPrompt() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(msg.getContent());
            }
        }
        return sb.toString();
    }

    public static final class Builder {
        private String model;
        private final List<ChatMessage> messages = new ArrayList<>();
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private List<String> stop;

        private Builder() {}

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        public Builder addMessage(ChatMessage message) {
            this.messages.add(message);
            return this;
        }

        public Builder addUserMessage(String content) {
            this.messages.add(ChatMessage.user(content));
            return this;
        }

        public Builder addSystemMessage(String content) {
            this.messages.add(ChatMessage.system(content));
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
