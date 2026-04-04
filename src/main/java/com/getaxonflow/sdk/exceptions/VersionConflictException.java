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
package com.getaxonflow.sdk.exceptions;

/**
 * Thrown when a plan update fails due to a version conflict (HTTP 409).
 *
 * <p>This indicates that the plan was modified by another client between the time it was read and
 * the time the update was attempted. The caller should re-read the plan, resolve any conflicts, and
 * retry with the updated version number.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *     axonflow.updatePlan(planId, request);
 * } catch (VersionConflictException e) {
 *     System.out.println("Conflict on plan: " + e.getPlanId());
 *     System.out.println("Expected version: " + e.getExpectedVersion());
 *     System.out.println("Current version: " + e.getCurrentVersion());
 *     // Re-read and retry
 * }
 * }</pre>
 */
public class VersionConflictException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String planId;
  private final int expectedVersion;
  private final Integer currentVersion;

  /**
   * Creates a new VersionConflictException.
   *
   * @param message the error message
   * @param planId the plan that had the conflict
   * @param expectedVersion the version the client expected
   * @param currentVersion the actual current version on the server, or null if unknown
   */
  public VersionConflictException(
      String message, String planId, int expectedVersion, Integer currentVersion) {
    super(message, 409, "VERSION_CONFLICT");
    this.planId = planId;
    this.expectedVersion = expectedVersion;
    this.currentVersion = currentVersion;
  }

  /**
   * Returns the plan ID that had the version conflict.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the version the client expected.
   *
   * @return the expected version number
   */
  public int getExpectedVersion() {
    return expectedVersion;
  }

  /**
   * Returns the actual current version on the server.
   *
   * @return the current version, or null if unknown
   */
  public Integer getCurrentVersion() {
    return currentVersion;
  }
}
