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
package com.getaxonflow.examples;

import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.authzen.Attribute;
import com.getaxonflow.sdk.authzen.AuthZENAction;
import com.getaxonflow.sdk.authzen.AuthZENDecision;
import com.getaxonflow.sdk.authzen.AuthZENErrorCode;
import com.getaxonflow.sdk.authzen.AuthZENEvaluation;
import com.getaxonflow.sdk.authzen.AuthZENEvaluationException;
import com.getaxonflow.sdk.authzen.AuthZENObligation;
import com.getaxonflow.sdk.authzen.AuthZENRefusedException;
import com.getaxonflow.sdk.authzen.AuthZENRequest;
import com.getaxonflow.sdk.authzen.AuthZENResource;
import com.getaxonflow.sdk.authzen.AuthZENSubject;
import com.getaxonflow.sdk.authzen.AuthZENUnresolvedException;
import java.util.List;

/**
 * The AuthZEN-native authorization surface, against a live agent.
 *
 * <pre>
 *   AXONFLOW_AGENT_URL=http://localhost:8080 mvn -q exec:java
 * </pre>
 *
 * <p>Set {@code AXONFLOW_CLIENT_ID} / {@code AXONFLOW_CLIENT_SECRET} for a deployment that needs
 * credentials; community mode needs none.
 *
 * <h2>Why the unhappy paths are most of this file</h2>
 *
 * <p>The surface refuses what it cannot evaluate rather than evaluating around it, and that is the
 * property an integration has to be written against. An example that only ever shows an allow
 * teaches a reader to write {@code if (decision.isAllowed())} and nothing else, and the first
 * refusal they meet in production is a string in a log.
 *
 * <p>Steps 5 to 8 are refusals - four of the nine. Each one is an outcome a real gateway hits.
 * Step 9 is the check a Policy Enforcement Point owes on the ALLOW path, which is the one people
 * forget.
 *
 * <p>The three non-refusal failure types ({@link
 * com.getaxonflow.sdk.authzen.AuthZENUnusableResponseException}, {@link
 * com.getaxonflow.sdk.authzen.AuthZENUnreadableProfileException} and {@link
 * com.getaxonflow.sdk.authzen.AuthZENTransportException}) are not demonstrated here because no
 * live server produces them on request - a stubbed transport can, and
 * {@code AuthZENSurfaceTest} does. They all extend {@link
 * com.getaxonflow.sdk.authzen.AuthZENEvaluationException}, so one catch covers the surface.
 */
public final class AuthZENExample {

  private static final int STEPS = 9;

  private AuthZENExample() {}

