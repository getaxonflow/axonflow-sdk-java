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
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.IdempotencyKeyMismatchException;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.MarkStepCompletedRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.PriorCompletionStatus;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.RetryContext;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateOptions;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateRequest;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepGateResponse;
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.StepType;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for WCP retry_context + idempotency_key (#1673 Phase 1 + 2). */
@WireMockTest
@DisplayName("retry_context + idempotency_key (#1673)")
class RetryContextIdempotencyTest {

  private static final String GATE_PATH = "/api/v1/workflows/wf_1/steps/step_1/gate";
  private static final String COMPLETE_PATH = "/api/v1/workflows/wf_1/steps/step_1/complete";

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
  }

  private static String retryContextJson(
      int gateCount,
      int completionCount,
      String priorStatus,
      boolean priorOutputAvailable,
      String priorOutput,
      String priorCompletionAt,
      String firstAttemptAt,
      String lastAttemptAt,
      String lastDecision,
      String idempotencyKey) {
    return "{"
        + "\"gate_count\":" + gateCount + ","
        + "\"completion_count\":" + completionCount + ","
        + "\"prior_completion_status\":\"" + priorStatus + "\","
        + "\"prior_output_available\":" + priorOutputAvailable + ","
        + "\"prior_output\":" + priorOutput + ","
        + "\"prior_completion_at\":"
        + (priorCompletionAt == null ? "null" : "\"" + priorCompletionAt + "\"") + ","
        + "\"first_attempt_at\":\"" + firstAttemptAt + "\","
        + "\"last_attempt_at\":\"" + lastAttemptAt + "\","
        + "\"last_decision\":\"" + lastDecision + "\","
        + "\"idempotency_key\":\"" + idempotencyKey + "\""
        + "}";
  }

  @Test
  @DisplayName("first-call: gate_count=1, prior_completion_status=none, timestamps equal")
  void firstCall() {
    String now = "2026-04-21T15:30:45.123Z";
    String body =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"cached\":false,"
            + "\"decision_source\":\"fresh\","
            + "\"retry_context\":"
            + retryContextJson(1, 0, "none", false, "null", null, now, now, "allow", "")
            + "}";
    stubFor(
        post(urlEqualTo(GATE_PATH))
            .willReturn(aResponse().withStatus(200).withBody(body).withHeader("Content-Type", "application/json")));

    StepGateResponse gate =
        axonflow.stepGate("wf_1", "step_1", StepGateRequest.builder().stepType(StepType.LLM_CALL).build());

    RetryContext rc = gate.getRetryContext();
    assertThat(rc).isNotNull();
    assertThat(rc.getGateCount()).isEqualTo(1);
    assertThat(rc.getCompletionCount()).isZero();
    assertThat(rc.getPriorCompletionStatus()).isEqualTo(PriorCompletionStatus.NONE);
    assertThat(rc.isPriorOutputAvailable()).isFalse();
    assertThat(rc.getPriorOutput()).isNull();
    assertThat(rc.getPriorCompletionAt()).isNull();
    assertThat(rc.getFirstAttemptAt()).isEqualTo(rc.getLastAttemptAt());
    assertThat(rc.getLastDecision().getValue()).isEqualTo(gate.getDecision().getValue());
    assertThat(rc.getIdempotencyKey()).isEmpty();
    verify(postRequestedFor(urlEqualTo(GATE_PATH)));
  }

  @Test
  @DisplayName("second-call after completion: gate_count=2, prior_completion_status=completed")
  void secondCallAfterCompletion() {
    String body =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"retry_context\":"
            + retryContextJson(
                2,
                1,
                "completed",
                true,
                "null",
                "2026-04-21T15:30:30.000Z",
                "2026-04-21T15:30:00.000Z",
                "2026-04-21T15:31:00.000Z",
                "allow",
                "")
            + "}";
    stubFor(post(urlEqualTo(GATE_PATH)).willReturn(aResponse().withStatus(200).withBody(body)));

    RetryContext rc =
        axonflow
            .stepGate("wf_1", "step_1", StepGateRequest.builder().stepType(StepType.LLM_CALL).build())
            .getRetryContext();
    assertThat(rc.getGateCount()).isEqualTo(2);
    assertThat(rc.getCompletionCount()).isEqualTo(1);
    assertThat(rc.getPriorCompletionStatus()).isEqualTo(PriorCompletionStatus.COMPLETED);
    assertThat(rc.isPriorOutputAvailable()).isTrue();
    assertThat(rc.getPriorCompletionAt()).isNotNull();
    assertThat(rc.getFirstAttemptAt()).isNotEqualTo(rc.getLastAttemptAt());
  }

  @Test
  @DisplayName("second-call without completion: gate_count=2, prior_completion_status=gated_not_completed")
  void secondCallWithoutCompletion() {
    String body =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"retry_context\":"
            + retryContextJson(
                2,
                0,
                "gated_not_completed",
                false,
                "null",
                null,
                "2026-04-21T15:30:00.000Z",
                "2026-04-21T15:31:00.000Z",
                "allow",
                "")
            + "}";
    stubFor(post(urlEqualTo(GATE_PATH)).willReturn(aResponse().withStatus(200).withBody(body)));

    RetryContext rc =
        axonflow
            .stepGate("wf_1", "step_1", StepGateRequest.builder().stepType(StepType.LLM_CALL).build())
            .getRetryContext();
    assertThat(rc.getGateCount()).isEqualTo(2);
    assertThat(rc.getCompletionCount()).isZero();
    assertThat(rc.getPriorCompletionStatus()).isEqualTo(PriorCompletionStatus.GATED_NOT_COMPLETED);
    assertThat(rc.isPriorOutputAvailable()).isFalse();
    assertThat(rc.getPriorCompletionAt()).isNull();
  }

  @Test
  @DisplayName("includePriorOutput() sends ?include_prior_output=true and carries prior_output")
  void includePriorOutputQueryParam() {
    String priorOutput = "{\"result\":\"ok\",\"score\":0.92}";
    String body =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"retry_context\":"
            + retryContextJson(
                2,
                1,
                "completed",
                true,
                priorOutput,
                "2026-04-21T15:30:30.000Z",
                "2026-04-21T15:30:00.000Z",
                "2026-04-21T15:31:00.000Z",
                "allow",
                "")
            + "}";
    stubFor(
        post(urlEqualTo(GATE_PATH + "?include_prior_output=true"))
            .willReturn(aResponse().withStatus(200).withBody(body)));

    StepGateResponse gate =
        axonflow.stepGate(
            "wf_1",
            "step_1",
            StepGateRequest.builder().stepType(StepType.LLM_CALL).build(),
            StepGateOptions.includePriorOutput());

    verify(postRequestedFor(urlEqualTo(GATE_PATH + "?include_prior_output=true")));
    Map<String, Object> po = gate.getRetryContext().getPriorOutput();
    assertThat(po).isNotNull();
    assertThat(po).containsEntry("result", "ok");
  }

  @Test
  @DisplayName("idempotency_key round-trip: gate sets it, retry_context echoes, complete carries")
  void idempotencyKeyRoundTrip() {
    String key = "payment:wire:acct4471:invoice-7721";
    String gateBody =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"retry_context\":"
            + retryContextJson(
                1,
                0,
                "none",
                false,
                "null",
                null,
                "2026-04-21T15:30:00.000Z",
                "2026-04-21T15:30:00.000Z",
                "allow",
                key)
            + "}";
    stubFor(post(urlEqualTo(GATE_PATH)).willReturn(aResponse().withStatus(200).withBody(gateBody)));
    stubFor(post(urlEqualTo(COMPLETE_PATH)).willReturn(aResponse().withStatus(204)));

    StepGateResponse gate =
        axonflow.stepGate(
            "wf_1",
            "step_1",
            StepGateRequest.builder().stepType(StepType.LLM_CALL).idempotencyKey(key).build());
    assertThat(gate.getRetryContext().getIdempotencyKey()).isEqualTo(key);

    axonflow.markStepCompleted(
        "wf_1",
        "step_1",
        MarkStepCompletedRequest.builder().output(Map.of("ok", true)).idempotencyKey(key).build());

    verify(postRequestedFor(urlEqualTo(GATE_PATH)).withRequestBody(containing("\"idempotency_key\":\"" + key + "\"")));
    verify(postRequestedFor(urlEqualTo(COMPLETE_PATH)).withRequestBody(containing("\"idempotency_key\":\"" + key + "\"")));
  }

  @Test
  @DisplayName("markStepCompleted 409 IDEMPOTENCY_KEY_MISMATCH surfaces typed exception")
  void markStepCompletedIdempotencyMismatch() {
    String errorBody =
        "{\"error\":{\"code\":\"IDEMPOTENCY_KEY_MISMATCH\","
            + "\"message\":\"idempotency_key on complete does not match the key recorded on gate\","
            + "\"details\":{\"workflow_id\":\"wf_1\",\"step_id\":\"step_1\","
            + "\"expected_idempotency_key\":\"a\",\"received_idempotency_key\":\"b\"}}}";
    stubFor(
        post(urlEqualTo(COMPLETE_PATH))
            .willReturn(aResponse().withStatus(409).withBody(errorBody).withHeader("Content-Type", "application/json")));

    assertThatThrownBy(
            () ->
                axonflow.markStepCompleted(
                    "wf_1",
                    "step_1",
                    MarkStepCompletedRequest.builder().idempotencyKey("b").build()))
        .isInstanceOf(IdempotencyKeyMismatchException.class)
        .satisfies(
            e -> {
              IdempotencyKeyMismatchException idem = (IdempotencyKeyMismatchException) e;
              assertThat(idem.getWorkflowId()).isEqualTo("wf_1");
              assertThat(idem.getStepId()).isEqualTo("step_1");
              assertThat(idem.getExpectedIdempotencyKey()).isEqualTo("a");
              assertThat(idem.getReceivedIdempotencyKey()).isEqualTo("b");
              assertThat(idem.getStatusCode()).isEqualTo(409);
              assertThat(idem.getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
            });
  }

  @Test
  @DisplayName("RetryContext preserves null idempotency_key (contract §3: \"string or null\")")
  void retryContextAcceptsNullIdempotencyKey() {
    String body =
        "{"
            + "\"decision\":\"allow\","
            + "\"step_id\":\"step_1\","
            + "\"retry_context\":{"
            + "\"gate_count\":1,"
            + "\"completion_count\":0,"
            + "\"prior_completion_status\":\"none\","
            + "\"prior_output_available\":false,"
            + "\"prior_output\":null,"
            + "\"prior_completion_at\":null,"
            + "\"first_attempt_at\":\"2026-04-21T15:30:00.000Z\","
            + "\"last_attempt_at\":\"2026-04-21T15:30:00.000Z\","
            + "\"last_decision\":\"allow\","
            + "\"idempotency_key\":null"
            + "}}";
    stubFor(post(urlEqualTo(GATE_PATH)).willReturn(aResponse().withStatus(200).withBody(body)));

    RetryContext rc =
        axonflow
            .stepGate("wf_1", "step_1", StepGateRequest.builder().stepType(StepType.LLM_CALL).build())
            .getRetryContext();
    assertThat(rc.getIdempotencyKey()).isNull();
  }

  @Test
  @DisplayName("stepGate 409 IDEMPOTENCY_KEY_MISMATCH surfaces typed exception")
  void stepGateIdempotencyMismatch() {
    String errorBody =
        "{\"error\":{\"code\":\"IDEMPOTENCY_KEY_MISMATCH\",\"message\":\"mismatch\","
            + "\"details\":{\"workflow_id\":\"wf_1\",\"step_id\":\"step_1\","
            + "\"expected_idempotency_key\":\"a\",\"received_idempotency_key\":\"b\"}}}";
    stubFor(post(urlEqualTo(GATE_PATH)).willReturn(aResponse().withStatus(409).withBody(errorBody)));

    assertThatThrownBy(
            () ->
                axonflow.stepGate(
                    "wf_1",
                    "step_1",
                    StepGateRequest.builder().stepType(StepType.LLM_CALL).idempotencyKey("b").build()))
        .isInstanceOf(IdempotencyKeyMismatchException.class)
        .satisfies(
            e -> {
              IdempotencyKeyMismatchException idem = (IdempotencyKeyMismatchException) e;
              assertThat(idem.getExpectedIdempotencyKey()).isEqualTo("a");
              assertThat(idem.getReceivedIdempotencyKey()).isEqualTo("b");
            });
  }
}
