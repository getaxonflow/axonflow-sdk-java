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
 * The server answered in a profile this build cannot interpret.
 *
 * <p>NOT retryable, and deliberately not a {@link AuthZENRefusedException}: {@code
 * evaluation_unavailable} is the refusal enumeration's one retryable code, and reporting "upgrade
 * the SDK" through it would send a client into a retry loop against a server that will answer
 * identically every time.
 *
 * <p>The parts this build cannot read are exactly the parts that constrain an allow — the
 * obligations and the approval challenge — so the decision cannot be acted on safely. This is the
 * case that matters at the v11 cutover, which is precisely when a server starts speaking a profile
 * an older SDK does not know.
 */
public final class AuthZENUnreadableProfileException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  private final String received;
  private final String understood;

  /**
   * @param received the profile the server named
   * @param understood the profile this build can read
   */
  public AuthZENUnreadableProfileException(String received, String understood) {
    super(
        "the server answered with AuthZEN profile \""
            + received
            + "\"; this build can only interpret \""
            + understood
            + "\". The obligations and approval challenge that constrain an allow are carried in"
            + " that payload, so the decision cannot be acted on safely. Upgrade the SDK.");
    this.received = received;
    this.understood = understood;
  }

  /**
   * What the server said it was speaking.
   *
   * @return the profile the server named
   */
  public String getReceived() {
    return received;
  }

  /**
   * What this build can read.
   *
   * @return the profile this build understands
   */
  public String getUnderstood() {
    return understood;
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}
