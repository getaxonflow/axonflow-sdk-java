# Runtime proof — `license_tier` in SDK telemetry (#3619)

Verifies that the SDK reports the connected platform's licence tier on its telemetry heartbeat, reads it from the `/health` response it **already** fetches for `platform_version`, and **omits** the field on every path where the tier could not be learned.

Closes the gap where telemetry could not distinguish an enterprise-licensed deployment from an unlicensed community one.

## Usage

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:$(cat /tmp/cp.txt)"

# 1. MATRIX — every tier value and every fail-open path, against a local stand-in platform.
java -cp "$CP" runtime-e2e/license_tier_telemetry/LicenseTierTelemetryTest.java

# 2. REAL PLATFORM — drive the SDK at a live agent and cross-check the wire
#    value against that agent's own /health.
AXONFLOW_E2E_PLATFORM_ENDPOINT=http://localhost:8080 \
  java -cp "$CP" runtime-e2e/license_tier_telemetry/LicenseTierTelemetryTest.java
```

Mode 2 is the one that proves the contract end to end: it reads the tier from the live platform independently, then asserts the SDK put *that* value on the wire verbatim. If the endpoint is unreachable it asserts the **platform-down** contract instead — ping still delivered, field omitted.

Both listeners are real JDK `HttpServer` instances and the SDK's own OkHttp client does the sending. No WireMock, no stubs — the `lint-no-mocks-in-runtime-e2e.sh` gate enforces this for everything under `runtime-e2e/`.

## What it asserts

1. `community`, `evaluation`, `Enterprise`, the csaas `Plus` alias and the transient `starting` each reach the wire byte-for-byte. No client-side case folding or alias mapping — normalization is the receiver's job (checkpoint-service `NormalizeLicenseTier`), and folding here would mask a tier this SDK build predates.
2. On every not-learned path — platform down, HTTP 500, malformed body, no `tier` key, empty `tier`, numeric `tier`, null `tier` — the ping is **still delivered** and `license_tier` is **absent** from the JSON. Never `""`, never `null`, never a substituted default.
3. `deployment_mode` is unchanged by the tier. The two dimensions stay separate.

## Omission, not null — and why the tier read is stricter than the version read

`platform_version` is written as an explicit JSON `null` when unknown, and that long-standing wire shape is unchanged. `license_tier` is different on purpose: the key is **omitted**. `null` is a claim ("the tier is nothing"); omission is what this wire uses for "we do not know", and the receiver preserves it for legacy pings.

The tier is also read with `isTextual()` rather than the version's `asText()`. `asText()` **coerces**, so a malformed `"tier": 42` or `"tier": true` would become `"42"`/`"true"` and land in the receiver's unknown bucket as though the platform had reported a tier. Absent is the honest answer. The version read keeps its existing coercing behaviour untouched.

## Mutation proof

| Mutation | Result |
|---|---|
| Delete `root.put("license_tier", licenseTier)` in `buildPayload` | 5 failures + 1 error |
| `putNull` instead of omitting when the tier is null | 12 failures |
| Restore a pre-#3619 early return once `version` is read | 1 failure (`tier only` row) |
| Relax `isTextual()` back to `!isNull()` | 2 failures (numeric + boolean tier rows) |
| Fold the tier client-side (`toLowerCase`) | 4 failures |

## CI coverage

The equivalent assertions run in CI as `TelemetryLicenseTierTest` (26 tests), which drive a real WireMock HTTP server on both legs. This runtime proof is a real-stack confirmation, not a CI gate.
