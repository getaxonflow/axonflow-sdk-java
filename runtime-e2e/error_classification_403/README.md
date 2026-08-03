# error_classification_403 (403 auth vs policy-block classification, #2861)

Real-stack proof that the SDK classifies a 403 by the error envelope's
`blocked` boolean instead of substring matching, against a live AxonFlow
enterprise agent, with NO mocks.

Background: every agent error envelope carries a literal `"blocked"` JSON key —
a tenant-mismatch rejection is `{"success":false,"error":"Tenant mismatch",
"blocked":false}` — so `handleErrorResponse`'s old
`body.contains("policy") || body.contains("blocked")` heuristic misclassified
EVERY 403 authorization rejection as `PolicyViolationException`. Since the
documented caller pattern treats a policy block as an expected, non-fatal
outcome, real auth failures (wrong `AXONFLOW_CLIENT_ID`/user-token tenant
pairing) were silently swallowed with exit 0. Found by the #2861
release-readiness smoke of `examples/basic`.

The fix parses the JSON body: a present `blocked` boolean is authoritative
(`true` → `PolicyViolationException`, `false` → `AuthenticationException`);
only unparseable or `blocked`-less bodies fall back to the policy-phrase
heuristic (`policy` / `block_reason`, no longer the bare `blocked` key).

This test asserts both directions through the SDK's real public surface
(`proxyLLMCall`):

1. **Tenant mismatch → `AuthenticationException`.** A valid JWT for a
   different tenant than the client identity draws the agent's 403
   `"blocked":false` envelope and must NOT surface as a policy violation.
2. **Genuine block → `PolicyViolationException`.** A stacked-SQLi query
   (`sys_sqli_stacked_drop`, `"blocked":true`) still surfaces as a policy
   violation.

## Run

```bash
# from the SDK root, against a live enterprise stack
source /tmp/axonflow-e2e-env.sh          # AXONFLOW_CLIENT_ID/SECRET, endpoint
export AXONFLOW_USER_TOKEN=...           # JWT, tenant matches AXONFLOW_CLIENT_ID
export AXONFLOW_MISMATCHED_USER_TOKEN=...# JWT for a DIFFERENT tenant
                                         # (platform repo: scripts/generate-jwt.sh --tenant-id <other>)

mvn install -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
  runtime-e2e/error_classification_403/ErrorClassification403Test.java
```

Expected output:

```
PASS [tenant-mismatch-403] AuthenticationException: ... Tenant mismatch ...
PASS [policy-block-403] PolicyViolationException: ... stacked DROP TABLE ...
RESULT: PASS (2/2)
```
