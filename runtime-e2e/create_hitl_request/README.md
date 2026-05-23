# `createHITLRequest` — runtime-e2e

Real-stack assertion for the cross-SDK
[`createHITLRequest`](https://github.com/getaxonflow/axonflow-enterprise/issues/2421)
surface added in Java SDK v8.2.0. Sister proof to the equivalent Python
/ TypeScript / Go runtime-e2e tests shipping in the same parity sweep.

## What this proves

Drives `axonflow.createHITLRequest(...)` through the real `OkHttp`
transport against a JDK `com.sun.net.httpserver.HttpServer` listener
that mimics the platform handler at
`platform/agent/hitl/handler.go:177`. Captures the raw HTTP body,
decodes it, and asserts every required field from
`com.getaxonflow.sdk.types.hitl.HITLTypes.HITLCreateInput` lands on the
wire — including the new `notify_url` field added in
[#2419](https://github.com/getaxonflow/axonflow-enterprise/issues/2419)
— then asserts the SDK parses the platform's `APIResponse{success,
data}` envelope back into a populated `HITLApprovalRequest`.

No WireMock, no JUnit `@Test`, no test doubles — runs the production
OkHttp transport against an in-process JDK `HttpServer`, which is what
the `runtime-e2e/` DoD gate is asking for.

## Usage

```bash
./mvnw -q package -DskipTests
java -cp "target/axonflow-sdk-8.2.0.jar:$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stderr 2>&1 | tail -1)" \
  runtime-e2e/create_hitl_request/CreateHITLRequestTest.java
```

Exits `0` on PASS, `1` on FAIL. Prints captured wire body + parsed
response fields on success for human-readable confirmation.

## Companion unit coverage

`src/test/java/com/getaxonflow/sdk/HITLTest.java`
`@Nested CreateHITLRequest` exercises the same surface through
WireMock for eight scenarios (happy path full-fields, minimal
required-fields, bad-`notifyUrl`-scheme 400 propagation, 401
propagation, connect-failure propagation, and the three
`IllegalArgumentException` guards for missing required fields). The
runtime proof here is the redundant real-stack confirmation.
