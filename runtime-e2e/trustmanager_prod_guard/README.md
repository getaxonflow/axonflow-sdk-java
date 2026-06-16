# trustmanager_prod_guard (insecure-TLS production guard, #2711)

Real-stack proof that the SDK's hard production guard refuses the trust-all TLS
escape hatch in a production-like environment, against a live AxonFlow
enterprise agent over its **real, valid** TLS certificate, with NO mocks.

Background: the SDK's trust-all `TrustManager` is an intentional, double-gated
development affordance for self-signed certificates (it needs BOTH
`insecureSkipVerify(true)` AND the `AXONFLOW_INSECURE_TLS` env var). The
hardening adds a belt-and-suspenders guard: when a production-like deployment
environment is detected (an env var such as `ENVIRONMENT`, `AXONFLOW_ENVIRONMENT`,
`APP_ENV`, `SPRING_PROFILES_ACTIVE`, `NODE_ENV`, ... carrying a `prod`/`production`
token), the insecure path is refused and TLS certificate verification stays ON.

This test MUST be run with the guard tripped (`ENVIRONMENT=production` +
`AXONFLOW_INSECURE_TLS=true`) and asserts:

1. **Guard prevents (not just logs).** With `insecureSkipVerify(true)` AND the
   insecure env gate AND a production signal, `HttpClientFactory.create()` leaves
   OkHttp's default verifying hostname verifier in place; the permissive
   `(hostname, session) -> true` lambda is never installed.
2. **Connectivity preserved.** A real governed `decide()` call against the live
   HTTPS agent succeeds over the agent's valid certificate, proving the kept-on
   verification did not break legitimate connectivity.
3. **Parity.** The guarded client is verifier-identical to a plain config that
   never requested `insecureSkipVerify`.

## Run

```
source /tmp/axonflow-e2e-env.sh   # AXONFLOW_ENDPOINT (https) + AXONFLOW_CLIENT_ID / _SECRET / _USER_TOKEN
mvn -q -DskipTests package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
AXONFLOW_INSECURE_TLS=true ENVIRONMENT=production \
  java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
    runtime-e2e/trustmanager_prod_guard/TrustManagerProdGuardTest.java
```

Exits non-zero (and prints `FAIL: ...`) if any step fails, for example if the
guard let the trust-all path install, if the HTTPS call could not complete over
verified TLS, or if the guard env preconditions were not set.

The complementary negative/false-positive cases (hyphen/underscore-delimited prod
names detected, `non-prod`/`pre-prod` correctly ignored, the guard preventing the
insecure verifier) are covered deterministically by the unit suite in
`src/test/java/com/getaxonflow/sdk/util/HttpClientFactoryTest.java`.
