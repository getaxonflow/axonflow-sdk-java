# v11_deprecations

Real-stack proof of the v11.0.0 deprecations through the SDK. `V11DeprecationsTest.java` calls the three policy simulation methods the SDK marks `@Deprecated` (`simulatePolicies`, `detectPolicyConflicts` and `getPolicyImpactReport`) and the two retired override writes against a real v11 agent and orchestrator, and reads what `AxonFlowConfig.Builder.onRouteDeprecation` reports. Nothing is mocked.

Requires a v11.1.0 or later platform: a v11.0.0 platform stamps `X-AxonFlow-Removed-In: v11.1`, and this leg reds against it.

## What it proves

| Step | Expected |
|---|---|
| `simulatePolicies`, `detectPolicyConflicts` | keep answering; each route reported exactly once, with `X-AxonFlow-Removed-In: v12.0` and the successor exactly `/api/v1/typed-policies` from the `Link` |
| `getPolicyImpactReport` | a non-2xx refusal (see below), reported once the same way |
| the RFC 9745 `Deprecation` header | absent, or `@<unix seconds>`: the platform adds it once v11.0.0 is tagged and omits it until then |
| a second call of each, and a call from a client derived with `asUser` | nothing new reported: once per route per client family |
| `createPolicyOverride`, `deletePolicyOverride` | `LegacyPolicyWriteFrozenException`, 409 `LEGACY_POLICY_WRITE_FROZEN`, whose message names `/api/v1/typed-policies`; the leg prints the platform's message |
| two static policies read by id | reported once, as `GET /api/v1/static-policies/{id}`; no report names a concrete id |
| a wrong secret | refused with the SDK's `AuthenticationException` (401), not swallowed |

## Why the impact report is proved on its signal, not on a result

The platform evaluates the named policy from the organization's tenant policies. A fresh organization has none, and a v11 platform refuses to create one (the legacy write freeze), so on a fresh v11 stack the call names a policy that cannot exist. The leg asserts only that the platform refuses it with a non-2xx (today a `500`, tracked in getaxonflow/axonflow-enterprise#4223), so a fix to that status leaves this leg green. The deprecation is stamped whatever the handler answers, and that is what this leg proves for the route. On an organization that still holds legacy tenant policies, the call returns its report until v12.0.

## Run

The simulation routes are registered from the Evaluation licence up, so boot an enterprise stack from the platform's main (`scripts/setup-e2e-testing.sh production-posture`). Then, from the repository root, with the credentials that script writes:

```
set -a; source /tmp/axonflow-e2e-env.sh; set +a
AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/v11_deprecations/run.sh
```

It exits non-zero on any failed assertion. It writes nothing the platform keeps: both override writes are refused.
