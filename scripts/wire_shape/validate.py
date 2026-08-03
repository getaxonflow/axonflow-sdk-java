#!/usr/bin/env python3
"""QF-14 Java arm — wire-shape contract validator.

Blocks PRs that introduce drift between Java classes (with
@JsonProperty annotations) and the OpenAPI specs pinned via
openapi_specs_sha in tests/fixtures/wire-shape-baseline.json.

Five gates:
1. Cross-spec schema divergence (same name, different shapes)
2. Intra-file schema duplicates (PolicyMatch-class bug)
3. Per-type SDK-vs-spec drift (baseline-aware)
4. Registered-type coverage (rename-escape guard)
(1-4 are the same classes as the Python/Go/TS validators.)
5. Audit-surface field binding (#3254): every wire key the COMPILED
   audit model classes actually map MUST exist as a property of the
   same-named schema in the pinned specs, unless it is explicitly
   allowlisted in tests/fixtures/audit-binding-allowlist.json with a
   note naming a tracking issue. Gate 3 is baseline-aware by design
   (drift recorded at refresh time stays green), which is exactly how
   seven never-served fields shipped on AuditLogEntry and stayed for
   months - the baseline RECORDED the fiction instead of binding the
   model to the contract. Gate 5 is the binding: it has no refresh
   path, only the curated allowlist, and an unresolvable binding
   (class, schema, or introspection probe missing) FAILS instead of
   skipping.

   Unlike gates 1-4, gate 5 does NOT use the source-regex discovery in
   lib.py: a regex cannot resolve a constant-valued annotation
   (@JsonProperty(SOME_CONSTANT)) and cannot see Jackson's getter
   auto-detection (an unannotated public getFoo() serializes `foo`
   with no annotation anywhere) - both were demonstrated as bypasses
   in review. Gate 5 asks Jackson itself, via
   scripts/wire_shape/AuditWireKeysProbe.java run against
   target/classes, so its view of the wire is the serializer's view.
   Prerequisites (CI compiles them in the workflow; locally run
   `mvn -q compile dependency:build-classpath
   -Dmdep.outputFile=target/wire-shape-cp.txt` first):
   target/classes and target/wire-shape-cp.txt.

Specs dir is passed via AXONFLOW_OPENAPI_SPECS_DIR. Without it, the
script exits 0 after a skip message so `mvn test` and local work
don't require a specs checkout.

Usage:
    AXONFLOW_OPENAPI_SPECS_DIR=/path/to/docs/api \\
      python3 scripts/wire_shape/validate.py
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lib import (  # noqa: E402
    REPO_ROOT,
    difference,
    discover_sdk_types,
    load_all_schemas,
    load_baseline,
)

# Gate 5 (audit-surface binding, #3254): the audit read/search surface is
# bound STRICTLY to the pinned spec schemas - the per_type_drift baseline
# does not apply here. Add a type to this tuple to put it under binding.
AUDIT_BINDING_TYPES = (
    "AuditLogEntry",
    "AuditSearchRequest",
    "AuditSearchResponse",
)
AUDIT_BINDING_PACKAGE = "com.getaxonflow.sdk.types"
AUDIT_BINDING_ALLOWLIST_PATH = (
    REPO_ROOT / "tests" / "fixtures" / "audit-binding-allowlist.json"
)
AUDIT_PROBE_SOURCE = Path(__file__).resolve().parent / "AuditWireKeysProbe.java"
TARGET_CLASSES = REPO_ROOT / "target" / "classes"
DEP_CLASSPATH_FILE = REPO_ROOT / "target" / "wire-shape-cp.txt"


def probe_audit_wire_keys() -> dict[str, list[str]]:
    """Ask Jackson (via AuditWireKeysProbe on the compiled classes) for the
    real wire-key set of every AUDIT_BINDING_TYPES class.

    Returns {SimpleTypeName: sorted_wire_keys}. Any missing prerequisite or
    probe failure raises SystemExit - an unresolvable binding must FAIL the
    gate, never weaken it to a skip.
    """
    problems: list[str] = []
    if shutil.which("java") is None:
        problems.append("`java` not on PATH.")
    if not AUDIT_PROBE_SOURCE.is_file():
        problems.append(f"probe source missing: {AUDIT_PROBE_SOURCE}")
    if not (
        TARGET_CLASSES / AUDIT_BINDING_PACKAGE.replace(".", "/")
    ).is_dir():
        problems.append(
            f"compiled SDK classes missing under {TARGET_CLASSES} - run "
            f"`mvn -q compile` first."
        )
    if not DEP_CLASSPATH_FILE.is_file():
        problems.append(
            f"{DEP_CLASSPATH_FILE} missing - run `mvn -q "
            f"dependency:build-classpath "
            f"-Dmdep.outputFile=target/wire-shape-cp.txt` first."
        )
    if problems:
        raise SystemExit(
            "❌ Audit-surface binding gate (#3254) prerequisites missing; "
            "the binding is unresolvable, which FAILS (never skips):\n  - "
            + "\n  - ".join(problems)
        )

    classpath = os.pathsep.join(
        [str(TARGET_CLASSES), DEP_CLASSPATH_FILE.read_text().strip()]
    )
    cmd = [
        "java",
        "-cp",
        classpath,
        str(AUDIT_PROBE_SOURCE),
    ] + [f"{AUDIT_BINDING_PACKAGE}.{t}" for t in AUDIT_BINDING_TYPES]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise SystemExit(
            f"❌ AuditWireKeysProbe failed (exit {proc.returncode}) - the "
            f"audit binding is unresolvable, which FAILS (never skips).\n"
            f"stderr:\n{proc.stderr.strip()}"
        )
    try:
        parsed = json.loads(proc.stdout)
    except json.JSONDecodeError as e:
        raise SystemExit(
            f"❌ AuditWireKeysProbe emitted unparseable output "
            f"({e.__class__.__name__}: {e}):\n{proc.stdout[:2000]}"
        ) from None
    for type_name in AUDIT_BINDING_TYPES:
        if not parsed.get(type_name):
            raise SystemExit(
                f"❌ AuditWireKeysProbe reported no wire keys for "
                f"{type_name} - an audit model type with zero mapped "
                f"properties means introspection broke; the binding is "
                f"unresolvable, which FAILS (never skips)."
            )
    return {k: sorted(v) for k, v in parsed.items()}


def load_audit_binding_allowlist() -> dict[str, dict[str, str]]:
    """Load the curated allowlist for Gate 5.

    Shape: {TypeName: {wire_field: "note naming the tracking issue"}}.
    Keys starting with "_" are comments. An absent file means an empty
    allowlist (strict binding). A malformed file or an entry without a
    non-empty note string fails loudly - a silent parse problem must not
    weaken the gate.
    """
    if not AUDIT_BINDING_ALLOWLIST_PATH.exists():
        return {}
    try:
        with AUDIT_BINDING_ALLOWLIST_PATH.open() as f:
            parsed = json.load(f)
    except json.JSONDecodeError as e:
        raise SystemExit(
            f"❌ {AUDIT_BINDING_ALLOWLIST_PATH} is malformed "
            f"({e.__class__.__name__}: {e}). Fix or delete it - a broken "
            f"allowlist must not weaken the audit binding gate."
        ) from None
    result: dict[str, dict[str, str]] = {}
    for type_name, fields in parsed.items():
        if type_name.startswith("_"):
            continue
        if not isinstance(fields, dict):
            raise SystemExit(
                f"❌ {AUDIT_BINDING_ALLOWLIST_PATH}: entry {type_name!r} "
                f"must map wire fields to note strings."
            )
        for field, note in fields.items():
            if not isinstance(note, str) or not note.strip():
                raise SystemExit(
                    f"❌ {AUDIT_BINDING_ALLOWLIST_PATH}: "
                    f"{type_name}.{field} has no justification note. Every "
                    f"allowlisted field must name its tracking issue."
                )
        result[type_name] = dict(fields)
    return result


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
    # Reverse pass: a baselined cross-spec divergence that is no longer
    # observed must be removed from the baseline. Otherwise the stale
    # fingerprint shields a future reintroduction of the same old
    # incompatible shape from the gate.
    for name in baselined_cross:
        if name not in cross_spec:
            cross_problems.append(
                f"  {name}: baselined cross-spec divergence no longer "
                f"observed — remove from "
                f"baseline.cross_spec_duplicates.{name} so a future "
                f"reintroduction of the same shape is caught as new."
            )
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

    # Gate 5: audit-surface field binding (#3254). Strict, baseline-free.
    # Wire keys come from Jackson introspection of the COMPILED classes
    # (probe_audit_wire_keys), NOT from the source-regex discovery used by
    # gates 1-4 - see the module docstring for the two demonstrated
    # regex bypasses (constant-valued annotations, getter auto-detection).
    # A class that cannot be loaded fails inside the probe (exit 2 ->
    # SystemExit here), so "class missing" is a hard failure, not a skip.
    allowlist = load_audit_binding_allowlist()
    probed = probe_audit_wire_keys()
    binding_problems: list[str] = []
    for type_name in AUDIT_BINDING_TYPES:
        sdk_fields = probed[type_name]
        spec_fields = merged.get(type_name)
        if spec_fields is None:
            binding_problems.append(
                f"  {type_name}: no OpenAPI schema of this name in the "
                f"pinned specs - the binding is unresolvable. This gate "
                f"fails instead of skipping; if the schema was renamed, "
                f"update AUDIT_BINDING_TYPES in the same PR."
            )
            continue
        allowed = allowlist.get(type_name, {})
        unbound = [
            f for f in difference(sdk_fields, spec_fields) if f not in allowed
        ]
        if unbound:
            binding_problems.append(
                f"  {type_name}: wire key(s) mapped by the compiled class "
                f"(Jackson introspection: @JsonProperty, constant-valued "
                f"annotations, and getter auto-detection alike) with NO "
                f"backing property in the pinned {type_name} schema: "
                f"{unbound}. A field the server never serves is fiction "
                f"(#3254 class): either the spec is missing it (fix the "
                f"contract first) or the field must not exist. If it must "
                f"stay temporarily, allowlist it WITH a tracking-issue note "
                f"in tests/fixtures/audit-binding-allowlist.json."
            )
        # Stale = allowlisted but no longer unbound: either the field left
        # the SDK class, or the spec now carries it. Both mean the entry
        # must go, so the allowlist only ever names live debt.
        stale = sorted(
            f for f in allowed if f not in difference(sdk_fields, spec_fields)
        )
        if stale:
            binding_problems.append(
                f"  {type_name}: allowlist entr{'ies' if len(stale) > 1 else 'y'} "
                f"{stale} no longer unbound (field removed from the SDK or "
                f"now present in the spec) - remove from "
                f"tests/fixtures/audit-binding-allowlist.json so the "
                f"allowlist only ever names live debt."
            )
        spec_missing = difference(spec_fields, sdk_fields)
        if spec_missing:
            # Informational only: fields the server serves that the SDK
            # does not model yet are a coverage gap, not fiction.
            print(
                f"ℹ️  {type_name}: spec fields not yet modeled in the SDK "
                f"(informational): {spec_missing}"
            )
    if binding_problems:
        print(
            "\nAudit-surface binding gate failed (#3254):\n", file=sys.stderr
        )
        for p in binding_problems:
            print(p + "\n", file=sys.stderr)
        errors += len(binding_problems)

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
