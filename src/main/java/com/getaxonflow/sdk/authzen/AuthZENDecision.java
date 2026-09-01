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
import java.util.Optional;

/**
 * A decision this build could read COMPLETELY.
 *
 * <p>The type exists so that "the profile payload was there and hung together" is established once,
 * by construction, rather than re-asked at every accessor. An {@link AuthZENResponse} that failed
 * any of those checks never becomes one of these — it becomes an {@link
 * AuthZENEvaluationException}.
 */
public final class AuthZENDecision {

  private final boolean decision;
  private final AuthZENResponseContext context;

  private AuthZENDecision(boolean decision, AuthZENResponseContext context) {
    this.decision = decision;
    this.context = context;
  }

  /**
   * Checks everything that has to hold before a body becomes a decision.
   *
   * @param response the decoded response
   * @param profileHeader the header this client negotiated with, for the diagnostic
   * @return the decision
   * @throws AuthZENUnreadableProfileException if the server answered in a profile this build cannot
   *     interpret
   * @throws AuthZENUnusableResponseException if the body cannot be trusted
   */
  public static AuthZENDecision from(AuthZENResponse response, String profileHeader) {
    // AN ABSENT CONTEXT IS A BLANKED CONTEXT, NOT AN EMPTY ONE.
    //
    // The server omits the profile payload for a caller that did not negotiate
    // — and this SDK ALWAYS negotiates. So a 200 with no context is a server
    // that ignored the header or a proxy that stripped it, and the parts this
    // build cannot see are exactly the parts that constrain an allow: the
    // obligations and the approval challenge. Reading it as "no obligations"
    // leaves isAllowed() returning true and the caller proceeding on an allow
    // whose mandatory redaction it never saw.
    AuthZENResponseContext context = response.getContext();
    if (context == null) {
      throw new AuthZENUnusableResponseException(
          "the response carries no profile payload, though this request negotiated "
              + profileHeader
              + ": "
              + AuthZENContract.PROFILE_V1
              + ". The obligations and the approval challenge ride in that payload, so an allow"
              + " cannot be distinguished from an allow this client must not act on");
    }

    // A profile from a version this build does not know is REFUSED, not
    // silently dropped. It is also the case that matters at the v11 cutover,
    // which is precisely when a server starts speaking a profile an older SDK
    // does not know.
    if (!AuthZENContract.PROFILE_V1.equals(context.getProfile())) {
      throw new AuthZENUnreadableProfileException(context.getProfile(), AuthZENContract.PROFILE_V1);
    }

    // THE DECODED BODY IS VALIDATED, not assumed. Decoding establishes that the
    // members are the right SHAPE; it says nothing about a required member
    // being empty, an obligation naming no source policy, or an approval clause
    // with no eligible approvers — each of which would be read by a caller as a
    // fact about the decision.
    try {
      response.validate("");
    } catch (AuthZENRefusedException e) {
      throw new AuthZENUnusableResponseException(e.getMessage());
    }

    // The boolean and the state are two renderings of ONE outcome: the contract
    // says `decision` is true exactly when the state is ALLOW. If they disagree,
    // one of them is wrong and nothing here can tell which, so acting on either
    // is a coin flip on an authorization decision.
    //
    // This also covers a state this build does not know: an unknown state with
    // `decision: true` cannot be ALLOW as far as this build can tell, and is
    // refused rather than proceeding.
    boolean stateAllows = AuthZENOperationalState.ALLOW.equals(context.getState());
    if (stateAllows != response.getDecision()) {
      throw new AuthZENUnusableResponseException(
          "the decision boolean is "
              + response.getDecision()
              + " but the operational state is "
              + context.getState()
              + "; the contract makes them one outcome, so a body where they disagree cannot be"
              + " acted on");
    }

    return new AuthZENDecision(response.getDecision(), context);
  }

