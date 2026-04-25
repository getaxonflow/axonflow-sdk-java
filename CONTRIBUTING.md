# Contributing to AxonFlow Java SDK

Thank you for your interest in contributing! Please open an issue or pull request via the [GitHub repository](https://github.com/getaxonflow/axonflow-sdk-java).

## Development setup

```bash
./mvnw verify
```

Tests run under JUnit 5. The wire-shape contract gate (under `scripts/wire_shape/`) runs in CI when a PR touches Java sources, the wire-shape baseline, or the gate scripts themselves — see `.github/workflows/wire-shape-contract.yml` for the exact path filter.

## Pull request guidelines

1. Keep PRs focused — one feature or fix per PR.
2. Update `CHANGELOG.md` under `[Unreleased]` for user-visible changes.
3. Ensure `./mvnw verify` is green and the wire-shape contract gate passes.
4. Prefer Jackson `@JsonProperty` on a `@JsonCreator` constructor for any new wire-bound POJO field — that propagates serialization both ways without hand-rolled transformer code.

## Baseline burndown policy

The wire-shape contract gate uses a baseline file (`tests/fixtures/wire-shape-baseline.json`) to grandfather pre-existing drift findings — the gate fails on any *new* drift but tolerates the listed entries. The baseline exists to land the gate without a giant cleanup PR; it is not intended to be permanent.

When your PR touches a type listed in the baseline, do one of:

- **Burn it down.** Realign the POJO with the OpenAPI spec in this PR, regenerate the baseline via `scripts/wire_shape/refresh.py`, and note "burndown: `<entry>`" in the PR description.
- **Justify it.** If the drift can't be resolved in this PR (different scope, blocked on a platform spec change, etc.), say so in the PR description in one line.

CI does not block PRs that touch a baselined type without addressing it, but reviewers will ask the burndown-or-justify question.

## Questions

- Open a GitHub issue
- Email: hello@getaxonflow.com
