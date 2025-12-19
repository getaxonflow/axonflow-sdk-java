/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/**
 * LLM interceptors for transparent governance integration.
 *
 * <p>This package provides interceptors for wrapping LLM API calls with
 * AxonFlow governance, enabling automatic policy enforcement and audit
 * logging without requiring changes to application code.
 *
 * <h2>Supported Providers</h2>
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.interceptors.OpenAIInterceptor} - For OpenAI API calls</li>
 *   <li>{@link com.getaxonflow.sdk.interceptors.AnthropicInterceptor} - For Anthropic API calls</li>
 * </ul>
 *
 * <h2>Quick Example</h2>
 * <pre>{@code
 * // Create AxonFlow client
 * AxonFlow axonflow = AxonFlow.builder()
 *     .agentUrl("http://localhost:8080")
 *     .licenseKey("your-license-key")
 *     .build();
 *
 * // Wrap OpenAI calls
 * Function<ChatCompletionRequest, ChatCompletionResponse> wrappedCall =
 *     OpenAIInterceptor.wrapChatCompletion(axonflow, "user-token", yourOpenAIFn);
 *
 * // Use wrapped function - governance happens automatically
 * ChatCompletionResponse response = wrappedCall.apply(
 *     ChatCompletionRequest.builder()
 *         .model("gpt-4")
 *         .addUserMessage("Hello!")
 *         .build());
 * }</pre>
 *
 * @see com.getaxonflow.sdk.AxonFlow
 * @see com.getaxonflow.sdk.interceptors.OpenAIInterceptor
 * @see com.getaxonflow.sdk.interceptors.AnthropicInterceptor
 */
package com.getaxonflow.sdk.interceptors;
