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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The request was refused rather than evaluated — by the server, or by this client before the round
 * trip.
 *
 * <p>Both name the same MEMBER: a local refusal carries the JSON Pointer the server would have sent
 * for the same bytes, verified against a live server by {@code runtime-e2e/authzen_evaluation}.
 *
 * <p>The CODE may be narrower on the server side, and that is not a defect in either. This client
 * knows only that a required member is missing, and says {@code incomplete_evaluation}; the server
 * additionally knows which values it can evaluate, and narrows the same condition to {@code
 * unsupported_subject} with a {@code supported} list. Branch on the pointer for "which member", and
 * read the code as the server's more specific reading when there is one.
 *
 * <p>A refusal is NOT a denial. A response carrying {@code decision: false} says the request was
 * evaluated and denied; a refusal says it was never evaluated. Returning one for the other would
 * make "denied" and "unevaluable" the same event in every audit and every client branch.
 */
public final class AuthZENRefusedException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  private final transient AuthZENError refusal;

  // The three members a caller branches on, held as plain fields so they SURVIVE
  // Java serialization. `refusal` is transient because the generated wire types
  // are not Serializable; without these, a deserialized refusal NPE'd on
  // getCode(), getPointer(), getSupported() and isRetryable() - every accessor a
  // caller has.
  private final String code;
  private final String pointer;
  private final List<String> supported;

  /**
   * Wraps a refusal document.
   *
   * @param refusal the structured refusal, from the server or from local validation
   */
  public AuthZENRefusedException(AuthZENError refusal) {
    super(render(Objects.requireNonNull(refusal, "a refusal is not null")));
    this.refusal = refusal;
    this.code = refusal.getCode() == null ? "" : refusal.getCode().value();
    this.pointer = refusal.getPointer();
    this.supported =
        refusal.getSupported() == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(refusal.getSupported()));
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
   * <p>Null after Java serialization - the generated wire types are not {@code Serializable}. The
   * members a caller branches on ({@link #getCode()}, {@link #getPointer()}, {@link
   * #getSupported()}, {@link #isRetryable()}) survive it; this accessor is for the full document.
   *
   * @return the refusal document, or null on a deserialized exception
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
    return AuthZENErrorCode.of(code);
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
    return pointer;
  }

  /**
   * The values that WOULD have been accepted.
   *
   * @return the supported values, possibly empty
   */
  public List<String> getSupported() {
    return supported;
  }

  @Override
  public boolean isRetryable() {
    return AuthZENRefusals.isRetryable(getCode());
  }
}
