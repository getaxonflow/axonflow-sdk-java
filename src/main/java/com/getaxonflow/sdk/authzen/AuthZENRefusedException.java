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

import java.util.List;

/**
 * The request was refused rather than evaluated — by the server, or by this client before the round
 * trip.
 *
 * <p>Both sides speak the same code and pointer vocabulary, so a caller branches on one thing
 * rather than two, and a local refusal names the same member the server would have named for the
 * same bytes.
 *
 * <p>A refusal is NOT a denial. A response carrying {@code decision: false} says the request was
 * evaluated and denied; a refusal says it was never evaluated. Returning one for the other would
 * make "denied" and "unevaluable" the same event in every audit and every client branch.
 */
public final class AuthZENRefusedException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  private final transient AuthZENError refusal;

  /**
   * Wraps a refusal document.
   *
   * @param refusal the structured refusal, from the server or from local validation
   */
  public AuthZENRefusedException(AuthZENError refusal) {
    super(render(refusal));
    this.refusal = refusal;
  }

  /**
   * Builds a local refusal in the server's own vocabulary.
   *
   * @param code the refusal code
   * @param pointer the JSON Pointer naming the member at fault
   * @param message what is wrong with it
   * @return the exception to throw
   */
  public static AuthZENRefusedException of(AuthZENErrorCode code, String pointer, String message) {
    AuthZENError error = new AuthZENError(code, message);
    if (pointer != null && !pointer.isEmpty()) {
      error.setPointer(pointer);
    }
    return new AuthZENRefusedException(error);
  }

  private static String render(AuthZENError refusal) {
    if (refusal == null) {
      return "axonflow: the request was refused";
    }
    String pointer = refusal.getPointer();
    if (pointer == null || pointer.isEmpty()) {
      return "axonflow: " + refusal.getCode() + ": " + refusal.getMessage();
    }
    return "axonflow: " + refusal.getCode() + " at " + pointer + ": " + refusal.getMessage();
  }

  /**
   * The structured refusal.
   *
   * @return the refusal document, never null
   */
  public AuthZENError getRefusal() {
    return refusal;
  }

  /**
   * The refusal code, for branching.
   *
   * @return the code
   */
  public AuthZENErrorCode getCode() {
    return refusal.getCode();
  }

  /**
   * The JSON Pointer naming the member at fault.
   *
   * <p>{@code unsupported_action} without the offending member is a puzzle rather than a diagnosis,
   * which is why the server never sends one without a pointer and neither does this SDK.
   *
   * @return the pointer, or null when the refusal is about the request as a whole
   */
  public String getPointer() {
    return refusal.getPointer();
  }

  /**
   * The values that WOULD have been accepted.
   *
   * @return the supported values, possibly empty
   */
  public List<String> getSupported() {
    return refusal.getSupported();
  }

  @Override
  public boolean isRetryable() {
    return AuthZENRefusals.isRetryable(refusal.getCode());
  }
}
