/*
 * Copyright 2026 AxonFlow
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
package com.getaxonflow.sdk.adapters;

/**
 * Functional interface for handling an MCP tool request.
 *
 * <p>Implementations execute the actual tool call and return the result.
 * Used by {@link MCPToolInterceptor} as the downstream handler.
 */
@FunctionalInterface
public interface MCPToolHandler {

    /**
     * Handles an MCP tool request.
     *
     * @param request the tool request to handle
     * @return the result of the tool invocation
     * @throws Exception if the tool call fails
     */
    Object handle(MCPToolRequest request) throws Exception;
}
