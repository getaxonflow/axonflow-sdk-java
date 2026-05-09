# Changelog

All notable changes to the AxonFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [8.0.0] - 2026-05-09 — Decision History API + policy_version recorded on every decision + telemetry simplification

**Major release.** The headline feature is the new decision-history client
API: `listDecisions` for paging through recorded decisions, plus a
runnable example showing the full record → list → explain audit flow.
Bundled into a major because the v8 line also tightens the telemetry
contract — see `Removed` at the bottom of this entry for that.

### Added

- **`listDecisions(ListDecisionsOptions opts)` client method.** Pages
 over recorded decision history from the orchestrator, mirroring `GET
 /api/v1/decisions`. Companion to the v7.4.0 `getDecisionExplain`
 method — callers can now both list and drill in. See
 `examples/list-decisions/`.
- **`examples/explain-decision/`** end-to-end runnable example covering
 the full decision audit flow: record → list → explain.

### Migration guide (v7 → v8)

- **`AxonFlowConfig.Builder.telemetry(Boolean)` removed.** Code that
 called `.telemetry(true)` or `.telemetry(false)` on the builder will
 fail to compile. Migration: remove the call from your builder chain.
 If you were using it to disable telemetry, set
 `AXONFLOW_TELEMETRY=off` in the environment instead — that's the
 sole opt-out lever as of v8. If you were using it to force-enable,
 the default is now ON for every mode so the override is no longer
 needed.
- **`AxonFlowConfig.getTelemetry()` removed.** Code reading the
 override field will fail to compile. Same migration: drop the call
 site; `AXONFLOW_TELEMETRY=off` is the only telemetry knob.
- **`TelemetryReporter.isEnabled` and `TelemetryReporter.sendPing`
 signatures simplified.** Both methods previously took the
 `(mode, configOverride, hasCredentials, ...)` parameter shape from
 the v7 mode-and-override gate. v8 collapses to a single env-var
 signal: `isEnabled(String axonflowTelemetry)` and
 `sendPing(String mode, String sdkEndpoint, boolean debug, ...)`.
 Application code does not call these directly; only test harnesses
 that exercise the testability surface need to update.

### Telemetry

- **`AXONFLOW_TELEMETRY=off` is the sole opt-out.** `AxonFlowConfig.Builder.telemetry(Boolean)` + `AxonFlowConfig.getTelemetry()` removed; sandbox-mode clients (constructed via `Mode.SANDBOX`) now fire on the same 7-day heartbeat schedule as production (was suppressed pre-v8), tagged `stream="sandbox"` so dev pings stay distinguishable.
- **Heartbeat payload v1 schema additions** on the wire: new `telemetry_type` and `deployment_mode` fields, new `DeploymentMode` constants class on the SDK side. Existing receivers continue working unchanged — strictly additive. `EndpointType.COMMUNITY_SAAS` is removed (now lives on `deployment_mode` instead).

## [7.1.0] - 2026-05-06 — X-Axonflow-Client header + scope-aware license validation

**Companion release to platform v7.7.0.** The Java SDK now sends an
`X-Axonflow-Client` identification header on every governed request, which
the agent uses to derive the SDK request scope and validate it against any
license token's audience claim per the license matrix.

### Added

- **`X-Axonflow-Client: sdk-java/<version>` header** on every governed
 outbound request. Set automatically by the SDK transport; not
 configurable. Agents at v7.7.0+ derive request scope from this header
 and reject cross-quadrant token misuse (e.g. a SaaS Plugin Pro token
 paired with an SDK request) at the validator boundary. Older agents
 (pre-v7.7.0) ignore the header and continue to work unchanged.

### Compatibility

- **No public API changes.** Existing v7.0.x callers update
 `<version>7.1.0</version>` on the `axonflow-sdk` dependency and
 rebuild against v7.1.0 with no source changes.
- **Backward-compatible against pre-v7.7.0 agents.** The header is
 silently dropped by older agents; the SDK behaves identically against
 v7.0.x / v7.1.x / v7.6.x agents as before.
- **Forward-compatible.** Future agent releases that require the header
 on specific governed surfaces will work with this SDK without further
 client changes.

### Companion releases (same day)

