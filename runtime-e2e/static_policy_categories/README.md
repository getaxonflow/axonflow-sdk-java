# static_policy_categories

Real-stack proof that a static-policy read no longer fails on the platform's categories. `StaticPolicyCategoriesTest.java` drives the built SDK against a real agent and reads `GET /api/v1/static-policies/effective`, whose policies carry categories the SDK's enum did not name before this change.

## What it proves

| Step | Expected |
|---|---|
| `getEffectiveStaticPolicies()` | returns instead of throwing, and each policy's `getCategoryValue()` is exactly the `category` string the same agent sends on the wire (read a second time, raw, and compared by policy id) |
| every category the platform returns | is one the SDK names, and comes back as its `PolicyCategory` |
| the platform's `security-dangerous` policies | come back as `PolicyCategory.SECURITY_DANGEROUS` |
| `listStaticPolicies` by `categoryValue("security-dangerous")` and by the constant | only those policies, the same count both ways (the list route applies `category`) |
| `listStaticPolicies` by a category no platform names | an empty list, or a 400 or 422 refusal raised as the SDK's exception; never a parse failure |
| `getEffectiveStaticPolicies` with a category | completes and matches the wire; the effective route declares no query parameter, so it returns the whole set |
| credentials unset, a wrong secret | served and matching the wire, or the SDK's `AuthenticationException` (401); the leg prints which |

## A red on the category check is drift, not a broken read

This leg fails when the platform returns a category the SDK does not name. That is the drift the posture pin exists to catch: regenerate `tests/fixtures/shipped_posture_categories.json` from `platform/decision/pdp/shipped_posture.json` at the platform sha the stack ran, and add the constant to `PolicyCategory` (getaxonflow/axonflow-enterprise#4224). Runtime callers are unaffected meanwhile: the SDK keeps such a category as its string (`getCategoryValue()`), with `getCategory()` null, and the read succeeds.

## What it does not prove

A category a later platform adds would come back with `getCategory()` null and its string in `getCategoryValue()`, rather than failing the read. `StaticPolicyCategoryTest` proves that, and pins the SDK's known set to the platform's shipped-posture categories.

## Run

Boot an agent from the platform's main (community mode is enough), then:

```
AXONFLOW_AGENT_URL=http://localhost:8080 \
AXONFLOW_CLIENT_ID=runtime-e2e AXONFLOW_CLIENT_SECRET=runtime-e2e-secret \
./runtime-e2e/static_policy_categories/run.sh
```

It writes nothing to the stack, and exits non-zero on any failed assertion.