  /**
   * Runs the walkthrough.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    String endpoint = env("AXONFLOW_AGENT_URL", env("AXONFLOW_ENDPOINT", "http://localhost:8080"));
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(env("AXONFLOW_CLIENT_ID", "authzen-example"))
                .clientSecret(env("AXONFLOW_CLIENT_SECRET", ""))
                .build());

    System.out.println("AuthZEN surface against " + endpoint);
    System.out.println();
    int done = 0;

    // --- 1. The permitted case -------------------------------------------
    AuthZENDecision decision =
        client.evaluate(
            AuthZENEvaluation.of(gateway(), llmCompletion(), llmResource())
                .query(Attribute.known("what is our refund policy?"))
                .correlation("x-session-id", Attribute.known("sess-4711"))
                .build());
    expectAllowed(decision, true, "1. a permitted completion");
    describe(decision);
    done++;

    // --- 2. The denied case ----------------------------------------------
    //
    // isAllowed() is false AND the state says which of the three non-allowing
    // outcomes this was. A caller that branched on "not DENY" would treat a
    // CHALLENGE as permission, which is why there is no such accessor.
    decision =
        client.evaluate(
            AuthZENEvaluation.of(gateway(), llmCompletion(), llmResource())
                .query(Attribute.known("'; DROP TABLE users; --"))
                .build());
    expectAllowed(decision, false, "2. a denied completion");
    describe(decision);
    done++;

    // --- 3. Several preconditions, ONE decision ---------------------------
    //
    // Moving a ticket has to be authorized against the destination project as
    // well as against the ticket. The entries MEET: one denied entry denies the
    // operation, and the API returns one decision so there is no entry for a
    // caller to act on selectively.
    decision =
        client.evaluateAll(
            AuthZENEvaluation.over(
                    new AuthZENRequest()
                        .setResource(new AuthZENResource("tool", "jira/move_issue")),
                    new AuthZENRequest()
                        .setResource(new AuthZENResource("tool", "jira/update_project")))
                .subject(gateway())
                .action(new AuthZENAction("tool.call"))
                .query(Attribute.known("move AXN-41 to the platform project"))
                .build());
    System.out.println(
        "3. two preconditions, one decision: allowed="
            + decision.isAllowed()
            + " state="
            + decision.getState()
            + " decision_id="
            + decision.getDecisionId());
    done++;

    // --- 4. An attribute the source resolved to NOTHING -------------------
    //
    // This gateway asked its directory for the caller's department and was told
    // there is none. That is ordinary resolved data: the member is omitted and
    // the evaluation proceeds.
    AuthZENSubject absent = gateway();
    absent.getProperties().putAbsent("department");
    decision =
        client.evaluate(
            AuthZENEvaluation.of(absent, llmCompletion(), llmResource())
                .query(Attribute.known("summarise the incident report"))
                .build());
    expectAllowed(decision, true, "4. an absent attribute still evaluates");
    done++;

    // --- 5. An attribute the source COULD NOT resolve ---------------------
    //
    // The same member, one state over. The directory timed out, so nobody knows
    // whether there is a department. Sending the request without it would
    // obtain a decision that weighed every attribute except that one — and
    // report it as complete. The SDK refuses before the round trip.
    //
    // It is NOT reported as retryable, and the difference matters: the refusal
    // is frozen inside this request, so resending it reproduces the identical
    // error. Re-resolve the attribute and build a NEW request.
    AuthZENSubject unknown = gateway();
    unknown.getProperties().putUnknown("department", "the directory timed out after 2s");
    expectUnresolved(
        client,
        AuthZENEvaluation.of(unknown, llmCompletion(), llmResource())
            .query(Attribute.known("summarise the incident report"))
            .build(),
        "5. an unresolvable attribute",
        "/evaluation/subject/properties/department");
    done++;

    // --- 6. An attribute the SERVER cannot evaluate -----------------------
    //
    // The mirror image of step 5, from the other side of the wire. The surface
    // has no way to read a caller-supplied property, so it names the member
    // rather than deciding without it.
    AuthZENSubject known = gateway();
    known.getProperties().putKnown("department", "finance");
    expectRefusal(
        client,
        AuthZENEvaluation.of(known, llmCompletion(), llmResource())
            .query(Attribute.known("summarise the incident report"))
            .build(),
        "6. an attribute the surface cannot evaluate",
        AuthZENErrorCode.UNEVALUABLE_ATTRIBUTE,
        "/evaluation/subject/properties",
        false);
    done++;

    // --- 7. An action outside the surface ---------------------------------
    //
    // The refusal names what WOULD have been accepted, so a caller can correct
    // itself without reading the documentation.
    AuthZENRefusedException refused =
        expectRefusal(
            client,
            AuthZENEvaluation.of(gateway(), new AuthZENAction("database.truncate"), llmResource())
                .query(Attribute.known("anything"))
                .build(),
            "7. an action this surface does not evaluate",
            AuthZENErrorCode.UNSUPPORTED_ACTION,
            "/evaluation/action/name",
            false);
    System.out.println("     supported: " + refused.getSupported());
    done++;

    // --- 8. A refusal that never leaves the process -----------------------
    //
    // An absent subject type is not the gateway type this surface evaluates,
    // and reading it as one would let a body name any caller. The SDK says so
    // at the SAME pointer the server would, so a caller reads one diagnostic
    // whichever side produced it.
    expectRefusal(
        client,
        AuthZENEvaluation.of(
                new AuthZENSubject(null, "llm-gateway-01"), llmCompletion(), llmResource())
            .query(Attribute.known("anything"))
            .build(),
        "8. an incomplete subject, refused locally",
        AuthZENErrorCode.INCOMPLETE_EVALUATION,
        "/evaluation/subject/type",
        false);
    done++;

    // --- 9. What a PEP still owes on an ALLOW ----------------------------
    //
    // isAllowed() is necessary and not sufficient. A mandatory obligation the
    // enforcement point cannot discharge means the operation must NOT proceed,
    // and the number of integrations that stop at `if (isAllowed())` is the
    // reason this step is here rather than in a doc comment.
    decision =
        client.evaluate(
            AuthZENEvaluation.of(gateway(), llmCompletion(), llmResource())
                .query(Attribute.known("what is our refund policy?"))
                .build());
    List<AuthZENObligation> mandatory = decision.getMandatoryObligations();
    if (decision.isAllowed() && !mandatory.isEmpty()) {
      // The branch a real PEP writes: discharge every one, or block.
      System.out.println("9. allow with " + mandatory.size() + " mandatory obligation(s)");
    } else {
      System.out.println(
          "9. allow with no mandatory obligations (state="
              + decision.getState()
              + ", obligations="
              + decision.getObligations().size()
              + ")");
    }
    done++;

    // Catches a step that returned early with a value rather than an exception.
    // It does NOT catch a step deleted from this method - nothing can, short of
    // naming them - so it is a floor, not a census.
    if (done != STEPS) {
      throw new IllegalStateException("only " + done + " of " + STEPS + " steps ran");
    }
    System.out.println();
    System.out.println(done + "/" + STEPS + " steps OK");
  }

  private static AuthZENSubject gateway() {
    return new AuthZENSubject("gateway", "llm-gateway-01");
  }

  private static AuthZENAction llmCompletion() {
    return new AuthZENAction("llm.completion");
  }

  private static AuthZENResource llmResource() {
    return new AuthZENResource("llm", "llm");
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? fallback : value;
  }

  private static void describe(AuthZENDecision decision) {
    System.out.println(
        "     state="
            + decision.getState()
            + " category="
            + decision.getCategory()
            + " reason="
            + decision.getReason().map(Object::toString).orElse("-")
            + " obligations="
            + decision.getObligations().size()
            + " decision_id="
            + decision.getDecisionId());
  }

  private static void expectAllowed(AuthZENDecision decision, boolean want, String step) {
    if (decision.isAllowed() != want) {
      throw new IllegalStateException(
          step
              + ": expected allowed="
              + want
              + ", got allowed="
              + decision.isAllowed()
              + " state="
              + decision.getState());
    }
    System.out.println(step + ": allowed=" + decision.isAllowed());
  }

  private static void expectUnresolved(
      AxonFlow client, AuthZENRequest request, String step, String pointer) {
    try {
      AuthZENDecision decision = client.evaluate(request);
      throw new IllegalStateException(
          step + ": expected a refusal, got a decision (allowed=" + decision.isAllowed() + ")");
    } catch (AuthZENUnresolvedException unresolved) {
      if (!pointer.equals(unresolved.getPointer())) {
        throw new IllegalStateException(
            step + ": expected pointer " + pointer + ", got " + unresolved.getPointer());
      }
      System.out.println(
          step + ": unresolved at " + unresolved.getPointer() + " (retryable=false)");
    }
  }

  private static AuthZENRefusedException expectRefusal(
      AxonFlow client,
      AuthZENRequest request,
      String step,
      AuthZENErrorCode code,
      String pointer,
      boolean retryable) {
    try {
      AuthZENDecision decision = client.evaluate(request);
      throw new IllegalStateException(
          step + ": expected a refusal, got a decision (allowed=" + decision.isAllowed() + ")");
    } catch (AuthZENRefusedException refused) {
      if (!code.equals(refused.getCode())) {
        throw new IllegalStateException(
            step + ": expected code " + code + ", got " + refused.getCode());
      }
      if (!pointer.equals(refused.getPointer())) {
        throw new IllegalStateException(
            step + ": expected pointer " + pointer + ", got " + refused.getPointer());
      }
      if (refused.isRetryable() != retryable) {
        throw new IllegalStateException(
            step + ": expected retryable=" + retryable + ", got " + refused.isRetryable());
      }
      System.out.println(
          step
              + ": "
              + refused.getCode()
              + " at "
              + refused.getPointer()
              + " (retryable="
              + refused.isRetryable()
              + ")");
      return refused;
    } catch (AuthZENEvaluationException other) {
      throw new IllegalStateException(step + ": expected a typed refusal, got " + other, other);
    }
  }
}
