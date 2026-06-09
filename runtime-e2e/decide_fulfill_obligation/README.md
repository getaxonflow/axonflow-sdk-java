# decide_fulfill_obligation (v8.5.0 — Decision Mode PEP, #2563 / #2571)

Real-stack proof that the SDK runs the Decision Mode PEP path
**decide → fulfill → forward** against a live AxonFlow enterprise agent, with
NO mocks and NO local redaction:

1. **`decide()`** on the PII-bearing query
   `"Send the receipt to john.doe@example.com and charge card 4111111111111111"`
   returns an `allow` verdict carrying a request-phase `redact_pii` obligation
   whose fulfillment names the request-redaction engine endpoint.
2. **`fulfillRequest()`** discharges that obligation through the engine and
   returns engine-masked content in which neither `john.doe@example.com` nor
   `4111111111111111` survives, and the content differs from the original. The
   SDK has no local redaction path — only the engine can produce this.
3. **`decideAndFulfill()`** yields the same masked content in one call.
4. **Demo credentials** (`demo-org` / `demo-license-not-real`) are refused with
   an `AuthenticationException` (HTTP 401).

## Run

```
source /tmp/axonflow-e2e-env.sh   # AXONFLOW_CLIENT_ID / _SECRET / _TENANT_ID / _USER_TOKEN
mvn -q -DskipTests package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
  runtime-e2e/decide_fulfill_obligation/DecideFulfillObligationTest.java
```

Exits non-zero (and prints `FAIL: ...`) if any step fails — e.g. if the PII
survives fulfillment or demo credentials are not refused.
