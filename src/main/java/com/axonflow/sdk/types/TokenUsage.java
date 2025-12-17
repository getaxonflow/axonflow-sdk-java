/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents token usage statistics from an LLM call.
 *
 * <p>Used when auditing LLM calls in Gateway Mode to track token consumption.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TokenUsage {

    @JsonProperty("prompt_tokens")
    private final int promptTokens;

    @JsonProperty("completion_tokens")
    private final int completionTokens;

    @JsonProperty("total_tokens")
    private final int totalTokens;

    /**
     * Creates a new TokenUsage instance.
     *
     * @param promptTokens     tokens used in the prompt/input
     * @param completionTokens tokens used in the completion/output
     * @param totalTokens      total tokens used (prompt + completion)
     */
    public TokenUsage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    /**
     * Creates a TokenUsage with auto-calculated total.
     *
     * @param promptTokens     tokens used in the prompt/input
     * @param completionTokens tokens used in the completion/output
     * @return a new TokenUsage instance
     */
    public static TokenUsage of(int promptTokens, int completionTokens) {
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }

    /**
     * Returns the number of tokens used in the prompt.
     *
     * @return prompt token count
     */
    public int getPromptTokens() {
        return promptTokens;
    }

    /**
     * Returns the number of tokens used in the completion.
     *
     * @return completion token count
     */
    public int getCompletionTokens() {
        return completionTokens;
    }

    /**
     * Returns the total number of tokens used.
     *
     * @return total token count
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenUsage that = (TokenUsage) o;
        return promptTokens == that.promptTokens &&
               completionTokens == that.completionTokens &&
               totalTokens == that.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "TokenUsage{" +
               "promptTokens=" + promptTokens +
               ", completionTokens=" + completionTokens +
               ", totalTokens=" + totalTokens +
               '}';
    }
}
