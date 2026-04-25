#!/usr/bin/env python3
"""QF-14 Java arm — wire-shape contract validator.

Blocks PRs that introduce drift between Java classes (with
@JsonProperty annotations) and the OpenAPI specs pinned via
openapi_specs_sha in tests/fixtures/wire-shape-baseline.json.

Four gates, same classes as the Python/Go/TS validators:
1. Cross-spec schema divergence (same name, different shapes)
2. Intra-file schema duplicates (PolicyMatch-class bug)
3. Per-type SDK-vs-spec drift (baseline-aware)
4. Registered-type coverage (rename-escape guard)

Specs dir is passed via AXONFLOW_OPENAPI_SPECS_DIR. Without it, the
script exits 0 after a skip message so `mvn test` and local work
don't require a specs checkout.

Usage:
    AXONFLOW_OPENAPI_SPECS_DIR=/path/to/docs/api \\
      python3 scripts/wire_shape/validate.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lib import (  # noqa: E402
    difference,
    discover_sdk_types,
    load_all_schemas,
    load_baseline,
)


def main() -> int:
    env = os.environ.get("AXONFLOW_OPENAPI_SPECS_DIR")
    if not env:
        # No env at all → local dev / unconfigured CI. Skip.
        print(
            "⏭️  AXONFLOW_OPENAPI_SPECS_DIR not set; wire-shape gate skipped."
        )
        print(
            "    The dedicated CI job clones getaxonflow/axonflow at the "
            "pinned SHA and exports this variable before running the "
            "validator."
        )
        return 0
    specs = Path(env)
    if not specs.is_dir():
        # Env set but path is bogus. CI probably failed to check out the
        # specs at the pinned SHA; treating that as a skip would let a
        # broken pipeline produce a green check. Fail loudly instead.
        print(
            f"❌ AXONFLOW_OPENAPI_SPECS_DIR={env} is not a directory.",
            file=sys.stderr,
        )
        print(
            "   The wire-shape job's specs-checkout step must run before "
            "this validator. A misconfigured path silently disables the "
            "gate, which we refuse to do.",
            file=sys.stderr,
        )
        return 1

    merged, cross_spec, intra_file = load_all_schemas(specs)
    if not merged:
        print(
            f"❌ Loaded 0 schemas with concrete properties from {specs}.",
            file=sys.stderr,
        )
        return 1
    print(f"📋 Loaded {len(merged)} schema(s) from {specs}\n")

    sdk = discover_sdk_types()
    baseline = load_baseline()
    errors = 0

    # Gate 1: cross-spec divergence.
    baselined_cross = baseline["cross_spec_duplicates"]
    cross_problems: list[str] = []
    for name, observed in cross_spec.items():
        expected = baselined_cross.get(name)
        if expected is None:
            lines = [f"  {name}: NEW cross-spec divergence (not in baseline)."]
            for spec in sorted(observed):
                lines.append(f"    {spec}: {observed[spec]}")
            cross_problems.append("\n".join(lines))
            continue
        if dict(expected) != {s: list(v) for s, v in observed.items()}:
            lines = [f"  {name}: divergence drifted from baseline."]
            all_specs = sorted(set(expected) | set(observed))
            for spec in all_specs:
                exp = expected.get(spec)
                obs = observed.get(spec)
                if exp != (list(obs) if obs is not None else None):
                    lines.append(f"    {spec}:")
                    lines.append(f"      baseline: {exp}")
                    lines.append(f"      observed: {obs}")
            cross_problems.append("\n".join(lines))
    if cross_problems:
        print("Cross-spec schema divergence gate failed:\n", file=sys.stderr)
        for p in cross_problems:
            print(p + "\n", file=sys.stderr)
        print(
            "Fix: reconcile in axonflow-enterprise specs (rename one, or "
            "merge into a shared supertype). If the divergence is "
            "intentional and must stand, regenerate "
            "tests/fixtures/wire-shape-baseline.json via "
            "scripts/wire_shape/refresh.py.\n",
            file=sys.stderr,
        )
        errors += len(cross_problems)

    # Gate 2: intra-file duplicates.
    baselined_intra = baseline["intra_file_duplicates"]
    intra_problems: list[str] = []
    for file, schemas in intra_file.items():
        for schema_name, count in schemas.items():
            allowed = baselined_intra.get(file, {}).get(schema_name)
            if allowed == count:
                continue
            intra_problems.append(
                f"  {file}: schema '{schema_name}' declared {count} time(s) "
                f"(baseline says {allowed or 0})."
            )
    for file, schemas in baselined_intra.items():
        for schema_name in schemas:
            if schema_name not in intra_file.get(file, {}):
                intra_problems.append(
                    f"  {file}: baselined duplicate '{schema_name}' no longer "
                    f"observed — remove from baseline.intra_file_duplicates."
                )
    if intra_problems:
        intra_problems.sort()
        print("Intra-file schema duplicate gate failed:\n", file=sys.stderr)
        for p in intra_problems:
            print(p, file=sys.stderr)
        print(
            "\nFix: remove the duplicate declaration in the OpenAPI spec. "
            "A schema declared twice in one file leaves the contract "
            "ambiguous. If the duplicate is intentional and must stand, "
            "regenerate the baseline.\n",
            file=sys.stderr,
        )
        errors += len(intra_problems)

    # Gate 3: SDK-vs-spec drift, baseline-aware.
    baselined_drift = baseline["per_type_drift"]
    drift_problems: list[str] = []
    matched = 0

    for name, sdk_fields in sdk.items():
        spec_fields = merged.get(name)
        if spec_fields is None:
            continue
        matched += 1
        sdk_only = difference(sdk_fields, spec_fields)
        spec_only = difference(spec_fields, sdk_fields)
        allowed = baselined_drift.get(name, {"sdk_only": [], "spec_only": []})
        new_sdk = difference(sdk_only, allowed.get("sdk_only", []))
        new_spec = difference(spec_only, allowed.get("spec_only", []))
        if not new_sdk and not new_spec:
            continue
        lines = [f"  {name}:"]
        if new_sdk:
            lines.append(f"    NEW, only in SDK class: {new_sdk}")
        if new_spec:
            lines.append(f"    NEW, only in OpenAPI:   {new_spec}")
        residual_sdk = difference(sdk_only, new_sdk)
        residual_spec = difference(spec_only, new_spec)
        if residual_sdk:
            lines.append(f"    (baseline, only in SDK):  {residual_sdk}")
        if residual_spec:
            lines.append(f"    (baseline, only in spec): {residual_spec}")
        drift_problems.append("\n".join(lines))

    if matched == 0:
        print(
            "❌ No Java class matched any OpenAPI schema by name — check discovery.",
            file=sys.stderr,
        )
        return 1

    if drift_problems:
        drift_problems.sort()
        print("NEW wire-shape drift detected (not covered by baseline):\n", file=sys.stderr)
        for p in drift_problems:
            print(p, file=sys.stderr)
        print(
            "\nFix: align the Java @JsonProperty name with the OpenAPI property "
            "name, OR update the spec if the SDK is the source of truth. Do "
            "not widen the baseline to hide drift without a tracking issue.\n",
            file=sys.stderr,
        )
        errors += len(drift_problems)

    # Gate 4: registered-type coverage (rename-escape guard).
    registered = baseline["registered_types"]
    if registered:
        missing_sdk = [n for n in registered if n not in sdk]
        missing_spec = [n for n in registered if n not in merged]
        if missing_sdk or missing_spec:
            print(
                "Registered-type mapping broken — rename-escape guard fired:\n",
                file=sys.stderr,
            )
            if missing_sdk:
                print(f"  No matching Java class for: {missing_sdk}", file=sys.stderr)
            if missing_spec:
                print(f"  No matching OpenAPI schema for: {missing_spec}", file=sys.stderr)
            print(
                "\nFix: revert the rename, do it on both sides, or update "
                "tests/fixtures/wire-shape-baseline.json::registered_types "
                "(and mirror the rename in baseline.per_type_drift entries).\n",
                file=sys.stderr,
            )
            errors += len(missing_sdk) + len(missing_spec)

    if errors > 0:
        print(f"❌ Found {errors} wire-shape issue(s).", file=sys.stderr)
        return 1

    print(f"✅ {matched} Java class/schema pair(s) validated against OpenAPI.")
    unmapped_sdk = sum(1 for k in sdk if k not in merged)
    unmapped_spec = sum(1 for k in merged if k not in sdk)
    print(
        f"   {unmapped_sdk} SDK-only class(es) with no matching schema "
        "(internal / client-side)."
    )
    print(
        f"   {unmapped_spec} OpenAPI schema(s) with no matching SDK class "
        "(coverage gap)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
