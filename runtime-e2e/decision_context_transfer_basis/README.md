# decision_context_transfer_basis (v8.4.0)

Real-stack proof for the v8.4.0 SDK surface (platform epic #2508):

- **`DecisionSummary.getContext()` / `DecisionExplanation.getContext()`
  (+ `isContextTruncated()`)** — the sanitized request context a PEP attaches to a
  Decision Mode call is surfaced back through `listDecisions` and `explainDecision`.
- **`AuditLogEntry` `transfer_basis = "pasal_56b_dpa"`** — the Pasal 56(b) explicit
  DPA tag round-trips through Jackson verbatim.

The driver acts as the PEP (raw `POST /api/v1/decide` — that endpoint is not
SDK-wrapped per ADR-056), then reads the decision back through the SDK against a
real running agent.

## Run

```
mvn -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
mvn -DskipTests -q package
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
CP="$SDK_JAR:$(cat /tmp/cp.txt)"
AXONFLOW_AGENT_URL=http://localhost:8080 \
  AXONFLOW_TENANT_ID=buku-e-java-e2e AXONFLOW_TENANT_SECRET=buku-e-secret \
  java -cp "$CP" runtime-e2e/decision_context_transfer_basis/DecisionContextTransferBasisTest.java
```

Exits non-zero if the SDK does not surface the new fields.
