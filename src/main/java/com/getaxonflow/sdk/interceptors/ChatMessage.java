/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.interceptors;

import java.util.Objects;

/**
 * Represents a chat message for LLM calls.
 * Works with both OpenAI and Anthropic-style message formats.
 */
public final class ChatMessage {
    private final String role;
    private final String content;

    private ChatMessage(String role, String content) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * Creates a new chat message.
     *
     * @param role    the role (e.g., "user", "assistant", "system")
     * @param content the message content
     * @return a new ChatMessage
     */
    public static ChatMessage of(String role, String content) {
        return new ChatMessage(role, content);
    }

    /**
     * Creates a user message.
     *
     * @param content the message content
     * @return a new ChatMessage with role "user"
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * Creates an assistant message.
     *
     * @param content the message content
     * @return a new ChatMessage with role "assistant"
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * Creates a system message.
     *
     * @param content the message content
     * @return a new ChatMessage with role "system"
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return Objects.equals(role, that.role) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "ChatMessage{role='" + role + "', content='" +
               (content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }
}
