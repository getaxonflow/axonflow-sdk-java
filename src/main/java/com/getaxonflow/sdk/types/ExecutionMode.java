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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Execution mode for multi-agent plan execution.
 *
 * <p>Controls how plan steps are scheduled and executed:
 *
 * <ul>
 *   <li>{@link #AUTO} - Let the engine determine optimal execution order
 *   <li>{@link #SEQUENTIAL} - Execute steps strictly in order
 *   <li>{@link #PARALLEL} - Execute independent steps concurrently
 *   <li>{@link #BALANCED} - Balance between parallelism and resource usage
 *   <li>{@link #CONFIRM} - Pause before each step for user confirmation
 *   <li>{@link #STEP} - Execute one step at a time with manual advancement
 * </ul>
 */
public enum ExecutionMode {

  /** Let the engine determine optimal execution order. */
  AUTO("auto"),

  /** Execute steps strictly in order. */
  SEQUENTIAL("sequential"),

  /** Execute independent steps concurrently. */
  PARALLEL("parallel"),

  /** Balance between parallelism and resource usage. */
  BALANCED("balanced"),

  /** Pause before each step for user confirmation. */
  CONFIRM("confirm"),

  /** Execute one step at a time with manual advancement. */
  STEP("step");

  private final String value;

  ExecutionMode(String value) {
    this.value = value;
  }

  /**
   * Returns the string value used in API requests.
   *
   * @return the execution mode value as a string
   */
  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Parses a string value to an ExecutionMode enum.
   *
   * @param value the string value to parse
   * @return the corresponding ExecutionMode
   * @throws IllegalArgumentException if the value is not recognized
   */
  public static ExecutionMode fromValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Execution mode cannot be null");
    }
    for (ExecutionMode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }
    throw new IllegalArgumentException("Unknown execution mode: " + value);
  }
}
