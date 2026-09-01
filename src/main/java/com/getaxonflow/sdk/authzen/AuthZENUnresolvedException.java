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
package com.getaxonflow.sdk.authzen;

/**
 * The request cannot be SENT as built: it carries an attribute the caller could not resolve.
 *
 * <p>Separate from {@link AuthZENRefusedException}, and NOT retryable, because the two need opposite
 * actions from the caller. A server {@code evaluation_unavailable} says "send these bytes again";
 * this says "re-resolve the attribute and build a NEW request". Reporting it as retryable — which an
 * earlier version of this SDK did — sends a {@code while (e.isRetryable())} loop against a request
 * whose refusal is frozen inside it, so every attempt produces the identical error until the budget
 * runs out.
 *
 * <p>The OPERATION may well succeed once the attribute resolves. That is a statement about a
 * different request, and it is why this carries the pointer and the reason rather than a boolean.
 */
public final class AuthZENUnresolvedException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  private final String pointer;
  private final String reason;

  /**
   * @param pointer the JSON Pointer naming the member nobody could resolve
   * @param reason the refusal message, which carries the reason the caller gave
   */
  public AuthZENUnresolvedException(String pointer, String reason) {
    super(
        "this request cannot be sent as built. At "
            + pointer
            + ": "
            + reason
            + " Re-resolve the attribute and build a NEW request; resending this one cannot"
            + " succeed.");
    this.pointer = pointer;
    this.reason = reason;
  }

  /**
   * The JSON Pointer naming the member nobody could resolve.
   *
   * @return the pointer
   */
  public String getPointer() {
    return pointer;
  }

  /**
   * The refusal message, which carries the reason the caller gave.
   *
   * @return the reason
   */
  public String getReason() {
    return reason;
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}