  /**
   * Whether the enforcement point may proceed.
   *
   * <p>Read this rather than comparing the state yourself. It requires BOTH the collapsed boolean
   * and the operational state to say {@code ALLOW}: exactly one state permits execution, and a
   * caller that branches on anything else — "not DENY", say — treats a CHALLENGE or an ERROR as
   * permission.
   *
   * <p>A decision whose boolean and state DISAGREE never reaches this method; it is refused as an
   * unusable response, because there is no reading of such a body that is not a guess. Which makes
   * the state conjunct here UNREACHABLE while that refusal stands. It is kept because the two
   * checks live in different methods, and this is not the one a future refactor of the decoding
   * path is likely to touch. No test kills a mutant that deletes it — that was measured, not
   * assumed — and saying so here is better than a comment implying coverage that does not exist.
   *
   * <p>An allow is not the end of it: a mandatory obligation the enforcement point cannot discharge
   * means the operation must NOT proceed. See {@link #getMandatoryObligations()}.
   *
   * @return true only when the operation may execute
   */
  public boolean isAllowed() {
    return decision && AuthZENOperationalState.ALLOW.equals(context.getState());
  }

  /**
   * The four-valued operational state.
   *
   * @return the state
   */
  public AuthZENOperationalState getState() {
    return context.getState();
  }

  /**
   * The coarse outcome category.
   *
   * @return the category
   */
  public AuthZENCategory getCategory() {
    return context.getCategory();
  }

  /**
   * The safe machine reason, when the server sent one.
   *
   * @return the reason, or empty
   */
  public Optional<AuthZENReasonCode> getReason() {
    return Optional.ofNullable(context.getReason());
  }

  /**
   * Every instruction the enforcement point must discharge.
   *
   * @return the obligations, possibly empty
   */
  public List<AuthZENObligation> getObligations() {
    List<AuthZENObligation> obligations = context.getObligations();
    return obligations == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(obligations);
  }

  /**
   * The obligations that must be discharged for the allow to stand.
   *
   * <p>An allow with an undischarged mandatory obligation is not an allow. A caller that cannot
   * discharge one must block.
   *
   * @return the mandatory obligations, possibly empty
   */
  public List<AuthZENObligation> getMandatoryObligations() {
    List<AuthZENObligation> out = new ArrayList<>();
    for (AuthZENObligation obligation : getObligations()) {
      // Boolean.TRUE.equals rather than an unbox: validation guarantees the
      // member is present before a decision exists, and an NPE here on the day
      // that stops being true would be a worse failure than reading a missing
      // flag as "not mandatory" - which is what the validator is there to stop
      // reaching this line at all.
      if (Boolean.TRUE.equals(obligation.getMandatory())) {
        out.add(obligation);
      }
    }
    return Collections.unmodifiableList(out);
  }

  /**
   * The approval challenge the contract declares for a {@code CHALLENGE} state.
   *
   * <p><b>NO DEPLOYED SERVER POPULATES THIS TODAY.</b> The v10 route is an adapter over the legacy
   * evaluation, and its handler builds the response context without an {@code approval} member - so
   * a CHALLENGE arrives with this empty, and a caller that writes {@code
   * decision.getApproval().get()} throws on its first real challenge.
   *
   * <p>It is surfaced because the contract declares it and the ADR-065 Policy Decision Point fills
   * it at v11. Until then, treat an empty approval on a CHALLENGE as the normal case and read
   * {@link #getState()} and {@link #getCategory()} instead. A CHALLENGE with no approval is
   * deliberately NOT refused: the shipped server produces exactly that, and refusing it would break
   * every real challenge in the name of a member no server sends.
   *
   * @return the approval requirement, or empty - which is the usual case today
   */
  public Optional<AuthZENApprovalRequirement> getApproval() {
    return Optional.ofNullable(context.getApproval());
  }

  /**
   * The id of the entry that DETERMINED the outcome.
   *
   * <p>For a plural envelope this names the entry that decided the meet, not the last one evaluated
   * — it is the id an operator looks up to explain the outcome.
   *
   * @return the decision id
   */
  public String getDecisionId() {
    return context.getDecisionId();
  }

  /**
   * The contract version the server evaluated under.
   *
   * @return the schema version
   */
  public String getSchemaVersion() {
    return context.getSchemaVersion();
  }

  /**
   * The whole profile payload, for a caller that wants a member this type does not surface.
   *
   * @return the response context, never null
   */
  public AuthZENResponseContext getContext() {
    return context;
  }

  @Override
  public String toString() {
    return "AuthZENDecision{allowed="
        + isAllowed()
        + ", state="
        + getState()
        + ", category="
        + getCategory()
        + ", decisionId="
        + getDecisionId()
        + "}";
  }
}
