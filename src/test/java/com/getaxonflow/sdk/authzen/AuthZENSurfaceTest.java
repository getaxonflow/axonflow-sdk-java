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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the AuthZEN surface, against a stubbed transport.
 *
 * <p>These are unit-level: they pin what the CLIENT does with a given body, which a live stack
 * cannot vary on demand — there is no way to ask a real server for a decision whose boolean and
 * state disagree. The proof that the surface works against the real thing lives in {@code
 * runtime-e2e/authzen_evaluation/}, and neither replaces the other.
 */
class AuthZENSurfaceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private WireMockServer server;
  private AxonFlow client;

  @BeforeEach
  void start() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    client =
        AxonFlow.create(
            AxonFlowConfig.builder().endpoint("http://localhost:" + server.port()).build());
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  // -------------------------------------------------------------------------
  // Fixtures
  // -------------------------------------------------------------------------

  private void answering(int status, String body) {
    server.stubFor(
        post(urlEqualTo(AxonFlow.AUTHZEN_PATH))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private JsonNode sentBody() throws IOException {
    List<LoggedRequest> requests =
        server.findRequestsMatching(postRequestedFor(urlEqualTo(AxonFlow.AUTHZEN_PATH)).build())
            .getRequests();
    assertThat(requests).as("a request was sent").isNotEmpty();
    return MAPPER.readTree(requests.get(0).getBody());
  }

  private int requestCount() {
    return server
        .findRequestsMatching(postRequestedFor(urlEqualTo(AxonFlow.AUTHZEN_PATH)).build())
        .getRequests()
        .size();
  }

  private static AuthZENRequest aRequest() {
    return AuthZENEvaluation.of(
            new AuthZENSubject("gateway", "llm-gateway-01"),
            new AuthZENAction("llm.completion"),
            new AuthZENResource("llm", "llm"))
        .query(Attribute.known("what is our refund policy?"))
        .build();
  }

  private static AuthZENRequest requestWith(AuthZENSubject subject) {
    return AuthZENEvaluation.of(
            subject, new AuthZENAction("llm.completion"), new AuthZENResource("llm", "llm"))
        .query(Attribute.known("hello"))
        .build();
  }

  private static String allowBody() {
    return "{\"decision\":true,\"context\":{"
        + "\"profile\":\""
        + AuthZENContract.PROFILE_V1
        + "\",\"state\":\"ALLOW\",\"category\":\"allowed\",\"reason\":\"permitted\","
        + "\"decision_id\":\"dec-1\",\"schema_version\":\"2026-08-29\"}}";
  }

  private static String denyBody() {
    return "{\"decision\":false,\"context\":{"
        + "\"profile\":\""
        + AuthZENContract.PROFILE_V1
        + "\",\"state\":\"DENY\",\"category\":\"not_permitted\","
        + "\"reason\":\"explicit_constraint\",\"decision_id\":\"dec-2\","
        + "\"schema_version\":\"2026-08-29\"}}";
  }

  // -------------------------------------------------------------------------
  // The three-valued attribute: the whole point of this lane
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("an ABSENT attribute is omitted and the request is evaluated")
  void absentAttributeIsOmittedAndEvaluated() throws IOException {
    // ABSENT is resolved data. The identity provider answered: this caller has
    // no department. A decision made without a fact that has no value is a
    // complete decision, so the request goes.
    answering(200, allowBody());
    AuthZENSubject subject = new AuthZENSubject("gateway", "llm-gateway-01");
    subject.getProperties().putAbsent("department");

    AuthZENDecision decision = client.evaluate(requestWith(subject));

    assertThat(decision.isAllowed()).isTrue();
    // The bag itself is still sent — the caller DID address `department`, and
    // an empty object says "I looked, there is nothing". What must not appear
    // is the member: a `null` there would assert a JSON null VALUE, which is a
    // value, and the server's context members are typed.
    assertThat(sentBody().at("/evaluation/subject/properties").toString())
        .as("an absent member must be omitted from the bag, not sent as null")
        .isEqualTo("{}");
  }

  @Test
  @DisplayName("an UNKNOWN attribute refuses the request before it is sent")
  void unknownAttributeRefusesBeforeSending() {
    // UNKNOWN is a failure to resolve. Sending the request without the member
    // would obtain a decision that weighed every attribute except the one
    // nobody could read — and report it as complete.
    answering(200, allowBody());
    AuthZENSubject subject = new AuthZENSubject("gateway", "llm-gateway-01");
    subject.getProperties().putUnknown("department", "the directory timed out");

    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(requestWith(subject)));

    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/subject/properties/department");
    assertThat(unresolved.getReason())
        .as("the reason the source gave must reach the operator")
        .contains("the directory timed out");
    // NOT retryable, and the distinction is the point. isRetryable() answers
    // "could sending THIS request again produce a different answer", and the
    // refusal is frozen inside the request: every resend reproduces it. The
    // operation may succeed once the attribute resolves, which is a different
    // request. An earlier version reported it retryable, which sends a
    // `while (isRetryable())` loop through its whole budget.
    assertThat(unresolved.isRetryable()).isFalse();
    assertThat(requestCount())
        .as("the request reached the server despite carrying an unresolvable attribute")
        .isZero();
  }

  @Test
  @DisplayName("absent and unknown are not the same outcome")
  void absentAndUnknownDiffer() {
    // The fixture that fails if the two states are collapsed. With a nullable
    // value in place of Attribute both of these are null, both take the same
    // branch, and exactly one of the two assertions below is wrong whichever
    // way that branch is written.
    //
    // The unknown side asserts the STAGE, not merely that something failed:
    // AttributeMap has TWO guards on this property — validate() before the
    // round trip and a serializer that refuses to encode an unknown — and a
    // test that only asked "did it throw" would pass with the first removed,
    // because the second catches the mutant and throws something wronger.
    answering(200, allowBody());

    AuthZENSubject absent = new AuthZENSubject("gateway", "g1");
    absent.getProperties().putAbsent("department");
    AuthZENDecision decision = client.evaluate(requestWith(absent));
    assertThat(decision.isAllowed()).as("an absent attribute must produce a decision").isTrue();

    AuthZENSubject unknown = new AuthZENSubject("gateway", "g1");
    unknown.getProperties().putUnknown("department", "idp down");
    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(requestWith(unknown)));
    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/subject/properties/department");
    assertThat(unresolved.isRetryable()).isFalse();
  }

  @Test
  @DisplayName("a KNOWN attribute reaches the wire")
  void knownAttributeReachesTheWire() throws IOException {
    // The third state, so the test above cannot pass by refusing everything.
    answering(200, allowBody());
    AuthZENSubject subject = new AuthZENSubject("gateway", "g1");
    subject.getProperties().putKnown("department", "finance");

    client.evaluate(requestWith(subject));

    assertThat(sentBody().at("/evaluation/subject/properties/department").asText())
        .isEqualTo("finance");
  }

  @Test
  @DisplayName("an unresolvable nested leaf is named by the leaf, not the bag")
  void unresolvableNestedLeafNamesTheLeaf() {
    // `context.correlation.x-session-id` is a leaf two levels down. A refusal
    // pointing at `/evaluation/context/correlation` tells an operator to go
    // looking through an object rather than at a member, which is most of the
    // reason the bag nests at all.
    answering(200, allowBody());
    AuthZENRequest request =
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "g1"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.known("hello"))
            .correlation("x-session-id", Attribute.unknown("the trace header was unreadable"))
            .build();

    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(request));
    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/context/correlation/x-session-id");
  }

  @Test
  @DisplayName("an unknown attribute has no wire representation at all")
  void unknownAttributeCannotBeEncoded() {
    // The backstop underneath the validator. A future code path that encoded an
    // envelope without validating it first must not be able to drop the
    // unresolved member quietly, so there is no encoding of "unknown" to fall
    // back to.
    AttributeMap bag = new AttributeMap();
    bag.putUnknown("department", "idp down");
    assertThatThrownBy(() -> MAPPER.writeValueAsString(bag)).hasMessageContaining("department");
  }

  @Test
  @DisplayName("a JSON object normalises into a nested bag so equality survives a round trip")
  void jsonObjectsNormaliseIntoNestedBags() throws IOException {
    AttributeValue fromJson = AttributeValue.of(MAPPER.readTree("{\"a\":{\"b\":1}}"));
    AttributeMap inner = new AttributeMap().putKnown("b", AttributeValue.of(1L));
    AttributeMap outer = new AttributeMap().putKnown("a", inner);
    assertThat(fromJson).isEqualTo(AttributeValue.of(outer));

    AttributeMap bag = new AttributeMap().put("a", Attribute.known(fromJson));
    String encoded = MAPPER.writeValueAsString(bag);
    assertThat(MAPPER.readValue(encoded, AttributeMap.class)).isEqualTo(bag);
  }

  @Test
  @DisplayName("fold sees all three states")
  void foldSeesAllThreeStates() {
    assertThat(Attribute.known("x").fold(v -> "known", () -> "absent", why -> "unknown"))
        .isEqualTo("known");
    assertThat(Attribute.absent().fold(v -> "known", () -> "absent", why -> "unknown"))
        .isEqualTo("absent");
    assertThat(Attribute.unknown("why").fold(v -> "known", () -> "absent", why -> "unknown"))
        .isEqualTo("unknown");
  }

  // -------------------------------------------------------------------------
  // The wire shape
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("the envelope is exactly the members the caller set")
  void theEnvelopeCarriesOnlyWhatWasSet() throws IOException {
    answering(200, allowBody());
    client.evaluate(
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "llm-gateway-01"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.known("what is our refund policy?"))
            .correlation("x-session-id", Attribute.known("sess-1"))
            .build());

    JsonNode expected =
        MAPPER.readTree(
            "{\"evaluation\":{"
                + "\"subject\":{\"type\":\"gateway\",\"id\":\"llm-gateway-01\"},"
                + "\"action\":{\"name\":\"llm.completion\"},"
                + "\"resource\":{\"type\":\"llm\",\"id\":\"llm\"},"
                + "\"context\":{\"args\":{\"query\":\"what is our refund policy?\"},"
                + "\"correlation\":{\"x-session-id\":\"sess-1\"}}}}");
    assertThat(sentBody())
        .as("the envelope carries a member the caller did not set, or is missing one they did")
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("every request negotiates the profile")
  void everyRequestNegotiatesTheProfile() {
    // Without the header the server answers with the bare boolean, and this SDK
    // refuses a body with no profile payload — so a dropped header would turn
    // every call into an unusable response rather than a silent downgrade. The
    // header is asserted anyway, because "it fails loudly" is a worse guarantee
    // than "it is sent".
    answering(200, allowBody());
    client.evaluate(aRequest());
    server.verify(
        postRequestedFor(urlEqualTo(AxonFlow.AUTHZEN_PATH))
            .withHeader(AxonFlow.AUTHZEN_PROFILE_HEADER, equalTo(AuthZENContract.PROFILE_V1)));
  }

  @Test
  @DisplayName("a bulk envelope yields one decision over a shared base")
  void bulkYieldsOneDecision() throws IOException {
    answering(200, denyBody());
    AuthZENDecision decision =
        client.evaluateAll(
            AuthZENEvaluation.over(
                    new AuthZENRequest().setResource(new AuthZENResource("tool", "jira/move")),
                    new AuthZENRequest().setResource(new AuthZENResource("tool", "jira/update")))
                .subject(new AuthZENSubject("gateway", "g1"))
                .action(new AuthZENAction("tool.call"))
                .query(Attribute.known("move the ticket"))
                .build());

    assertThat(decision.isAllowed()).isFalse();
    assertThat(decision.getState()).isEqualTo(AuthZENOperationalState.DENY);
    assertThat(sentBody().at("/evaluations/evaluations")).hasSize(2);
    assertThat(sentBody().at("/evaluations/action/name").asText()).isEqualTo("tool.call");
  }

  // -------------------------------------------------------------------------
  // Local validation, in the same vocabulary the server uses
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("an envelope carrying both members is malformed")
  void bothEnvelopeMembersIsMalformed() {
    AuthZENEnvelope envelope =
        new AuthZENEnvelope()
            .setEvaluation(aRequest())
            .setEvaluations(new AuthZENBulk(Collections.singletonList(aRequest())));
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> envelope.validate(""));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.MALFORMED_ENVELOPE);
  }

  @Test
  @DisplayName("an envelope carrying neither member is malformed")
  void neitherEnvelopeMemberIsMalformed() {
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> new AuthZENEnvelope().validate(""));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.MALFORMED_ENVELOPE);
  }

  @Test
  @DisplayName("a singular evaluation must carry its own subject, action and resource")
  void singularEvaluationNeedsAllThree() {
    // It has no shared base to inherit from, and the pointer names which member
    // is missing rather than saying the evaluation is incomplete.
    List<AuthZENRequest> incomplete =
        Arrays.asList(
            new AuthZENRequest()
                .setAction(new AuthZENAction("llm.completion"))
                .setResource(new AuthZENResource("llm", "llm")),
            new AuthZENRequest()
                .setSubject(new AuthZENSubject("gateway", "g"))
                .setResource(new AuthZENResource("llm", "llm")),
            new AuthZENRequest()
                .setSubject(new AuthZENSubject("gateway", "g"))
                .setAction(new AuthZENAction("llm.completion")));
    List<String> pointers =
        Arrays.asList("/evaluation/subject", "/evaluation/action", "/evaluation/resource");

    for (int i = 0; i < incomplete.size(); i++) {
      AuthZENEnvelope envelope = new AuthZENEnvelope().setEvaluation(incomplete.get(i));
      AuthZENRefusedException refused =
          assertThrows(AuthZENRefusedException.class, () -> envelope.validate(""));
      assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.INCOMPLETE_EVALUATION);
      assertThat(refused.getPointer()).isEqualTo(pointers.get(i));
    }
  }

  @Test
  @DisplayName("a subject with no type is refused at the member the server would name")
  void subjectWithNoTypeIsRefusedAtTheSamePointer() {
    // The wave's sharpest defect, from the client's side: an absent `type` is
    // not the one type the surface evaluates. The server refuses this at
    // `/evaluation/subject/type`, and so does this — the same pointer, so a
    // caller reads one diagnostic whichever side produced it.
    AuthZENEnvelope envelope =
        new AuthZENEnvelope()
            .setEvaluation(
                AuthZENEvaluation.of(
                        new AuthZENSubject(null, "g1"),
                        new AuthZENAction("llm.completion"),
                        new AuthZENResource("llm", "llm"))
                    .query(Attribute.known("hello"))
                    .build());
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> envelope.validate(""));
    assertThat(refused.getPointer()).isEqualTo("/evaluation/subject/type");
    assertThat(refused.isRetryable()).isFalse();
    // The CODE is pinned too, and it is NOT the server's for these bytes: the
    // live server answers `unsupported_subject` here, because it additionally
    // knows the supported set and narrows the same condition. This client knows
    // only that a required member is missing. Both name the same MEMBER, which
    // is the property a caller branches on; asserting the code here is what
    // stops the local one drifting silently.
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.INCOMPLETE_EVALUATION);
  }

  @Test
  @DisplayName("a bulk envelope with no entries is malformed, not a request for no decisions")
  void emptyBulkIsMalformed() {
    AuthZENEnvelope envelope =
        new AuthZENEnvelope().setEvaluations(new AuthZENBulk(Collections.emptyList()));
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> envelope.validate(""));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.MALFORMED_ENVELOPE);
    assertThat(refused.getPointer()).isEqualTo("/evaluations/evaluations");
  }

  // -------------------------------------------------------------------------
  // Reading the answer
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a 200 with no profile payload is not an allow")
  void aBareBooleanIsNotAnAllow() {
    // The SDK always negotiates, so an absent context is a BLANKED context: the
    // obligations and the approval challenge that constrain an allow are
    // exactly what is missing. Reading it as "no obligations" is the fail-open.
    answering(200, "{\"decision\":true}");
    AuthZENUnusableResponseException e =
        assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
    assertThat(e.isRetryable()).isFalse();
    assertThat(e.getMessage()).contains("no profile payload");
  }

  @Test
  @DisplayName("a decision whose boolean and state disagree is refused both ways")
  void disagreeingBooleanAndStateAreRefused() {
    answering(200, allowBody().replace("\"ALLOW\"", "\"DENY\""));
    assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));

    answering(200, denyBody().replace("\"DENY\"", "\"ALLOW\""));
    assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
  }

  @Test
  @DisplayName("an operational state this build cannot read never becomes an allow")
  void unknownStateWithATrueBooleanIsRefused() {
    answering(200, allowBody().replace("\"ALLOW\"", "\"QUARANTINE\""));
    assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
  }

  @Test
  @DisplayName("an unreadable state on a denial stays a denial")
  void unknownStateOnADenialStaysADenial() {
    // The other direction, which must NOT be an error: the server collapsed a
    // state this build does not know to false, and "not allowed" is a reading
    // this build can act on safely.
    answering(200, denyBody().replace("\"DENY\"", "\"QUARANTINE\""));
    AuthZENDecision decision = client.evaluate(aRequest());
    assertThat(decision.isAllowed()).isFalse();
    assertThat(decision.getState().isKnown()).isFalse();
  }

  @Test
  @DisplayName("a profile this build cannot interpret is refused and is not retryable")
  void unreadableProfileIsRefused() {
    answering(
        200, allowBody().replace(AuthZENContract.PROFILE_V1, "axonflow-authzen-profile-2099-01-01"));
    AuthZENUnreadableProfileException e =
        assertThrows(AuthZENUnreadableProfileException.class, () -> client.evaluate(aRequest()));
    assertThat(e.getReceived()).isEqualTo("axonflow-authzen-profile-2099-01-01");
    assertThat(e.isRetryable())
        .as("retrying cannot make an older SDK able to read a newer profile")
        .isFalse();
  }

  @Test
  @DisplayName("an unknown member in a decision is refused rather than partly read")
  void unknownResponseMemberIsRefused() {
    answering(
        200,
        allowBody().replace("\"schema_version\"", "\"quarantine_until\":\"2099\",\"schema_version\""));
    assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
  }

  @Test
  @DisplayName("a decision missing a required member is refused by validation, not by decoding")
  void emptyRequiredMemberIsRefusedByValidation() {
    // Decoding establishes the SHAPE. A `decision_id` present but empty decodes
    // happily and is read by a caller as the id to look up.
    answering(200, allowBody().replace("\"dec-1\"", "\"\""));
    AuthZENUnusableResponseException e =
        assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
    assertThat(e.getMessage()).contains("decision_id");
  }

  @Test
  @DisplayName("an allow surfaces its obligations and which of them are mandatory")
  void obligationsAreSurfaced() {
    String body =
        allowBody()
            .replace(
                "\"schema_version\":\"2026-08-29\"",
                "\"schema_version\":\"2026-08-29\",\"obligations\":["
                    + "{\"type\":\"field_redact\",\"target\":\"args.query\","
                    + "\"params\":{\"fulfillment_endpoint\":\"/api/v1/mcp/check-input\"},"
                    + "\"mandatory\":true,\"source_policy\":\"legacy:redact_pii\","
                    + "\"schema_version\":1},"
                    + "{\"type\":\"notification\",\"mandatory\":false,"
                    + "\"source_policy\":\"policy:notify\",\"schema_version\":1}]");
    answering(200, body);

    AuthZENDecision decision = client.evaluate(aRequest());
    assertThat(decision.isAllowed()).isTrue();
    assertThat(decision.getObligations()).hasSize(2);
    assertThat(decision.getMandatoryObligations()).hasSize(1);
    assertThat(decision.getMandatoryObligations().get(0).getTarget()).isEqualTo("args.query");
  }

  @Test
  @DisplayName("a challenge is not an allow and carries its approval requirement")
  void challengeIsNotAnAllow() {
    String body =
        "{\"decision\":false,\"context\":{\"profile\":\""
            + AuthZENContract.PROFILE_V1
            + "\",\"state\":\"CHALLENGE\",\"category\":\"approval_required\","
            + "\"reason\":\"approval_required\",\"decision_id\":\"dec-3\","
            + "\"schema_version\":\"2026-08-29\",\"approval\":{\"all_of\":[{\"quorum\":2,"
            + "\"eligible\":[{\"kind\":\"group\",\"type\":\"team\",\"local\":\"risk\"}]}],"
            + "\"separation_of_duties\":true,\"expires_at\":\"2026-09-02T00:00:00Z\"}}}";
    answering(200, body);

    AuthZENDecision decision = client.evaluate(aRequest());
    assertThat(decision.isAllowed()).as("a challenge is not permission").isFalse();
    assertThat(decision.getState()).isEqualTo(AuthZENOperationalState.CHALLENGE);
    assertThat(decision.getApproval()).isPresent();
    assertThat(decision.getApproval().get().getAllOf()).hasSize(1);
  }

  // -------------------------------------------------------------------------
  // Refusals off the wire
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a typed refusal reaches the caller with its code and pointer")
  void typedRefusalReachesTheCaller() {
    answering(
        422,
        "{\"code\":\"unevaluable_attribute\",\"pointer\":\"/evaluation/context/department\","
            + "\"message\":\"this surface cannot evaluate the context member\","
            + "\"supported\":[\"args\",\"correlation\"]}");
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> client.evaluate(aRequest()));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.UNEVALUABLE_ATTRIBUTE);
    assertThat(refused.getPointer()).isEqualTo("/evaluation/context/department");
    assertThat(refused.getSupported()).containsExactly("args", "correlation");
    assertThat(refused.isRetryable()).isFalse();
  }

  @Test
  @DisplayName("a later write must not erase an unresolved parent")
  void aLaterWriteMustNotEraseAnUnresolvedParent() {
    // The fail-open this surface exists to prevent, arriving through its own
    // builder. A gateway whose body decode failed records that `args` is
    // unresolvable; a recovered partial prompt written over it would have
    // produced a complete-looking envelope that passed validation and went on
    // the wire.
    answering(200, allowBody());
    AuthZENEvaluation.SingleBuilder builder =
        AuthZENEvaluation.of(
            new AuthZENSubject("gateway", "g1"),
            new AuthZENAction("llm.completion"),
            new AuthZENResource("llm", "llm"));
    builder.build().getContext().putUnknown("args", "the request body failed to decode");
    AuthZENRequest request = builder.query(Attribute.known("a partial prompt")).build();

    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(request));
    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/context/args");
    assertThat(requestCount()).as("the request was sent with the unknown erased").isZero();
  }

  @Test
  @DisplayName("a later write must not erase an unresolved LEAF")
  void aLaterWriteMustNotEraseAnUnresolvedLeaf() {
    // The half the first version of this guard missed, and the shape a caller
    // would actually write: no manual putUnknown, just the builder twice. The
    // parent guard alone let this through one level down.
    answering(200, allowBody());
    AuthZENRequest request =
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "g1"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.unknown("the request body could not be decoded"))
            .query(Attribute.known("a recovered partial prompt"))
            .build();

    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(request));
    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/context/args/query");
    assertThat(unresolved.getReason()).contains("could not be decoded");
    assertThat(requestCount())
        .as("the request was sent with the unresolved leaf overwritten")
        .isZero();
  }

  @Test
  @DisplayName("a later correlation write must not erase an unresolved leaf")
  void aLaterCorrelationWriteMustNotEraseAnUnresolvedLeaf() {
    answering(200, allowBody());
    AuthZENRequest request =
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "g1"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.known("hello"))
            .correlation("x-session-id", Attribute.unknown("the trace header was unreadable"))
            .correlation("x-session-id", Attribute.known("sess-1"))
            .build();

    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(request));
    assertThat(unresolved.getPointer())
        .isEqualTo("/evaluation/context/correlation/x-session-id");
    assertThat(requestCount()).isZero();
  }

  @Test
  @DisplayName("a 5xx carrying an unknown code stays a retryable transport failure")
  void a5xxWithAnUnknownCodeIsNotAPermanentRefusal() {
    // The regression the leniency fix nearly introduced. An ingress or sidecar
    // answering 503 with its OWN JSON error body decodes cleanly now that the
    // refusal is read leniently - and its code round-trips as an unknown, which
    // is non-retryable. Reading it as a refusal turns a transient outage into a
    // permanent one that `while (isRetryable())` never retries.
    answering(
        503,
        "{\"code\":\"upstream_unavailable\",\"message\":\"backend down\",\"trace_id\":\"t-1\"}");
    AuthZENTransportException e =
        assertThrows(AuthZENTransportException.class, () -> client.evaluate(aRequest()));
    assertThat(e.isRetryable()).isTrue();
  }

  @Test
  @DisplayName("a 5xx carrying a known code is still a typed refusal")
  void a5xxWithAKnownCodeIsStillTyped() {
    answering(
        502,
        "{\"code\":\"evaluation_unavailable\",\"pointer\":\"/evaluation\","
            + "\"message\":\"no answer\"}");
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> client.evaluate(aRequest()));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.EVALUATION_UNAVAILABLE);
    assertThat(refused.isRetryable()).isTrue();
  }

  @Test
  @DisplayName("a 4xx carrying an unknown code is still a typed refusal")
  void a4xxWithAnUnknownCodeIsStillTyped() {
    // "Fix the request" is right whatever the code, and the POINTER is worth
    // more than the code - so a newer server's refusal still reaches the caller
    // as something it can act on.
    answering(
        422,
        "{\"code\":\"unevaluable_realm\",\"pointer\":\"/evaluation/subject/realm\","
            + "\"message\":\"no\"}");
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> client.evaluate(aRequest()));
    assertThat(refused.getCode().isKnown()).isFalse();
    assertThat(refused.getPointer()).isEqualTo("/evaluation/subject/realm");
    assertThat(refused.isRetryable()).isFalse();
  }

  @Test
  @DisplayName("an attribute bag survives a setter round trip, so validate has no null path")
  void anAttributeBagIsNeverNull() {
    // `validate` guarded `if (bag != null)`, so a null bag meant the unknown
    // check did not run at all - and a public setter could produce one. The
    // field's invariant is "never null"; the setter now holds it, and the
    // guard is gone, so there is no null path left for a future unknown to
    // hide behind.
    answering(200, allowBody());
    AuthZENRequest request =
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "g1"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.unknown("idp down"))
            .build();
    AttributeMap bag = request.getContext();

    request.setContext(null);
    assertThat(request.getContext()).as("the setter must not leave the field null").isNotNull();

    // The bag itself still carries the unknown, and putting it back refuses.
    request.setContext(bag);
    AuthZENUnresolvedException unresolved =
        assertThrows(AuthZENUnresolvedException.class, () -> client.evaluate(request));
    assertThat(unresolved.getPointer()).isEqualTo("/evaluation/context/args/query");
    assertThat(requestCount()).isZero();
  }

  @Test
  @DisplayName("an obligation with no mandatory flag is refused, not read as advisory")
  void anObligationMissingItsMandatoryFlagIsRefused() {
    // Jackson leaves a missing primitive at its default, so `mandatory` omitted
    // decoded to `false`, passed validation (a primitive cannot be null) and
    // getMandatoryObligations() returned empty - turning off the one
    // instruction a caller must not ignore, silently. The strict reader cannot
    // help: it catches EXTRA members, never missing ones.
    String body =
        allowBody()
            .replace(
                "\"schema_version\":\"2026-08-29\"",
                "\"schema_version\":\"2026-08-29\",\"obligations\":["
                    + "{\"type\":\"field_redact\",\"target\":\"args.query\","
                    + "\"source_policy\":\"legacy:redact_pii\",\"schema_version\":1}]");
    answering(200, body);
    AuthZENUnusableResponseException e =
        assertThrows(AuthZENUnusableResponseException.class, () -> client.evaluate(aRequest()));
    assertThat(e.getMessage()).contains("mandatory");
  }

  @Test
  @DisplayName("a refusal carrying a member this build does not know is still a refusal")
  void aRefusalWithAnExtraMemberIsStillTyped() {
    // Strictness belongs on the DECISION, not on the diagnostic. Refusing to
    // decode a refusal because the server added a member collapses a typed
    // error carrying a code and a pointer into an opaque transport failure
    // carrying neither - the one thing a refusal exists to avoid.
    answering(
        422,
        "{\"code\":\"unsupported_action\",\"pointer\":\"/evaluation/action/name\","
            + "\"message\":\"not an evaluable action\",\"retry_after\":5}");
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> client.evaluate(aRequest()));
    assertThat(refused.getCode()).isEqualTo(AuthZENErrorCode.UNSUPPORTED_ACTION);
    assertThat(refused.getPointer()).isEqualTo("/evaluation/action/name");
  }

  @Test
  @DisplayName("a refusal survives Java serialization with everything a caller branches on")
  void aRefusalSurvivesSerialization() throws Exception {
    AuthZENError document =
        new AuthZENError(AuthZENErrorCode.EVALUATION_UNAVAILABLE, "nope")
            .setPointer("/evaluation/context/args")
            .setSupported(java.util.Arrays.asList("args", "correlation"));
    AuthZENRefusedException before = new AuthZENRefusedException(document);
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(before);
    }
    AuthZENRefusedException after;
    try (java.io.ObjectInputStream in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      after = (AuthZENRefusedException) in.readObject();
    }
    // Every accessor used to NPE here: the refusal document is transient
    // because the generated wire types are not Serializable.
    assertThat(after.getCode()).isEqualTo(AuthZENErrorCode.EVALUATION_UNAVAILABLE);
    assertThat(after.getPointer()).isEqualTo("/evaluation/context/args");
    // A non-empty list on purpose: asserting isEmpty() on a refusal built
    // WITHOUT one is satisfied whether or not the field round-trips at all.
    assertThat(after.getSupported()).containsExactly("args", "correlation");
    assertThat(after.isRetryable()).isTrue();
  }

  @Test
  @DisplayName("a dependency failure is the one refusal worth retrying")
  void dependencyFailureIsRetryable() {
    answering(
        502,
        "{\"code\":\"evaluation_unavailable\",\"message\":\"the evaluator did not answer\"}");
    AuthZENRefusedException refused =
        assertThrows(AuthZENRefusedException.class, () -> client.evaluate(aRequest()));
    assertThat(refused.isRetryable()).isTrue();
  }

  @Test
  @DisplayName("an authentication failure stays observable and never becomes a denial")
  void authFailureIsNotADenial() {
    // A 401 rendered as `decision: false` would be indistinguishable from a
    // policy denial in every caller branch and every dashboard.
    answering(401, "{\"error\":{\"code\":401,\"message\":\"unauthorized\"}}");
    AuthZENTransportException e =
        assertThrows(AuthZENTransportException.class, () -> client.evaluate(aRequest()));
    assertThat(e.getStatusCode()).isEqualTo(401);
    assertThat(e.isRetryable())
        .as("retrying a credentials problem turns a misconfiguration into a rate-limit incident")
        .isFalse();
  }

  @Test
  @DisplayName("an error body that is not a typed refusal is still never a decision")
  void nonRefusalErrorBodyIsNotADecision() {
    answering(500, "<html>gateway error</html>");
    AuthZENTransportException e =
        assertThrows(AuthZENTransportException.class, () -> client.evaluate(aRequest()));
    assertThat(e.isRetryable()).isTrue();
  }

  // -------------------------------------------------------------------------
  // The refusal enumeration itself
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("exactly one refusal code is retryable")
  void exactlyOneCodeIsRetryable() {
    // Derived from the artifact's own enumeration rather than from a list
    // written beside it, so a code added to the contract fails this test until
    // somebody decides which side of the line it is on.
    List<String> retryable =
        AuthZENErrorCode.KNOWN_WIRE_VALUES.stream()
            .filter(v -> AuthZENRefusals.isRetryable(AuthZENErrorCode.of(v)))
            .collect(java.util.stream.Collectors.toList());
    assertThat(retryable).containsExactly("evaluation_unavailable");
  }

  @Test
  @DisplayName("a refusal code this build does not know round-trips and is not retryable")
  void unknownCodeRoundTrips() throws IOException {
    AuthZENErrorCode code = AuthZENErrorCode.of("quarantined");
    assertThat(code.isKnown()).isFalse();
    assertThat(AuthZENRefusals.isRetryable(code)).isFalse();
    assertThat(code.value()).isEqualTo("quarantined");
    assertThat(MAPPER.writeValueAsString(code)).isEqualTo("\"quarantined\"");
  }

  @Test
  @DisplayName("a refusal reads as an error naming the member")
  void refusalMessageNamesTheMember() {
    AuthZENRefusedException refused =
        AuthZENRefusedException.of(
            AuthZENErrorCode.UNSUPPORTED_SUBJECT, "/evaluation/subject/type", "no");
    assertThat(refused.getMessage()).contains("/evaluation/subject/type");
    assertThat(refused).isInstanceOf(AuthZENEvaluationException.class);
  }

  @Test
  @DisplayName("a response context that names another profile does not validate")
  void constProfileIsValidated() throws IOException {
    // The generated `const` check, which is what catches a payload whose
    // profile member was rewritten in transit rather than negotiated.
    AuthZENResponse decoded =
        MAPPER.readValue(allowBody().replace(AuthZENContract.PROFILE_V1, "x"), AuthZENResponse.class);
    try {
      decoded.validate("");
      fail("a profile the contract does not allow must not validate");
    } catch (AuthZENRefusedException refused) {
      assertThat(refused.getPointer()).isEqualTo("/context/profile");
    }
  }
}
