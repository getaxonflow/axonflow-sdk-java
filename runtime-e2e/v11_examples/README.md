# v11_examples

Real-stack proof that the two v11 examples run as the README says. `run.sh` builds the SDK from this tree, compiles `examples/pep-handshake` and `examples/typed-policies` against it, and runs them against a live Community agent in the README's order. Nothing is mocked.

## What it proves

| Step | Expected |
|---|---|
| `pep-handshake` on a fresh stack | exits 0; the first decide's verdict is `allow`; the declaration the platform would refuse fails in the client at `/pep_id`, before anything is sent |
| `typed-policies` with `AXONFLOW_TYPED_POLICY_PUBLISH=1` | exits 0; the document is published and activated |
| `pep-handshake` again, after the activation | printed as an observation, not asserted (below) |

## Why the third run is an observation

After a document with an organization-scope constraint is activated, a decide that does not supply the attribute the constraint conditions on is denied fail-closed with reasons `["unknown_constraint"]`. The example's default document is such a document, so the third run shows that deny; it is the platform's by-design answer, not the SDK's, and this leg prints it rather than pinning it. That deny names no evaluated policy, which is getaxonflow/axonflow-enterprise#4227. It is why the README runs the handshake example first.

## Run

Community on the application database role (typed publishing needs an edition that lets a sole author publish). On a fresh stack:

```
AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/v11_examples/run.sh
```

Every step uses `AXONFLOW_CLIENT_ID` and `AXONFLOW_CLIENT_SECRET`, defaulting to `runtime-e2e` / `runtime-e2e-secret`; a Community deployment accepts any credentials. It exits non-zero on any failed assertion, and it changes the organization's active policy.
