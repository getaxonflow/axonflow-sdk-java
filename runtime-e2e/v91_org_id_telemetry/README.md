# Runtime proof — `org_id` in SDK telemetry payload (v9.1)

Verifies the v9.1 contract for the Java SDK: every telemetry heartbeat
body carries an `org_id` field, populated from the `ORG_ID` env var
with a `local-dev-org` sentinel fallback. Issue #2277.

## Usage

Build the SDK first:

```sh
mvn package -DskipTests

# ORG_ID set — operator-supplied (self-hosted) or cs_<uuid>:
ORG_ID=acme-corp java -cp "target/axonflow-sdk-8.1.0.jar:target/dependency/*" \
  runtime-e2e/v91_org_id_telemetry/V91OrgIdTelemetryTest.java

# ORG_ID unset — local-dev-org sentinel:
unset ORG_ID && java -cp "target/axonflow-sdk-8.1.0.jar:target/dependency/*" \
  runtime-e2e/v91_org_id_telemetry/V91OrgIdTelemetryTest.java
```

(Depending on Maven layout, you may need `mvn dependency:copy-dependencies`
to populate `target/dependency/`.)

Expected output:

```
PASS: telemetry wire payload carries org_id="acme-corp" (expected="acme-corp")
Wire body: {"telemetry_type":"sdk","sdk":"java", ... ,"org_id":"acme-corp"}
```

## CI coverage

Functional E2E equivalent runs in CI via WireMock-based tests in
`src/test/java/com/getaxonflow/sdk/telemetry/TelemetryReporterTest.java`:

- `v9.1: telemetryOrgId returns ORG_ID env when set`
- `v9.1: telemetryOrgId returns local-dev-org sentinel when ORG_ID unset`
- `v9.1: telemetryOrgId treats empty ORG_ID as unset`
- `v9.1: telemetryOrgId passes through cs_<uuid> Community SaaS tenant identifier`
- `v9.1: buildPayload includes ORG_ID env on the wire`
- `v9.1: buildPayload emits local-dev-org sentinel when ORG_ID unset`
- `v9.1: buildPayload passes through cs_<uuid> on the wire`
- `v9.1: functional E2E — ORG_ID arrives on the wire at the receiver (WireMock real HTTP)`
- `v9.1: functional E2E — sentinel arrives on the wire when ORG_ID unset`

## Mutation proof

Remove the `root.put("org_id", telemetryOrgId());` line in
`TelemetryReporter.buildPayload` and rerun. The proof exits with
`FAIL: wire org_id = "<MISSING>", want "<expected>"` and JsonNode
returns missing-node fallback `""`.

## Cross-SDK parity

Companion runtime-e2e tests live under the same subdirectory in the
other 4 SDKs:

- `axonflow-sdk-go/runtime-e2e/v91_org_id_telemetry/`
- `axonflow-sdk-python/runtime-e2e/v91_org_id_telemetry/`
- `axonflow-sdk-typescript/runtime-e2e/v91_org_id_telemetry/`
- `axonflow-sdk-rust/runtime-e2e/v91_org_id_telemetry/`

All five SDKs emit `org_id` with the same wire name, same sentinel
value (`local-dev-org`), and the same precedence (env → sentinel).
