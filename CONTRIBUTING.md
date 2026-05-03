# Contributing to AxonFlow Java SDK

Thank you for your interest in contributing! Please open an issue or pull request via the [GitHub repository](https://github.com/getaxonflow/axonflow-sdk-java).

## Sign your commits — Developer Certificate of Origin (DCO) is required

All contributions to this repository must be **signed off** under the [Developer Certificate of Origin v1.1](https://developercertificate.org/). The DCO is a per-commit affirmation that you wrote the code (or otherwise have the right to submit it) and are licensing it under the same license as the rest of this repository.

Add the sign-off automatically with `-s` (or `--signoff`) on every commit:

```bash
git commit -s -m "your commit message"
```

This appends a trailer like:

```
Signed-off-by: Your Name <your.email@example.com>
```

The name and email must match `git config user.name` / `git config user.email`.

If you forgot `-s` on an existing commit, fix it with one of:

```bash
# most recent commit
git commit --amend --signoff --no-edit

# every commit on the current branch
git rebase --signoff origin/main
```

A DCO check runs automatically on every PR opened in the `getaxonflow` org. **PRs with any unsigned commit will be blocked from merging until the missing sign-offs are added.** No exceptions, including for maintainers.

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