- **Platform v7.7.0** — V1 SaaS Plugin Pro launch, license matrix,
 per-tenant tier resolution, GDPR right-to-erasure
 ([CHANGELOG](https://github.com/getaxonflow/axonflow/blob/main/CHANGELOG.md))
- **Go SDK v7.1.0** / **Python SDK v7.1.0** /
 **TypeScript SDK v7.1.0** — same `X-Axonflow-Client` injection
- **Plugins** — Claude Code / Cursor / Codex v1.2.0; OpenClaw v2.2.0
 with Pro license token paste activating Pro features

axonflow-sdk-rust remains at v0.1.0 (preview); SDK-Rust will gain the
header in a future preview release.

## [7.0.0] - 2026-04-29 — Production, quality, and security hardening — upgrade encouraged

**Upgrade strongly recommended.** Over the past month we've shipped substantial production, quality, and security hardening across the AxonFlow SDKs and platform — upgrade to the latest major for a more secure, reliable, and bug-free experience.

**Security highlights from this release cycle:**
- **Webhook signing-key now exposed by SDK response type** (this release). The `secret` (HMAC-SHA256) field on `WebhookSubscription` — returned by `createWebhook` — was missing from the SDK type, so callers had no way to retrieve the signing key and webhook signature verification was effectively un-implementable. The field is now wired through end-to-end. Documented in [`GHSA-248h-974q-xrc2`](https://github.com/getaxonflow/axonflow-sdk-java/security/advisories/GHSA-248h-974q-xrc2).
- **`DO_NOT_TRACK` opt-out removed in favor of `AXONFLOW_TELEMETRY=off`** (this release). `DO_NOT_TRACK` was unreliable because host CLIs and runtimes commonly inject `DO_NOT_TRACK=1` regardless of user intent; an explicit AxonFlow-scoped opt-out is the only signal we honor now. Maven Surefire and Failsafe environment blocks were tightened so local `mvn test` runs no longer inherit a host `DO_NOT_TRACK=1` and emit accidental pings.
- **Test-harness opt-out hygiene** (last cycle, v6.x). Test environments that mutate `DO_NOT_TRACK` no longer silently leak real pings from CI; transport is mocked at the test boundary.

Major release across the AxonFlow SDK family. Companion releases ship the same day: TypeScript v7.0.0 / Python v7.0.0 / Go v7.0.0 (with `/v7` module path migration) / Java v7.0.0. The full set of platform-side security fixes shipped alongside this release is documented in the consolidated platform advisory [`GHSA-9h64-2846-7x7f`](https://github.com/getaxonflow/axonflow/security/advisories/GHSA-9h64-2846-7x7f).

**Reliability and bug-fix highlights:**
- **`retry_context` + `idempotency_key` for cross-step de-duplication** (last cycle, v6.x). Workflow steps that retry across pod restarts no longer record duplicate audit entries; idempotency_key flows end-to-end through MAP HITL approve/reject responses.
- **`mapTimeout` config field — SDK parity with Go / Python / TypeScript** (last cycle, v6.x). MAP plan generation has its own timeout knob distinct from the per-request timeout, so multi-LLM-call decompositions no longer cancel the wrong path under load.
- **`LLMProvider` source compatibility restored** (last cycle, v6.x). The 7-arg primitive constructor and primitive `getPriority()` / `getWeight()` accessors are back; null-safe boxed accessors split off as `getPriorityBoxed()` / `getWeightBoxed()` for callers needing "explicitly 0 vs not set" disambiguation.

### BREAKING

- **`DO_NOT_TRACK` is no longer honored as an AxonFlow telemetry opt-out.** Use `AXONFLOW_TELEMETRY=off` instead. Host tools and CLIs commonly inject `DO_NOT_TRACK=1` regardless of user intent, which makes it unreliable as a signal.
- **(Test API)** Package-private `TelemetryReporter.isEnabled` and `TelemetryReporter.sendPing` overloads no longer accept a `String doNotTrack` parameter. The remaining `String axonflowTelemetry` parameter is the sole opt-out signal accepted by the testability surface.

### Security

- **TLS verification bypass closed (CWE-295).** `HttpClientFactory` previously honored `insecureSkipVerify(true)` on `AxonFlowConfig` as a single-flag opt-in to a permissive `X509TrustManager` that accepted ANY server certificate, including attacker-presented certificates in MITM scenarios. The insecure path is now double-gated: it activates only if both `insecureSkipVerify(true)` is set on the builder AND the `AXONFLOW_INSECURE_TLS` environment variable is set to `true` (or `1`). When the builder flag is set without the env var, the SDK logs a warning and keeps the JVM's default `TrustManager` in place. A loud `*** SECURITY WARNING ***` is logged whenever the insecure path actually activates. Default behavior — and behavior in production environments without the env var — uses standard JDK + system trust-store validation. Resolves code-scanning alert #8.

### Fixed

- The `DO_NOT_TRACK=1 is deprecated.` `logger.warn` is no longer emitted on every telemetry decision when `DO_NOT_TRACK=1` is set.

### Changed

- **Telemetry now follows the 7-day delivered-heartbeat contract** instead of firing on every `new AxonFlow()` construction. The SDK emits at most one anonymous heartbeat per environment every 7 days during SDK activity. A stamp file at the OS-native user cache dir tracks last successful delivery; mtime is the source of truth across process restarts. Failed POSTs do NOT advance the stamp — a transient network error does not silence telemetry for 7 days. An in-memory 1-hour cache caps `Files.getLastModifiedTime` calls on hot request paths; a `ReentrantLock`-guarded in-flight flag coalesces concurrent threads so only one ping fires under load. `AXONFLOW_TELEMETRY=off` is re-evaluated on every gate run. Restricted environments where no cache dir is available (e.g. AWS Lambda with no `HOME`/`LOCALAPPDATA`) fall back transparently to the previous "one ping per construction" behavior.

### CI / development

- CI workflows (`ci.yml`, `integration.yml`, `release.yml`, `wire-shape-contract.yml`, `validate-version-alignment.yml`) now use `AXONFLOW_TELEMETRY=off` to suppress telemetry during automated runs.

## [6.2.0] - 2026-04-28 — listLLMProviders() + LLMProvider source-compat

Minor release. New LLM-provider listing API closes the parity gap with the Python + Go SDKs; the rest of the cycle restores `LLMProvider` source-compatibility for callers using the 7-arg primitive shape. Coordinated cycle: TypeScript v6.2.0 / Python v6.9.0 / Go v6.0.0 (major: see SDKCompatibility breaking type change in that release) ship same day.

### Added

- **`axonflow.listLLMProviders()`** + `listLLMProviders(String type, Boolean enabled)` — list configured LLM providers and their per-provider health snapshot. Calls `GET /api/v1/llm-providers`. New `LLMProvider` and `LLMProviderHealth` types in `com.getaxonflow.sdk.types`. Async variant `listLLMProvidersAsync()`. Closes the parity gap with the Python SDK's `list_providers()` and the Go SDK's `ListProviders()`.
- **`examples/basic/`** — minimal smoke example exercising `healthCheck()`, `proxyLLMCall()`, and `listConnectors()` against a running AxonFlow agent. Uses try-with-resources so OkHttp's dispatcher + connection pool are cleaned up at exit. Run via `mvn -q compile exec:java` after `mvn install -DskipTests` at the SDK root.

### Fixed

- **`LLMProvider` source compatibility restored.** The 7-arg primitive constructor `LLMProvider(name, type, enabled:bool, priority:int, weight:int, hasApiKey:bool, health)` is back (delegates to the new 13-arg boxed form, marked `@Deprecated` so new callers move to the boxed shape). `getPriority()` / `getWeight()` return primitive `int` again (null-safe-unbox to 0). Boxed accessors are available as `getPriorityBoxed()` / `getWeightBoxed()` / `getEnabledBoxed()` / `getHasApiKeyBoxed()` for callers that need to distinguish "explicitly 0" from "field not present".

## [6.1.0] - 2026-04-25 — Plugin Batch 1 explainability fields on MCP responses

Minor release. Surfaces fields the AxonFlow agent has emitted since v7.1.0 (Plugin Batch 1) but the SDK didn't declare. Pure field-additions on existing methods — additive only, no breaking changes. The pre-existing constructors are preserved as source-compat overloads. Documented in OpenAPI via platform v7.4.3.

Coordinated cycle: TypeScript v6.1.0 / Python v6.8.0 / Go v5.8.0 ship same day with the same field set.

### Added

- **`MCPCheckInputResponse`** gains 5 optional Plugin Batch 1 fields:
 - `decisionId: String` — audit correlator
 - `riskLevel: String` — `low` | `medium` | `high` | `critical`
 - `policyMatches: List<ExplainPolicy>` — per-policy explainability records
 - `overrideAvailable: Boolean` — whether session override is permitted for the matched policies (boxed so callers can distinguish "unset" from `false` on older platforms)
 - `overrideExistingId: String` — already-active override consumed by this decision (if any)
- **`MCPCheckOutputResponse`** gains 3 optional fields:
 - `decisionId: String`
 - `policyMatches: List<ExplainPolicy>`
 - `redactedMessage: String` — text-redaction counterpart to `redactedData` (used when the connector returned a string message rather than tabular rows; e.g. execute-style responses)

`ExplainPolicy` already shipped — same Jackson-annotated record now reused on the MCP response types. Pre-v7.1.0 platforms leave all new fields as `null`; callers should treat `null` as "context not available" rather than an error.

### Source compatibility

Both `MCPCheckInputResponse` and `MCPCheckOutputResponse` retain their v6.0.0 constructor signatures as overloads that delegate to the new `@JsonCreator` constructors with `null` for the new fields. Existing callers that build response instances locally compile unchanged. `equals()` / `hashCode()` / `toString()` updated to include the new fields.

### Deferred

`client.explainDecision(decisionId)` and the full `ExplainRule` / `DecisionExplanation` type surface are tracked separately as feature work. This release ships only field-surfacing on existing methods.

## [6.0.0] - 2026-04-25 — Major: WebhookSubscription identity-based equality

This is a major release. The bump is driven by a single observable-contract change: `WebhookSubscription.equals()` and `.hashCode()` now compare on `id` only, not every field. Coordinated with the TypeScript SDK v6.0.0 release (PolicyInfo rename) as a v6 alignment cycle for the SDKs that needed breaking changes; Python (v6.7.0) and Go (v5.7.0) ship as minor on the same day because their changes are purely additive.

### BREAKING — `WebhookSubscription` equality is now identity-based on `id`

`WebhookSubscription` is an entity, not a value object. Two instances with the same `id` represent the same logical webhook regardless of whether one view has loaded `secret` (only returned by `createWebhook`) and another has not, or whether `updatedAt` / `active` have moved between fetches.

Previously `equals()` / `hashCode()` compared every field. That meant a webhook constructed locally with the legacy 6-arg constructor compared **unequal** to the same logical webhook deserialized from a server response that included `secret` / `tenantId` / `orgId`. `Set<WebhookSubscription>`, `Map` keying, and identity-tracking caches all broke under those semantics.

Identity-based equality is the canonical entity semantics; the prior value-based equality was a bug. Because `equals()` / `hashCode()` are part of the observable Java contract that callers depend on for set deduplication, map lookup, and identity caches, the fix is a breaking change per strict semver — even though the new behaviour corrects incorrect semantics rather than introducing them.

If you need content-equality (e.g. to detect a rotated `secret`), compare the relevant getters directly. The 6-arg constructor is preserved as a source-compat overload for callers building local instances; only `equals()` / `hashCode()` semantics changed. `toString()` is unchanged (still emits full state with `secret` redacted).

### Added

- **Version alignment check** (`.github/workflows/validate-version-alignment.yml`). CI now fails any PR or push to `main` where `pom.xml`'s `<version>` drifts from the first released `## [X.Y.Z]` section in `CHANGELOG.md`. Matches the pattern in the platform repo and the Go SDK.
- **Wire-shape contract gate** (`.github/workflows/wire-shape-contract.yml`). CI fails any PR that introduces drift between Java `@JsonProperty` annotations and the OpenAPI specs pinned at `tests/fixtures/wire-shape-baseline.json::openapi_specs_sha`. Four gates: cross-spec schema divergence, intra-file schema duplicates, per-type SDK-vs-spec drift, and registered-type rename-escape. The pinned spec SHA is itself guarded by a `spec-pin-bump` PR label so a single PR can't both move the SHA and silence drift. Source-discovery walks brace depth so nested classes (e.g. `WorkflowTypes.CreateWorkflowRequest`) and inner enums are attributed to the correct type rather than the file's outer class. Mirrors the Python, Go, and TypeScript gates.
- **`WebhookSubscription.secret`** — HMAC-SHA256 signing key now exposed on the response from `createWebhook`. Required to verify the `X-AxonFlow-Signature` header on inbound webhook deliveries; without it, callers can't validate payload authenticity. Also adds `tenantId` and `orgId` (ownership scoping). The 6-arg constructor is preserved as a source-compat overload that delegates to the 9-arg with nulls for the new fields. `toString()` redacts `secret` to avoid log leakage.
- **`BudgetAlert.acknowledged`** — alert dismissal flag. Also adds `@JsonProperty` annotations on previously-unannotated fields (`id`, `threshold`, `message`) so the wire-shape gate can see them; Jackson's default name mapping was correct, but the validator's discovery walks `@JsonProperty` only.

### Fixed

- Telemetry pings now deliver reliably from short-lived JVMs (CLI, serverless, cold-starts). `AxonFlow` construction blocks briefly while the ping is sent synchronously (bounded by the telemetry timeout).
- Telemetry path is bounded at `TIMEOUT_SECONDS` (3s) total; the `/health` probe and checkpoint POST share a single monotonic deadline instead of stacking independent timeouts.

## [5.7.0] - 2026-04-22

### Added

- **Rich `ApproveStepResponse` / `RejectStepResponse`** — both classes now carry
 the same shape as the step-gate response: `decision` resolves to `"allow"` or
 `"block"`, `retryContext` mirrors the gate response retry state, `approvedBy`
 / `approvedAt` / `rejectedBy` / `rejectedAt` carry reviewer identity,
 `approvalId` is the deterministic HITL queue UUID, `policiesMatched`
 reconstructs the governance trail. The legacy `workflowId` / `stepId` /
 `status` fields remain for back-compat.
- **`planId` on approve/reject responses** — populated when the response comes
 from the MAP plan-scoped endpoint; empty on WCP plane responses. Same types
 work across both endpoints.
- **Back-compat 3-arg constructors** — `new ApproveStepResponse(workflowId, stepId, status)`
 and `new RejectStepResponse(workflowId, stepId, status)` still compile, so
 existing test fixtures and SDK consumers keep working without changes.
- **`getPendingPlanApprovals` / `getPendingPlanApprovalsAsync`** — new client
 methods that list MAP-plane pending approvals
 (`GET /api/v1/plans/approvals/pending`), the counterpart of
 `getPendingApprovals` for the WCP plane. The two-arg form accepts an
 optional `planId` filter so reviewer tools can scope the listing to one
 plan. Available on Evaluation+ licenses (same tier gate as the MAP step
 approve/reject endpoints).
- **`PendingApproval.planId`** — populated on MAP-plane entries, null on
 WCP-plane entries. Mirrors the approve/reject asymmetry. `PendingApproval`
 also gains `stepIndex`, `decision`, `decisionReason`, and `approvalStatus`
 so reviewer tools can render the full approval context without a second
 request. The existing 6-arg back-compat constructor still works; new
 fields default to null / 0 for callers that don't pass them.

### Fixed

- **`approveStep` / `rejectStep` / `getPendingApprovals` endpoint URLs** —
 all three previously targeted non-existent paths under
 `/api/v1/workflow-control/` and would fail against a real AxonFlow server.
 Corrected to the canonical `/api/v1/workflows/{id}/steps/{step_id}/(approve|reject)`
 and `/api/v1/workflows/approvals/pending` routes. Customers using these
 methods against a live deployment were receiving 404s; this release makes
 them work.
- **`PendingApprovalsResponse` getters and JSON field names aligned with the
 wire shape** — the class previously declared `getApprovals()` / `getTotal()`
 over a JSON body with keys `approvals` / `total`, which never matched the
 server (`pending_approvals` / `count`). Getters renamed to
 `getPendingApprovals()` / `getCount()` with the correct JSON bindings.
 Callers that read `response.getApprovals()` or `response.getTotal()` need
 to update to the new getter names.

### Deprecated

- `DO_NOT_TRACK=1` as an AxonFlow telemetry opt-out — scheduled for removal after 2026-05-05 in the next major release. Use `AXONFLOW_TELEMETRY=off` instead. The SDK emits a one-line migration warning when `DO_NOT_TRACK=1` is the active control and `AXONFLOW_TELEMETRY=off` is not also set.

### Unchanged

- `approveStep(workflowId, stepId)` / `rejectStep(workflowId, stepId, reason)`
 method signatures on `AxonFlow` are unchanged — only the response fields grew.

## [5.6.0] - 2026-04-21

### Added

- **`retry_context` and `idempotency_key` support on the step gate** —
 `StepGateResponse` now carries a `RetryContext` object on every gate call with the
 true `(workflow_id, step_id)` lifecycle: `gateCount`, `completionCount`,
 `priorCompletionStatus` (`PriorCompletionStatus` enum —
 `NONE` / `COMPLETED` / `GATED_NOT_COMPLETED`), `priorOutputAvailable`,
 `priorOutput`, `priorCompletionAt`, `firstAttemptAt`, `lastAttemptAt`,
 `lastDecision`, and `idempotencyKey`. Prefer these to the legacy `cached` /
 `decisionSource` fields.
- **`stepGate(workflowId, stepId, request, options)` overload** — new 4-arg overload
 taking `StepGateOptions`. Use `StepGateOptions.includePriorOutput()` to send
 `?include_prior_output=true` so `retryContext.priorOutput` is populated when a prior
 `/complete` has landed. Existing 3-arg overload keeps its signature and delegates
 with `StepGateOptions.defaults()`.
- **`StepGateRequest.idempotencyKey`** — caller-supplied opaque business-level key
 (max 255 chars; validated at construction). Immutable once recorded on the first gate
 call for a `(workflow, step)`; subsequent gate/complete calls must pass the same key.
- **`MarkStepCompletedRequest.idempotencyKey`** — must match the key set on the
 corresponding gate call, if any. Mismatch (including missing-vs-set on either side)
 surfaces as a typed `IdempotencyKeyMismatchException`.
- **`IdempotencyKeyMismatchException`** — new typed exception in
 `com.getaxonflow.sdk.exceptions`. Thrown by `stepGate` and `markStepCompleted` when
 the platform returns HTTP 409 with `error.code == "IDEMPOTENCY_KEY_MISMATCH"`.
 Surfaces `workflowId`, `stepId`, `expectedIdempotencyKey`, `receivedIdempotencyKey`,
 plus inherited `statusCode=409` and `errorCode="IDEMPOTENCY_KEY_MISMATCH"`.
- **`RetryContext`, `PriorCompletionStatus`, `StepGateOptions`** — exported in
 `WorkflowTypes`.

### Fixed

- **409 dispatch on step gate/complete** — previously all 409 responses on
 `markStepCompleted` fell through to a generic `AxonFlowException(..., 409,
 "VERSION_CONFLICT")`, conflating step idempotency conflicts with plan version
 conflicts. The step gate/complete call sites now inspect the 409 body and dispatch
 to `IdempotencyKeyMismatchException` when `error.code` matches, falling back to a
 generic `AxonFlowException` otherwise. Plan update paths (which also use 409) are
 untouched.

### Deprecated

- **`StepGateResponse.isCached()`** and **`StepGateResponse.getDecisionSource()`** —
 marked `@Deprecated`. Use `getRetryContext().getGateCount() > 1` and
 `getRetryContext().getPriorCompletionStatus()` instead. Planned for removal in a
 future major version.

### Compatibility

Companion to the platform change that introduces `retry_context` on
`POST /api/v1/workflows/{workflow_id}/steps/{step_id}/gate`. Additive only — existing
callers that never set `idempotencyKey` or pass `StepGateOptions` see no behavior
change. Binary-compatibility preserved: old `StepGateRequest`, `StepGateResponse`, and
`MarkStepCompletedRequest` constructors kept alongside new ones.

## [5.5.0] - 2026-04-20

### Added

- **`mapTimeout` field on `AxonFlowConfig`** — brings Java to parity with
 the TypeScript, Python, and Go SDKs (all three already had a separate
 MAP timeout). The shared `timeout` (default 60s) only covered single-
 request endpoints; MAP plans routinely take 60-120s because they
 chain multiple LLM calls end-to-end, and the global timeout was
 cutting them off. New constant `DEFAULT_MAP_TIMEOUT = 120s`, new
 builder method `AxonFlowConfig.builder().mapTimeout(Duration.ofSeconds(300))`,
 and new environment variable `AXONFLOW_MAP_TIMEOUT_SECONDS`. All
 plan-lifecycle methods (`generatePlan`, `executePlan`, `getPlanStatus`,
 `updatePlan`, `cancelPlan`, `resumePlan`, `rollbackPlan`,
 `getPlanVersions`) now use a plan-specific `OkHttpClient` clone with
 `callTimeout` / `readTimeout` / `writeTimeout` set to `mapTimeout`.
 Shares connection pool + interceptors + dispatcher with the main
 client; only the timeout attributes differ. Keep `mapTimeout` ≤
 server `AXONFLOW_MAP_MAX_TIMEOUT_SECONDS` (default 300s) ≤
 front-door ALB `idle_timeout.timeout_seconds` (default 300s), or the
 connection is killed mid-stream.

## [5.4.0] - 2026-04-18

### Added

- **Execution boundary semantics** — `retryPolicy` field on `StepGateRequest`
 (via builder: `.retryPolicy("reevaluate")`). Controls cached vs fresh
 evaluation for the same step boundary.
- **Step gate response metadata** — `cached` (boolean) and `decisionSource`
 (String) fields on `StepGateResponse` via `isCached()` and
 `getDecisionSource()`.
- **Workflow checkpoints** — `getCheckpoints(workflowId)` lists step-gate
 checkpoints. `resumeFromLastCheckpoint(workflowId)` resumes from last
 checkpoint (Evaluation+). `resumeFromCheckpoint(workflowId, checkpointId)`
 resumes from a specific checkpoint (Enterprise).
- **Checkpoint types** — `Checkpoint`, `CheckpointListResponse`, and
 `ResumeFromCheckpointResponse` with Jackson deserialization.
- **`AxonFlow.explainDecision(decisionId)`** (+ `explainDecisionAsync`) — fetches
 the full explanation for a previously-made policy decision via
 `GET /api/v1/decisions/:id/explain`. Returns a `DecisionExplanation` with
 matched policies, risk level, reason, override availability, existing
 override ID (if any), and a rolling-24h session hit count for the matched
 rule. Shape is frozen (future extra fields ignored via Jackson's
 `@JsonIgnoreProperties(ignoreUnknown = true)`); additive-only fields ensure
 forward compatibility.
- **`DecisionExplanation`, `ExplainPolicy`, `ExplainRule`** — new immutable
 DTOs in `com.getaxonflow.sdk.types`.
- **`AuditSearchRequest.Builder.decisionId`, `policyName`, `overrideId`** —
 three new optional filter fields on `searchAuditLogs`. Use `decisionId`
 to gather every record tied to one decision; `policyName` to find
 everything matched by a specific policy; `overrideId` to reconstruct an
 override's full lifecycle.

### Compatibility

Companion to platform v7.1.0. Works against plugin releases (OpenClaw v1.3.0+,
Claude Code v0.5.0+, Cursor v0.5.0+, Codex v0.4.0+) that surface the
`DecisionExplanation` shape. Audit filter fields pass through when unset;
server-side filtering activates on v7.1.0+ platforms.

## [5.3.0] - 2026-04-09

### Added

- `AXONFLOW_TRY=1` environment variable to connect to `try.getaxonflow.com` shared evaluation server
- `AxonFlowTry.register()` helper for self-registering a tenant
- Checkpoint telemetry reports `endpoint_type: "community-saas"` when try mode is active

---

## [5.2.0] - 2026-04-08

### Added

- **Telemetry `endpoint_type` field.** The anonymous telemetry ping now includes an SDK-derived classification of the configured AxonFlow endpoint as one of `localhost`, `private_network`, `remote`, or `unknown`. The raw URL is never sent and is not hashed. This helps distinguish self-hosted evaluation from real production deployments on the checkpoint dashboard. Opt out as before via `DO_NOT_TRACK=1` or `AXONFLOW_TELEMETRY=off`.
- **`TelemetryReporter.classifyEndpoint(url)` method and `TelemetryReporter.EndpointType` constants** exported publicly for applications that want to inspect the classification.

### Changed

- Examples and documentation updated to reflect the new AxonFlow platform v6.2.0 defaults for `PII_ACTION` (now `warn` — was `redact`) and the new `AXONFLOW_PROFILE` env var. No SDK API changes.

---

## [5.1.0] - 2026-04-06

### Added

- **`GovernedTool` adapter** — framework-agnostic tool governance wrapper. Wraps any `Tool` interface with input/output policy enforcement (`mcpCheckInput` before execution, `mcpCheckOutput` after). Factory: `GovernedTool.wrap(tool, client)`, builder pattern, batch helper: `GovernedTool.governTools(tools, client)`.
- **`checkToolInput()` / `checkToolOutput()`** — generic aliases for tool governance. Existing `mcpCheckInput()` / `mcpCheckOutput()` remain supported. Async variants included.

### Changed

- Anonymous telemetry is now enabled by default for all endpoints, including localhost/self-hosted evaluation. Opt out with `DO_NOT_TRACK=1` or `AXONFLOW_TELEMETRY=off`.

---

## [5.0.0] - 2026-04-05

### BREAKING CHANGES

- **`X-Tenant-ID` header removed.** The SDK no longer sends `X-Tenant-ID`. The server derives tenant from OAuth2 Client Credentials (Basic auth). Requires platform v6.0.0+.
- **`getMaterialityClassification()` method renamed.** MAS FEAT `getMateriality()` renamed to `getMaterialityClassification()` to match server JSON field `materiality_classification`.

### Added

- **`Status` field on `PlanResponse`.** The server returns plan status (pending, executing, completed, failed, cancelled) which was previously not parsed by the SDK.

### Fixed

- **MCP examples missing `client_id` and `user_token`** in request body for enterprise MCP handler authentication.

---

## [4.3.0] - 2026-03-24

### Added

- `simulatePolicies()` / `simulatePoliciesAsync()` — dry-run all active policies against an input query. Returns allowed/blocked status, applied policies, risk score, and daily usage. Requires Evaluation tier or above.
- `getPolicyImpactReport()` / `getPolicyImpactReportAsync()` — test a single policy against multiple inputs and get aggregate match/block statistics.
- `detectPolicyConflicts()` / `detectPolicyConflictsAsync()` — analyze active policies for contradictions, shadows, and redundancies. Optionally filter to conflicts involving a specific policy.
- Types in `com.getaxonflow.sdk.simulation` package: `SimulatePoliciesRequest`, `SimulatePoliciesResponse`, `SimulationDailyUsage`, `ImpactReportInput`, `ImpactReportRequest`, `ImpactReportResult`, `ImpactReportResponse`, `PolicyConflictRef`, `PolicyConflict`, `PolicyConflictResponse`

### Security

- Hardened insecure TLS trust manager (`HttpClientFactory`) to suppress CodeQL `java/insecure-trustmanager` alert. The trust-all `X509TrustManager` is only activated when the user explicitly opts in via `insecureSkipVerify=true` in `AxonFlowConfig`. Added `lgtm` suppression comments, clarified intent in code comments, and enhanced the warning log message to explicitly discourage production use.

---

## [4.2.0] - 2026-03-17

### Added

- `LangGraphAdapter` class — wraps LangGraph workflows with AxonFlow governance gates and per-tool policy enforcement. Includes:
 - `checkGate()` / `stepCompleted()` — step-level governance at LangGraph node boundaries
 - `checkToolGate()` / `toolCompleted()` — per-tool governance within tool_call nodes (each tool gets its own gate check)
 - `mcpToolInterceptor()` — factory returning an interceptor enforcing `mcpCheckInput → handler → mcpCheckOutput` around every MCP tool call
 - `waitForApproval()` — poll until a step is approved or rejected
 - `startWorkflow()` / `completeWorkflow()` / `abortWorkflow()` / `failWorkflow()` — workflow lifecycle management
 - Builder pattern construction, implements `AutoCloseable`
- `WorkflowBlockedError` and `WorkflowApprovalRequiredError` exception classes
- Builder-based option classes: `CheckGateOptions`, `StepCompletedOptions`, `CheckToolGateOptions`, `ToolCompletedOptions`
- MCP interceptor types: `MCPInterceptorOptions`, `MCPToolRequest`, `MCPToolHandler`, `MCPToolInterceptor`
- `getCircuitBreakerStatus()` / `getCircuitBreakerStatusAsync()` — query active circuit breaker circuits and emergency stop state
- `getCircuitBreakerHistory(limit)` / `getCircuitBreakerHistoryAsync(limit)` — retrieve circuit breaker trip/reset audit trail
- `getCircuitBreakerConfig(tenantId)` / `getCircuitBreakerConfigAsync(tenantId)` — get effective circuit breaker config (global or tenant-specific)
- `updateCircuitBreakerConfig(config)` / `updateCircuitBreakerConfigAsync(config)` — update per-tenant circuit breaker thresholds

---

## [4.1.0] - 2026-03-14

### Added

- `auditToolCall()` — record non-LLM tool calls (API, MCP, function) in the audit trail. Returns audit ID, status, and timestamp. Requires Platform v5.1.0+
- `getAuditLogsByTenant()` — retrieve audit logs for a tenant with optional pagination
- `searchAuditLogs()` — search audit logs with filters (client ID, request type, limit)

### Fixed

- Telemetry pings now suppressed for localhost/127.0.0.1/[::1] endpoints unless `telemetryEnabled` is explicitly set to `true`. Prevents telemetry noise during local development.

---

## [4.0.0] - 2026-03-09

### Breaking Changes

- **Removed `totalSteps` from `CreateWorkflowRequest`**. Requires Platform v4.5.0+ (recommended v5.0.0+).
 Total steps are auto-computed when the workflow reaches a terminal state.
- **`mcpCheckInput()` default `operation` changed from `"query"` to `"execute"`**. Callers relying on
 the implicit `"query"` default must now pass `operation("query")` explicitly. The check-input endpoint is
 used by external orchestrators managing their own MCP execution, so `"execute"` (conservative) is the
 correct default over `"query"` (read-only).

### Changed

- README install snippets updated from `3.2.0` to `4.0.0` for Maven and Gradle

### Note

`MediaAnalysisResult.getExtractedText()` was replaced by `hasExtractedText()` + `getExtractedTextLength()`
in v3.5.0. This major version formally acknowledges that breaking change.

---

## [3.8.0] - 2026-03-03

### Added

- `healthCheck()` now returns `capabilities` list and `sdkCompatibility` in `HealthStatus`
- `hasCapability(name)` method on `HealthStatus` to check if platform supports a specific feature
- `SDK_VERSION` constant on `AxonFlowConfig` for programmatic SDK version access
- User-Agent header corrected from `axonflow-java-sdk/1.0.0` to `axonflow-sdk-java/{version}`
- Version mismatch warning logged when SDK version is below platform's `min_sdk_version`
- `PlatformCapability` and `SDKCompatibility` types
- `traceId` field on `CreateWorkflowRequest`, `CreateWorkflowResponse`, `WorkflowStatusResponse`, and `ListWorkflowsOptions` for distributed tracing correlation
- `ToolContext` type for per-tool governance within workflow steps
- `toolContext` field on `StepGateRequest` for tool-level policy enforcement
- `listWorkflows()` now supports `traceId` filter parameter
- Anonymous runtime telemetry for version adoption tracking and feature usage signals
- `TelemetryEnabled` / `telemetry` configuration option to explicitly control telemetry
- `AXONFLOW_TELEMETRY=off` and `DO_NOT_TRACK=1` environment variable opt-out support

### Fixed

- Default User-Agent was hardcoded to `1.0.0` regardless of actual SDK version

---

## [3.7.0] - 2026-02-28

### Added

- **MCP Policy-Check Endpoints** (Platform v4.6.0+): Standalone policy validation for external orchestrators (LangGraph, CrewAI) to enforce AxonFlow policies without executing connector queries
 - `mcpCheckInput(connectorType, statement)`: Validate SQL/commands against input policies (SQLi detection, dangerous query blocking, PII in queries, dynamic policies). Returns `MCPCheckInputResponse` with `isAllowed()` or `getBlockReason()`
 - `mcpCheckOutput(connectorType, responseData)`: Validate MCP response data against output policies (PII redaction, exfiltration limits, dynamic policies). Returns original or redacted data with `PolicyInfo`
 - New types: `MCPCheckInputRequest`, `MCPCheckInputResponse`, `MCPCheckOutputRequest`, `MCPCheckOutputResponse`
 - Sync + async variants with overloads for additional options (`parameters`, `operation`)
 - Supports both query-style (`responseData`) and execute-style (`message` + `metadata`) output validation

---

## [3.6.0] - 2026-02-22

### Added

- Media governance configuration methods: `getMediaGovernanceConfig()`, `updateMediaGovernanceConfig()`, `getMediaGovernanceStatus()`
- Media governance types: `MediaGovernanceConfig`, `MediaGovernanceStatus`
- Media policy category constants: `CATEGORY_MEDIA_SAFETY`, `CATEGORY_MEDIA_BIOMETRIC`, `CATEGORY_MEDIA_PII`, `CATEGORY_MEDIA_DOCUMENT`
- `PolicyCategory` enum values: `MEDIA_SAFETY`, `MEDIA_BIOMETRIC`, `MEDIA_PII`, `MEDIA_DOCUMENT`
- `MarkStepCompletedRequest` now accepts post-execution metrics (`tokens_in`, `tokens_out`, `cost_usd`)

---

## [3.5.0] - 2026-02-18

### Added

- **Media Governance Types**: `MediaContent`, `MediaAnalysisResult`, `MediaAnalysisResponse` for multimodal image governance
- **Media support in `proxyLLMCall()`**: Pass images (base64 or URL) via `ClientRequest.Builder.media()` for governance analysis before LLM routing

### Changed

- **Response cache skipped for media requests**: Requests containing media bypass the response cache (binary content makes cache keys unreliable)

### Breaking

- `MediaAnalysisResult.getExtractedText()` replaced by `isHasExtractedText()` (boolean) and `getExtractedTextLength()` (int). Raw extracted text is no longer exposed in API responses.

---

## [3.4.0] - 2026-02-13

### Added

- **failWorkflow()**: Fail a workflow with optional reason
 - `failWorkflow(workflowId, reason)` + async variant + overload without reason
 - Sends `POST /api/v1/workflows/{id}/fail`
- **HITL Queue API** (Enterprise): Human-in-the-loop approval queue management
 - `listHITLQueue(opts)`: list pending approvals with filtering
 - `getHITLRequest(requestId)`: get approval details
 - `approveHITLRequest(requestId, review)`: approve a request
 - `rejectHITLRequest(requestId, review)`: reject a request
 - `getHITLStats()`: dashboard statistics
 - New types: `HITLApprovalRequest`, `HITLQueueListOptions`, `HITLQueueListResponse`, `HITLReviewInput`, `HITLStats`

## [3.3.1] - 2026-02-12

### Fixed

- **`listUnifiedExecutions` deserialization**: Fixed Jackson deserialization failure on `UnifiedListExecutionsResponse`. Added `@JsonCreator` and `@JsonProperty` annotations to constructor. Without this, `listUnifiedExecutions()` threw "no Creators, like default constructor, exist" error.
- **SSE streaming endpoint path**: `streamExecutionStatus()` now uses correct `/api/v1/unified/executions/{id}/stream` path (was incorrectly pointing to `/api/v1/executions/{id}/stream` which is the Execution Replay API)

---

## [3.3.0] - 2026-02-10

### Added

- **WCP Approval Gates**: HITL approval and rejection for workflow steps
 - `approveStep(workflowId, stepId)` - Approve a pending workflow step
 - `rejectStep(workflowId, stepId, reason)` - Reject a step with reason (backward-compatible overload without reason preserved)
 - `rejectStepAsync(workflowId, stepId, reason)` - Async variant with reason
 - `getPendingApprovals(limit)` - List steps awaiting human approval

- **MAP Plan Cancellation**: Cancel running multi-agent plans
 - `cancelPlan(planId, reason)` - Cancel a plan with optional reason

- **MAP Plan Update**: Modify plan configuration before or during execution
 - `updatePlan(planId, request)` - Update execution mode, domain, or version

- **MAP Plan Versioning and Rollback**: Version history and rollback support
 - `getPlanVersions(planId)` - List plan version history
 - `rollbackPlan(planId, version)` - Rollback to a previous version (throws on 409 conflict)
 - New types: `RollbackPlanResponse`, `PlanVersion`

- **Webhook Subscriptions**: Event notification management
 - `createWebhook(request)` - Create a webhook subscription
 - `listWebhooks()` - List active webhook subscriptions
 - `getWebhook(webhookId)` - Get webhook details
 - `updateWebhook(webhookId, request)` - Update webhook configuration
 - `deleteWebhook(webhookId)` - Delete a webhook subscription
 - New type: `WebhookSubscription`

- **Unified Execution Cancellation**: Cancel running executions across both MAP and WCP subsystems
 - `cancelExecution(executionId, reason)` - Cancel a unified execution via `POST /api/v1/unified/executions/{id}/cancel`
 - Overloaded `cancelExecution(executionId)` variant without reason parameter
 - Propagates to MAP `cancelPlan()` or WCP `abortWorkflow()` based on execution type

### Fixed

- **`executePlan` status hardcoded**: `executePlan()` always returned `status: "completed"` regardless of actual server response. Now reads status from response (`data.status` > `metadata.status` > default), correctly surfacing `awaiting_approval` for WCP confirm mode.
- **Unified execution API URLs**: `getExecutionStatus()` and `listUnifiedExecutions()` now use correct `/api/v1/unified/executions` path (was incorrectly pointing to `/api/v1/executions` which is the Execution Replay API)
- **`rejectStep` reason parameter**: Added `reason` parameter to `rejectStep()` and `rejectStepAsync()` with backward-compatible 2-arg overloads

---

## [3.2.0] - 2026-02-05

### Added

- **Dynamic policy tier support**: `tier` (`PolicyTier`) and `organizationId` fields on `CreateDynamicPolicyRequest`, `UpdateDynamicPolicyRequest`, and `DynamicPolicy` response. Defaults to `PolicyTier.TENANT` when not specified. Builder: `.tier(PolicyTier.ORGANIZATION).organizationId("org-123")`.
- **`ListDynamicPoliciesOptions` filters**: Filter dynamic policies by `tier` and `organizationId`, matching `ListStaticPoliciesOptions` parity.

---

## [3.1.0] - 2026-02-04

### Changed

- Simplified internal endpoint handling by removing legacy helper names `getPortalUrl()` and `getOrchestratorUrl()`.
- Internal request URL construction is now standardized on `config.getEndpoint()`.
- No public API change.

## [3.0.0] - 2026-02-03

### Breaking Changes

- **Removed `executeQuery()`**: Use `proxyLLMCall()` instead (deprecated since v2.7.0). Removed both sync and async (`executeQueryAsync`) variants.

### Added

- **`isRedacted()` verification**: Verified `MCPExecuteResponse.isRedacted()` works correctly for execute responses with PII redaction

### Changed

- Updated all internal references, Javadoc examples, and tests from `executeQuery` to `proxyLLMCall`

---

## [2.7.1] - 2026-01-25

### Changed

- **Gateway Mode smart defaults**: `getPolicyApprovedContext()` and `auditLLMCall()` now use `"community"` as default clientId when not configured, enabling zero-config usage for community/self-hosted deployments

### Fixed

- **PolicyCategory enum**: Added `PII_SINGAPORE("pii-singapore")` value for Singapore PII detection policies (NRIC, FIN, UEN patterns)
- **proxyLLMCall clientId auto-injection**: Auto-populate `clientId` from config when not explicitly set in `ClientRequest`, matching Go/Python/TypeScript SDK behavior

---

## [2.7.0] - 2026-01-25

### Added

- **Unified Execution Tracking**: Consistent status tracking for MAP plans and WCP workflows
 - `getExecutionStatus(executionId)` - Get unified execution status by ID
 - `listUnifiedExecutions(request)` - List executions with type/status filters
 - `ExecutionTypes.ExecutionStatus` class with unified fields for both MAP and WCP executions
 - `ExecutionTypes.ExecutionType` enum: `MAP_PLAN`, `WCP_WORKFLOW`
 - `ExecutionTypes.ExecutionStatusValue` enum: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `ABORTED`, `EXPIRED`
 - `ExecutionTypes.StepStatusValue` enum: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`, `BLOCKED`, `APPROVAL`
 - `ExecutionTypes.UnifiedStepType` enum: `LLM_CALL`, `TOOL_CALL`, `CONNECTOR_CALL`, `HUMAN_TASK`, `SYNTHESIS`, `ACTION`, `GATE`
 - `ExecutionTypes.UnifiedStepStatus` class with step-level details (duration, cost, policy decisions)
 - Helper methods: `isTerminal()`, `isStepTerminal()`, `isStepBlocking()`, `calculateTotalCost()`, `getCurrentStep()`
 - Consistent response format across MAP Multi-Agent Planning and WCP Workflow Control Plane

- **MAS FEAT Compliance Module** (Enterprise): Singapore financial services AI governance
 - AI System Registry: `masfeat().registerSystem()`, `masfeat().getSystem()`, `masfeat().updateSystem()`, `masfeat().listSystems()`, `masfeat().activateSystem()`, `masfeat().retireSystem()`, `masfeat().getRegistrySummary()`
 - 3-Dimensional Risk Rating: Customer Impact × Model Complexity × Human Reliance
 - Materiality Classification: High (sum≥12), Medium (sum≥8), Low (sum<8)
 - FEAT Assessments: `masfeat().createAssessment()`, `masfeat().getAssessment()`, `masfeat().updateAssessment()`, `masfeat().listAssessments()`, `masfeat().submitAssessment()`, `masfeat().approveAssessment()`, `masfeat().rejectAssessment()`
 - Assessment Lifecycle: pending → in_progress → completed → approved/rejected
 - Kill Switch: `masfeat().getKillSwitch()`, `masfeat().configureKillSwitch()`, `masfeat().checkKillSwitch()`, `masfeat().triggerKillSwitch()`, `masfeat().restoreKillSwitch()`, `masfeat().enableKillSwitch()`, `masfeat().disableKillSwitch()`, `masfeat().getKillSwitchHistory()`
 - Automatic model shutdown based on accuracy, bias, and error rate thresholds
 - New types: `AISystemRegistry`, `AISystemUseCase`, `MaterialityClassification`, `SystemStatus`, `FEATAssessment`, `FEATAssessmentStatus`, `FEATPillar`, `KillSwitch`, `KillSwitchStatus`, `KillSwitchEvent`, `KillSwitchEventType`, `RegistrySummary`

- **proxyLLMCall()**: New primary method for Proxy Mode with improved documentation
 - Clearly describes Proxy Mode behavior (AxonFlow makes the LLM call on your behalf)
 - Documents when to use Proxy Mode vs Gateway Mode
 - Both sync (`proxyLLMCall`) and async (`proxyLLMCallAsync`) variants

- **BudgetInfo**: `QueryResponse.getBudgetInfo()` for budget enforcement (HTTP 402)

### Deprecated

- **executeQuery()**: Deprecated in favor of proxyLLMCall()
 - Marked with `@Deprecated` annotation
 - Will be removed in v3.0.0
 - Logs deprecation warning in debug mode
 - Remains functional as a wrapper around proxyLLMCall()

---

## [2.6.0] - 2026-01-18

### Added

- **Workflow Policy Enforcement**: Policy transparency for workflow operations
 - `StepGateResponse` now includes `getPoliciesEvaluated()` and `getPoliciesMatched()` methods with `PolicyMatch` type
 - `PolicyMatch` class with `getPolicyId()`, `getPolicyName()`, `getAction()`, `getReason()` for policy transparency
 - `PolicyEvaluationResult` class for MAP execution with `isAllowed()`, `getAppliedPolicies()`, `getRiskScore()`
 - Workflow operations (`workflow_created`, `workflow_step_gate`, `workflow_completed`) logged to audit trail

---

## [2.5.0] - 2026-01-17

### Added

- **Workflow Control Plane**: Governance gates for external orchestrators
 - "LangChain runs the workflow. AxonFlow decides when it's allowed to move forward."
 - `createWorkflow()` - Register workflows from LangChain/LangGraph/CrewAI/external
 - `stepGate()` - Check if step is allowed to proceed (allow/block/require_approval)
 - `markStepCompleted()` - Mark a step as completed with optional output data
 - `getWorkflow()` - Get workflow status and step history
 - `listWorkflows()` - List workflows with filters (status, source, pagination)
 - `completeWorkflow()` - Mark workflow as completed
 - `abortWorkflow()` - Abort workflow with reason
 - `resumeWorkflow()` - Resume after approval
 - New types: `WorkflowStatus`, `WorkflowSource`, `GateDecision`, `StepType`, `ApprovalStatus`, `MarkStepCompletedRequest`
 - Helper methods on `StepGateResponse`: `isAllowed()`, `isBlocked()`, `requiresApproval()`
 - Helper methods on `WorkflowStatus` and `WorkflowStatusResponse`: `isTerminal()`

---

## [2.4.0] - 2026-01-14

### Added

- **MCP Exfiltration Detection**: `ConnectorPolicyInfo` now includes `getExfiltrationCheck()` with row/volume limit information
 - `ExfiltrationCheckInfo` type with `getRowsReturned()`, `getRowLimit()`, `getBytesReturned()`, `getByteLimit()`, `isWithinLimits()` methods
 - Prevents large-scale data extraction via MCP queries
 - Configurable via `MCP_MAX_ROWS_PER_QUERY` and `MCP_MAX_BYTES_PER_QUERY` environment variables

- **MCP Dynamic Policies**: `ConnectorPolicyInfo` now includes `getDynamicPolicyInfo()` for Orchestrator-evaluated policies
 - `DynamicPolicyInfo` type with `getPoliciesEvaluated()`, `getMatchedPolicies()`, `isOrchestratorReachable()`, `getProcessingTimeMs()`
 - `DynamicPolicyMatch` type with `getPolicyId()`, `getPolicyName()`, `getPolicyType()`, `getAction()`, `getReason()`
 - Supports rate limiting, budget controls, time-based access, and role-based access policies
 - Optional feature - enable via `MCP_DYNAMIC_POLICIES_ENABLED=true`

---

## [2.3.0] - 2026-01-09

### Added

- **MCP Policy Enforcement Response Fields**: `mcpQuery()` and `mcpExecute()` now return policy enforcement metadata
 - `isRedacted()` - Whether any fields were redacted by PII policies
 - `getRedactedFields()` - JSON paths of redacted fields (e.g., `rows[0].ssn`)
 - `getPolicyInfo()` - Full policy evaluation metadata

- **PolicyInfo types**: New types for policy enforcement metadata
 - `ConnectorPolicyInfo` - Contains `getPoliciesEvaluated()`, `isBlocked()`, `getBlockReason()`, `getRedactionsApplied()`, `getProcessingTimeMs()`, `getMatchedPolicies()`
 - `PolicyMatchInfo` - Details of matched policies including `getPolicyId()`, `getPolicyName()`, `getCategory()`, `getSeverity()`, `getAction()`

---

## [2.2.0] - 2026-01-08

### Added

- **OAuth2-style client credentials**: New `clientId()` and `clientSecret()` builder methods following OAuth2 client credentials pattern.
 - `clientId` is used for request identification (required for most API calls)
 - `clientSecret` is optional - community/self-hosted deployments work without it

- **Enterprise: Close PR** (`closePR`): Close a PR without merging and optionally delete the branch
 - Useful for cleaning up test/demo PRs created by code governance examples
 - Supports all Git providers: GitHub, GitLab, Bitbucket
 - Requires enterprise portal authentication

### Changed

- **Simplified authentication**: For community mode, simply provide `clientId` for request identification. No `clientSecret` needed.

```java
// Community mode - no secret needed
AxonFlowClient client = AxonFlowClient.builder()
.endpoint("http://localhost:8080")
.clientId("my-app") // Used for request identification
.build();
```

### Fixed

- **getPlanStatus endpoint**: Fixed endpoint path from `/api/v1/orchestrator/plan/{id}` to `/api/v1/plan/{id}` to match agent proxy routes

### Enterprise

- OAuth2 Basic auth: `Authorization: Basic base64(clientId:clientSecret)` replaces `X-License-Key` header
- Removed `licenseKey()` builder method (use `clientId()`/`clientSecret()`)

## [2.1.2] - 2026-01-07

### Fixed

- **Gateway Mode clientId not sent in request body**: Fixed `getPolicyApprovedContext()` to auto-populate `client_id` in request body from config when not explicitly provided
 - Server requires `client_id` in JSON body for `/api/policy/pre-check` endpoint
 - Previously only sent as header (X-Client-ID), causing "client_id field is required" errors
 - Now matches Go SDK behavior which auto-populates from `config.ClientID`
 - Affects all Gateway Mode pre-check calls

- **executePlan() using non-existent endpoint**: Fixed `executePlan()` to use correct Agent API endpoint
 - Changed from `/api/v1/orchestrator/plan/{planId}/execute` (404) to `/api/request` with `request_type: "execute-plan"`
 - Now matches Go SDK pattern for plan execution
 - Fixes MAP (Multi-Agent Planning) two-step execution flow

## [2.1.1] - 2026-01-06

### Fixed

- **Null Policies List Handling**: Fixed `NullPointerException` in list-returning policy methods when API returns null instead of empty array
 - Affected methods: `listDynamicPolicies()`, `getEffectiveDynamicPolicies()`, `listStaticPolicies()`, `getEffectiveStaticPolicies()`
 - Added explicit null check for wrapper and list fields before returning
 - Returns empty list when wrapper or list field is null

## [2.1.0] - 2026-01-05

### Added

- **Sensitive Data Category**: Added `SENSITIVE_DATA` to `PolicyCategory` enum for policies that return `sensitive-data` category
- **Provider Restrictions for Compliance**: Support for `allowed_providers` in dynamic policy action config
 - Specify allowed providers via `DynamicPolicyAction` with `config.put("allowed_providers", List.of(...))`
 - Enables GDPR, HIPAA, and RBI compliance by restricting LLM routing to specific providers
 - Example: `new DynamicPolicyAction("route", Map.of("allowed_providers", List.of("ollama", "azure-eu")))`
- **Category field**: Added `category` field to `CreateDynamicPolicyRequest` and `UpdateDynamicPolicyRequest`
- **Dynamic Policy Response Wrappers**: Added `DynamicPoliciesResponse` and `DynamicPolicyResponse` wrapper types

### Fixed

- **toggleDynamicPolicy HTTP Method**: Changed from PATCH to PUT to match API specification
- **Dynamic Policy Response Parsing**: Fixed all dynamic policy methods to correctly parse wrapped API responses
 - Agent proxy returns `{"policies": [.]}` and `{"policy": {.}}` wrappers
 - Updated `listDynamicPolicies`, `getDynamicPolicy`, `createDynamicPolicy`, `updateDynamicPolicy`, `toggleDynamicPolicy`, `getEffectiveDynamicPolicies`
- **X-Tenant-ID Header for Orchestrator Requests**: Fixed missing X-Tenant-ID header in orchestrator API calls
 - Added `addTenantIdHeader()` call to `buildOrchestratorRequest()` method
 - Ensures tenant identification works in community/self-hosted mode without full credentials

## [2.0.0] - 2026-01-05

### Breaking Changes

- **BREAKING**: Renamed `agentUrl` to `endpoint` in `AxonFlowConfig.Builder`
- **BREAKING**: Removed `orchestratorUrl` and `portalUrl` config options (Agent now proxies all routes)
- **BREAKING**: Dynamic policy API path changed from `/api/v1/policies/dynamic` to `/api/v1/dynamic-policies`

### Added

- **Audit Log Reading**: Added `searchAuditLogs()` for searching audit logs with filters (user email, client ID, time range, request type)
- **Tenant Audit Logs**: Added `getAuditLogsByTenant()` for retrieving audit logs scoped to a specific tenant
- **Audit Types**: Added `AuditLogEntry`, `AuditSearchRequest`, `AuditQueryOptions`, and `AuditSearchResponse` types
- **PII Redaction Support**: Added `isRequiresRedaction()` method to `PolicyApprovalResult`
 - When `true`, PII was detected with redact action and response should be processed for redaction
 - Supports new detection defaults: PII defaults to redact instead of block

### Changed

- All SDK methods now route through single Agent endpoint
- Simplified configuration - only `endpoint()` builder method needed
- Removed `getOrchestratorUrl()` and `getPortalUrl()` config methods (now return endpoint directly)
- Added `@Deprecated` annotation on `agentUrl()` builder method for backwards compatibility

### Migration Guide

**Before (v1.x):**
```java
AxonFlowConfig config = AxonFlowConfig.builder()
.agentUrl("http://localhost:8080")
.orchestratorUrl("http://localhost:8081")
.portalUrl("http://localhost:8082")
.clientId("my-client")
.clientSecret("my-secret")
.build();
```

**After (v2.x):**
```java
AxonFlowConfig config = AxonFlowConfig.builder()
.endpoint("http://localhost:8080")
.clientId("my-client")
.clientSecret("my-secret")
.build();
```

---

## [1.12.0] - 2026-01-04

### Added

- **Portal Authentication**: Added `loginToPortal()` and `logoutFromPortal()` for session-based authentication
- **Portal URL Configuration**: New `portalUrl` config option for Code Governance portal endpoints
- **CSV Export**: Added `exportCodeGovernanceDataCsv()` for CSV format exports

### Fixed

- **Code Governance Authentication**: Changed Code Governance methods to use portal session-based auth instead of API key auth

---

## [1.11.0] - 2026-01-04

### Added

- **Get Connector**: `getConnector(id)` to retrieve details for a specific connector
- **Connector Health Check**: `getConnectorHealth(id)` to check health status of an installed connector
- **ConnectorHealthStatus type**: New type for connector health responses
- **Orchestrator Health Check**: `orchestratorHealthCheck()` to verify Orchestrator service health
- **Uninstall Connector**: `uninstallConnector()` to remove installed MCP connectors

### Fixed

- **Connector API Endpoints**: Fixed endpoints to use Orchestrator (port 8081) instead of Agent
 - `listConnectors()` - Changed from Agent `/api/connectors` to Orchestrator `/api/v1/connectors`
 - `installConnector()` - Fixed path to `/api/v1/connectors/{id}/install`
- **Dynamic Policies Endpoint**: Changed from Agent `/api/v1/policies` to Orchestrator `/api/v1/policies/dynamic`

---

## [1.10.0] - 2026-01-04

### Added

- **Execution Replay API**: Debug governed workflows with step-by-step state capture
 - `listExecutions()` - List executions with filtering (status, time range)
 - `getExecution()` - Get execution with all step snapshots
 - `getExecutionSteps()` - Get individual step snapshots
 - `getExecutionTimeline()` - Timeline view for visualization
 - `exportExecution()` - Export for compliance/archival
 - `deleteExecution()` - Delete execution records

- **Cost Controls**: Budget management and LLM usage tracking
 - `createBudget()` / `getBudget()` / `listBudgets()` - Budget CRUD
 - `updateBudget()` / `deleteBudget()` - Budget management
 - `getBudgetStatus()` - Check current budget usage
 - `checkBudget()` - Pre-request budget validation
 - `recordUsage()` - Record LLM token usage
 - `getUsageSummary()` - Usage analytics and reporting

---

## [1.9.0] - 2025-12-31

### Fixed

- **Gateway Mode Community Fix**: Removed client-side credential validation from Gateway Mode methods
 - `getPolicyApprovedContext()` and `auditLLMCall()` now work without credentials in community/self-hosted deployments
 - Server decides auth requirements based on `DEPLOYMENT_MODE`
 - Matches TypeScript SDK v1.11.1 behavior

---

## [1.8.0] - 2025-12-30

### Changed

- **Community Mode**: Credentials are now optional for self-hosted/community deployments
 - SDK can be initialized without `licenseKey` or `clientId/clientSecret` for community features
 - `executeQuery()` and `healthCheck()` work without credentials
 - Auth headers are only sent when credentials are configured

### Added

- `hasCredentials()` method in `AxonFlowConfig` to check if credentials are configured
- `requireCredentials()` helper for enterprise feature validation

### Fixed

- Fixed `PolicyOverride` JSON field mappings (`action_override`, `override_reason`)
- Fixed `listPolicyOverrides()` endpoint path and response parsing
- Fixed `getStaticPolicyVersions()` response parsing

---

## [1.7.0] - 2025-12-29

_Note: v1.7.0 on Maven Central does not include community mode. Use v1.8.0 instead._

---

## [1.6.0] - 2025-12-29

### Added

- **Enterprise Policy Features**:
 - `organizationId()` builder method in `CreateStaticPolicyRequest` for organization-tier policies
 - `organizationId()` builder method in `ListStaticPoliciesOptions` for filtering by organization
 - `listPolicyOverrides()` method to list all active policy overrides

- **Convenience Methods**:
 - `listStaticPolicies(PolicyTier tier, String organizationId)` - filter by tier and organization
 - `listStaticPolicies(PolicyTier tier, PolicyCategory category)` - filter by tier and category
 - `listStaticPolicies(PolicyCategory category)` - filter by category
 - `getEffectiveStaticPolicies(PolicyCategory category)` - filter effective policies by category

---

## [1.5.0] - 2025-12-29

### Added

- **Code Governance Metrics & Export APIs** (Enterprise): Compliance reporting for AI-generated code
 - `getCodeGovernanceMetrics()` - Returns aggregated statistics (PR counts, file totals, security findings)
 - `exportCodeGovernanceData()` - Exports PR records as JSON for auditors
 - `exportCodeGovernanceDataCSV()` - Exports PR records as CSV

- **New Types**: `CodeGovernanceMetrics`, `ExportOptions`, `ExportResponse`

---

## [1.4.0] - 2025-12-29

### Added

- **Code Governance Git Provider APIs** (Enterprise): Create PRs from LLM-generated code
 - `validateGitProvider()` - Validate credentials before saving
 - `configureGitProvider()` - Configure GitHub, GitLab, or Bitbucket
 - `listGitProviders()` - List configured providers
 - `deleteGitProvider()` - Remove a provider
 - `createPR()` - Create PR from generated code with audit trail
 - `listPRs()` - List PRs with filtering
 - `getPR()` - Get PR details
 - `syncPRStatus()` - Sync status from Git provider

- **New Types**: `GitProviderType`, `FileAction`, `CodeFile`, `CreatePRRequest`, `CreatePRResponse`, `PRRecord`, `ListPRsOptions`, `ListPRsResponse`

- **Supported Git Providers**:
 - GitHub (Cloud and Enterprise Server)
 - GitLab (Cloud and Self-Managed)
 - Bitbucket (Cloud and Server/Data Center)

---

## [1.3.1] - 2025-12-29

### Fixed

- **MCP Connector Queries**: Fixed endpoint mismatch causing 404 errors
 - Changed `queryConnector()` to use `/api/request` with `request_type: "mcp-query"` (matches Go and TypeScript SDKs)
 - Previously used non-existent `/api/v1/connectors/query` endpoint
 - MCP connector examples now work correctly with configured connectors

---

## [1.3.0] - 2025-12-28

### Added

- **HITL Support**: `PolicyAction.REQUIRE_APPROVAL` for human oversight policies
 - Use with `createStaticPolicy()` to trigger approval workflows
 - Enterprise: Full HITL queue integration
 - Community: Auto-approves immediately

- **Code Governance**: `CodeArtifact` type for LLM-generated code detection
 - Language and code type identification
 - Potential secrets and unsafe pattern detection

---

## [1.2.0] - 2025-12-25

### Added

- **Policy CRUD Methods**: Full policy management support for Unified Policy Architecture v2.0.0
 - `listStaticPolicies()` - List policies with filtering
 - `getStaticPolicy()` - Get single policy by ID
 - `createStaticPolicy()` - Create custom policy
 - `updateStaticPolicy()` - Update existing policy
 - `deleteStaticPolicy()` - Delete policy
 - `toggleStaticPolicy()` - Enable/disable policy
 - `getEffectiveStaticPolicies()` - Get merged hierarchy
 - `testPattern()` - Test regex pattern

- **Policy Override Methods** (Enterprise)
- **Dynamic Policy Methods**
- **New Types**: `StaticPolicy`, `DynamicPolicy`, `PolicyOverride`

## [1.1.2] - 2025-12-23

### Fixed

- **Java 11 Compatibility** - Fixed compilation error on Java 11
 - Replaced `Stream.toList()` (Java 16+) with `Collectors.toList()` (Java 8+)
 - MAP plan parsing now works correctly on all supported Java versions (11, 17, 21)

## [1.1.1] - 2025-12-23

### Fixed

- **MAP Endpoint** - Fixed `generatePlan()` to use correct Agent API endpoint
 - Changed from `/api/v1/orchestrator/plan` to `/api/request` with `request_type: "multi-agent-plan"`
 - Added proper response parsing for Agent API format
 - Fixed null-safety issues with request context

## [1.1.0] - 2025-12-19

### Added

- **LLM Interceptors** - Transparent governance for LLM API calls
 - `OpenAIInterceptor` for OpenAI API interception
 - `AnthropicInterceptor` for Anthropic API interception
 - `GeminiInterceptor` for Google Generative AI interception
 - Policy enforcement and audit logging for all providers
- Full feature parity with other SDKs for LLM interceptors
- **Self-Hosted Zero-Config Tests** - Auth header verification for localhost
 - Tests verify auth headers are skipped for localhost endpoints

## [1.0.0] - 2025-12-04

### Added

- Initial release of AxonFlow Java SDK
- Core client with `executeQuery()` for governed AI calls
- Policy enforcement with `PolicyViolationException`
- **Gateway Mode** support
 - `getPolicyApprovedContext()` for pre-checks
 - `auditLLMCall()` for compliance logging
- **Multi-Agent Planning**
 - `generatePlan()` for creating execution plans
 - `executePlan()` for running plans
 - `getPlanStatus()` for checking plan status
- **MCP Connectors**
 - `listConnectors()` for available connectors
 - `installConnector()` for connector installation
 - `queryConnector()` for connector queries
- Comprehensive type definitions with Jackson
- Retry logic with exponential backoff (OkHttp)
- Response caching with Caffeine
- Self-hosted mode for localhost deployments
- Java 11+ compatibility
- Maven Central publishing support
