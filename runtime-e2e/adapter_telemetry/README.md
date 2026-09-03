# adapter_telemetry — real-wire proof of the adapter registry

Covers axonflow-enterprise#3682 for the Java SDK: `TelemetryReporter.registerAdapter`
puts `adapter:<name>` on the `features` array of the heartbeat that already fires,
`LangGraphAdapter` declares itself, an over-cap name is dropped whole, the relayed
platform-identity fields ride one `/health` fetch, and redirects are refused on **both**
telemetry legs.

## Run it

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
mvn -q package -DskipTests
java -cp "target/classes:$(cat /tmp/cp.txt)" \
  runtime-e2e/adapter_telemetry/AdapterTelemetryTest.java
```

Bring an agent up with the standard setup in
`axonflow-internal-docs/engineering/E2E_EXAMPLES_TESTING_WORKFLOW.md` if you want to point
the platform leg at a live one.

## Why there are listeners

The real checkpoint service is **production** — a runtime proof must not deliver test pings
to it. So the driver runs real JDK `HttpServer` listeners on both sides and the bytes flow
real → real through the SDK's own outbound `OkHttpClient`. Nothing about the SDK is mocked;
the stand-ins are the two *peers*, exactly as in the neighbouring `license_tier_telemetry`
driver.

There is **no registry reset** between cases, and that is deliberate rather than a
limitation worked around: `resetAdapterRegistryForTest` is package-private, and a test
helper should not become public API just so a driver can call it. The JVM starts with an
empty registry, so the cases run in sequence and each asserts on the specific names it
cares about rather than on the array being otherwise empty.

## What it asserts, and what it cannot

| # | Assertion |
|---|---|
| 1 | Constructing `LangGraphAdapter` alone puts `adapter:langgraph` on the wire — no application telemetry code. |
| 2 | An adapter nothing registered does not appear, with (1) as its positive control. |
| 3 | A 65-byte name is dropped **whole**: neither sent in full nor truncated to 64, and it does not take the valid name with it. |
| 4 | `edition` and `platform_deployment_mode` arrive from **one** `/health` fetch (the count is asserted), and the platform's own mode does **not** overwrite the SDK's `deployment_mode` topology. |
| 5 | A 302 on `/health` and a 302 on the checkpoint POST are both refused, each proven with **two** listeners where the second one records. |

**Cannot vary:** the receiver's behaviour — `NormalizeAdapterFeature`'s read-time bucketing
lives in another repo and is asserted there. That separation is the point: this SDK sends
the caller's name and takes no view on the vocabulary.

**Cannot vary:** the **scheme**. Both listeners are local `http`, so an `https → http`
downgrade is not exercised. This is the same blind spot that hid the Go
per-user-credential leak in #3651. It does not apply to this path — the telemetry clients
send no credential and no `Authorization` header — but it is stated rather than left
implied. Note that `followSslRedirects(false)` is set precisely because that is the
setting governing the scheme-crossing hop.

The redirect cases use **two** listeners because a single-listener fixture cannot express
the defect: if the redirector and the target are the same process, a followed redirect and
a refused one are indistinguishable. Each asserts on what the *second* listener saw **and**
carries a positive control that the *first* was actually contacted — otherwise "the target
saw nothing" is equally true of a run that never happened.

## Mutation proof

| Mutation | Expected failure |
|---|---|
| Delete `registerAdapter("langgraph")` from `LangGraphAdapter`'s constructor | case 1: `features = []` |
| Drop the length guard in `registerAdapter` (truncate instead) | case 3: `the 65-byte name was TRUNCATED to 64 and sent` |
| Remove `followRedirects(false)` from the `/health` client | case 5: `the TARGET was fetched 1 times` |
| Remove `followRedirects(false)` from the checkpoint client | case 5: `the TARGET received 1 request(s)` |

The unit suite (`src/test/java/.../AdapterRegistryTest.java`) carries the same mutants plus
the byte-vs-code-unit boundary; all twelve were run and observed red.
