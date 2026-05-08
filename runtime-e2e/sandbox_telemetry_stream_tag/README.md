# Runtime proof — Sandbox-mode telemetry fires with stream=sandbox (Java SDK v8)

Verifies the v8 contract: a Java SDK client constructed with
`Mode.SANDBOX` produces an anonymous heartbeat ping that lands in
checkpoint DynamoDB with the row tagged `stream="sandbox"`.

## When to run

**Post-deploy verification.** Two infrastructure prerequisites:

1. **`axonflow-enterprise` PR #2005 deployed** — without the server-side
   wire-allowlist, the Lambda hardcodes `stream=heartbeat` regardless of
   payload, and this test will fail at the assertion step. Confirm with:
   ```sh
   curl -sS -X POST -H 'Content-Type: application/json' \
     -d '{"sdk":"java","sdk_version":"8.0.0","stream":"community_saas_operational","instance_id":"x"}' \
     https://checkpoint.getaxonflow.com/v1/ping
   # Expect HTTP 400 "invalid stream value"
   ```
2. **AWS credentials** with read on `/aws/lambda/prod-axonflow-checkpoint`.

## Usage

```sh
AWS_REGION=us-east-1 ./test.sh
```

## What it asserts

1. Builds the local SDK to the Maven local repo via `mvn install -DskipTests`.
2. A small Java consumer that depends on `com.getaxonflow:axonflow-sdk:8.0.0` is
   compiled and run.
3. The consumer constructs `AxonFlow.create(AxonFlowConfig.builder()
   .endpoint("http://localhost:65530").mode(Mode.SANDBOX)...)` — pointing
   at an unreachable port so we exercise the heartbeat ping but not any
   platform call.
4. The Lambda's CloudWatch audit log records an `event_stored` row with
   `sdk=java/8` AND `stream=sandbox`.

## Pre-v8 behavior (regression-guard context)

In v7.x, sandbox-mode clients were silently suppressed by the SDK gate
(`mode != "sandbox"` default-off rule). This test guards against that
hole being re-introduced. If a future refactor restores any mode-based
suppression, this test fires loudly.
