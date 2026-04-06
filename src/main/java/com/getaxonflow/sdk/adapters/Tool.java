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
 * Framework-agnostic interface for any callable tool.
 *
 * <p>Implement this interface to wrap your tools with AxonFlow governance via {@link GovernedTool}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Tool searchTool = new Tool() {
 *     public String name() { return "web_search"; }
 *     public String description() { return "Search the web"; }
 *     public Object invoke(Object input) { return webSearch(input.toString()); }
 * };
 *
 * GovernedTool governed = GovernedTool.wrap(searchTool, axonflow);
 * Object result = governed.invoke("latest AI research");
 * }</pre>
 */
public interface Tool {

  /** Returns the tool name, used as the default connector type for policy checks. */
  String name();

  /** Returns a human-readable description of what this tool does. */
  String description();

  /**
   * Invokes the tool with the given input.
   *
   * @param input the tool input (may be a String, Map, or any serializable object)
   * @return the tool result
   * @throws Exception if the tool invocation fails
   */
  Object invoke(Object input) throws Exception;
}
