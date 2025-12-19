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
 * Represents a chat completion response from OpenAI-compatible APIs.
 */
public final class ChatCompletionResponse {
    private final String id;
    private final String object;
    private final long created;
    private final String model;
    private final List<Choice> choices;
    private final Usage usage;

    private ChatCompletionResponse(Builder builder) {
        this.id = builder.id;
        this.object = builder.object;
        this.created = builder.created;
        this.model = builder.model;
        this.choices = builder.choices != null ?
            Collections.unmodifiableList(new ArrayList<>(builder.choices)) :
            Collections.emptyList();
        this.usage = builder.usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getObject() {
        return object;
    }

    public long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public Usage getUsage() {
        return usage;
    }

    /**
     * Gets the content of the first choice's message.
     *
     * @return the content or empty string if not available
     */
    public String getContent() {
        if (choices.isEmpty()) {
            return "";
        }
        ChatMessage msg = choices.get(0).getMessage();
        return msg != null ? msg.getContent() : "";
    }

    /**
     * Gets a summary of the response (first 100 characters).
     *
     * @return the summary
     */
    public String getSummary() {
        String content = getContent();
        if (content.length() > 100) {
            return content.substring(0, 100);
        }
        return content;
    }

    /**
     * Represents a choice in the completion response.
     */
    public static final class Choice {
        private final int index;
        private final ChatMessage message;
        private final String finishReason;

        public Choice(int index, ChatMessage message, String finishReason) {
            this.index = index;
            this.message = message;
            this.finishReason = finishReason;
        }

        public int getIndex() {
            return index;
        }

        public ChatMessage getMessage() {
            return message;
        }

        public String getFinishReason() {
            return finishReason;
        }
    }

    /**
     * Represents token usage information.
     */
    public static final class Usage {
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public Usage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public static Usage of(int promptTokens, int completionTokens) {
            return new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
        }

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }
    }

    public static final class Builder {
        private String id;
        private String object = "chat.completion";
        private long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder created(long created) {
            this.created = created;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder choices(List<Choice> choices) {
            this.choices = choices;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}
