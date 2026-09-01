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
 * The server answered {@code 200} with a body this build will not act on.
 *
 * <p>A decision that cannot be read completely is not a decision. Acting on the half that parsed is
 * how an allow carrying a mandatory obligation reaches an enforcement point that never saw it.
 *
 * <p>Never retryable: a server that produced an unreadable body once will produce the same one
 * again, and the fix is on its side.
 */
public final class AuthZENUnusableResponseException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  /**
   * @param detail what about the body could not be trusted
   */
  public AuthZENUnusableResponseException(String detail) {
    super("the server's decision cannot be acted on: " + detail);
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}
