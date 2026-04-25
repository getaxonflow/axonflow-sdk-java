"""Shared helpers for the wire-shape contract CI (QF-14 Java arm).

Used by:
- scripts/wire_shape/validate.py  (the PR-blocking gate)
- scripts/wire_shape/refresh.py   (the baseline regenerator)

Mirrors axonflow-sdk-python's tests/test_wire_shape.py,
axonflow-sdk-go's internal/wireshape package, and axonflow-sdk-
typescript's scripts/wire-shape/lib.js so all four SDKs' gates stay
conceptually aligned.

Design decisions specific to Java:
- Discovery scans Java source for `class Name { ... }` declarations
  and `@JsonProperty("wire_name")` annotations. A naive regex that
  only captures the outermost class per file under-covers the SDK:
  WorkflowTypes.java alone holds 10+ wire types nested inside an
  outer class. The parser below tracks brace depth (after stripping
  strings + comments) so annotations are attributed to the innermost
  class whose body contains them.
- Avoids the `mvn compile` round-trip in CI and stays dependency-free
  beyond PyYAML. If nested-generic or annotation-inside-string edge
  cases start causing false positives, switch to `javalang`.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SRC_MAIN_JAVA = REPO_ROOT / "src" / "main" / "java"
BASELINE_PATH = REPO_ROOT / "tests" / "fixtures" / "wire-shape-baseline.json"

# Matches a class declaration up to (but not including) the opening
# brace. Works at any brace depth — nesting is handled by the parser
# in discover_sdk_types, not by the regex. `record` and `interface`
# types that declare @JsonProperty are treated like classes.
_CLASS_DECL_RE = re.compile(
    r"\b(?:public\s+|final\s+|abstract\s+|static\s+|sealed\s+|non-sealed\s+|private\s+|protected\s+)*"
    r"(?:class|record|interface|enum)\s+(\w+)"
)
_JSON_PROPERTY_RE = re.compile(r'@JsonProperty\(\s*"([^"]+)"\s*\)')


def load_all_schemas(spec_dir: Path) -> tuple[
    dict[str, list[str]],
    dict[str, dict[str, list[str]]],
    dict[str, dict[str, int]],
]:
    """Load every *.yaml in spec_dir.

    Returns (merged, cross_spec_duplicates, intra_file_duplicates).

    - merged[name] = sorted property names; last-loaded declaration wins
      on cross-spec name collision (matches Python/Go/TS behavior).
    - cross_spec_duplicates[name] = {spec_file: [fields]} for schemas
      declared in >1 file with DIFFERENT shapes. Identical redundant
      declarations are benign and filtered out.
    - intra_file_duplicates[file][name] = count of declarations for
      schemas declared >1 time in a single file. PyYAML's default
      SafeLoader collapses duplicate keys silently (the real
      PolicyMatch bug in orchestrator-api.yaml); we walk the YAML
      mapping tree manually to preserve the count before the collapse.
    """
    merged: dict[str, list[str]] = {}
    all_decls: dict[str, dict[str, list[str]]] = {}
    intra_file_duplicates: dict[str, dict[str, int]] = {}

    for spec_file in sorted(spec_dir.glob("*.yaml")):
        text = spec_file.read_text()
        # yaml.compose preserves node-level structure (duplicate keys
        # show up as separate mapping-pair entries) unlike yaml.safe_load.
        root = yaml.compose(text)
        schemas_node = _find_schemas_node(root)
        if schemas_node is None:
            continue

        intra_counts: dict[str, int] = {}
        for key_node, value_node in schemas_node.value:
            if not isinstance(key_node.value, str):
                continue
            schema_name = key_node.value
            intra_counts[schema_name] = intra_counts.get(schema_name, 0) + 1

            props_node = _find_mapping_child(value_node, "properties")
            if props_node is None:
                continue
            fields = sorted(
                k.value
                for k, _ in props_node.value
                if isinstance(k.value, str)
            )
            if not fields:
                continue
            if schema_name not in all_decls:
                all_decls[schema_name] = {}
            all_decls[schema_name][spec_file.name] = fields
            merged[schema_name] = fields

        for schema_name, count in intra_counts.items():
            if count > 1:
                intra_file_duplicates.setdefault(spec_file.name, {})[schema_name] = count

    cross_spec_duplicates: dict[str, dict[str, list[str]]] = {}
    for schema_name, decls in all_decls.items():
        if len(decls) < 2:
            continue
        shapes = {tuple(v) for v in decls.values()}
        if len(shapes) > 1:
            cross_spec_duplicates[schema_name] = decls

    return merged, cross_spec_duplicates, intra_file_duplicates


def _find_schemas_node(root):
    top = root
    if top is None:
        return None
    if top.__class__.__name__ != "MappingNode":
        # DocumentNode isn't exposed by yaml.compose at the root; what
        # we get is directly the top-level mapping. If for some reason
        # it isn't a mapping, bail.
        return None
    components = _find_mapping_child(top, "components")
    if components is None or components.__class__.__name__ != "MappingNode":
        return None
    schemas = _find_mapping_child(components, "schemas")
    if schemas is None or schemas.__class__.__name__ != "MappingNode":
        return None
    return schemas


def _find_mapping_child(mapping_node, key):
    for k_node, v_node in mapping_node.value:
        if isinstance(k_node.value, str) and k_node.value == key:
            return v_node
    return None


def _strip_strings_and_comments(src: str) -> str:
    """Return source with string literals, char literals, // line
    comments, and /* block */ comments replaced with equal-length
    whitespace. Preserves character offsets so subsequent regex
    matches line up with the original text.
    """
    out = list(src)
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        # Line comment.
        if c == "/" and nxt == "/":
            j = i
            while j < n and src[j] != "\n":
                out[j] = " "
                j += 1
            i = j
            continue
        # Block comment.
        if c == "/" and nxt == "*":
            j = i
            out[j] = " "
            out[j + 1] = " "
            j += 2
            while j < n - 1 and not (src[j] == "*" and src[j + 1] == "/"):
                if src[j] != "\n":
                    out[j] = " "
                j += 1
            if j < n - 1:
                out[j] = " "
                out[j + 1] = " "
                j += 2
            i = j
            continue
        # String / char literal. Handles escapes; skips newlines (text
        # blocks in Java 15+ break this but none of our SDK types use them).
        if c == '"' or c == "'":
            quote = c
            j = i + 1
            out[i] = " "
            while j < n and src[j] != quote:
                if src[j] == "\\" and j + 1 < n:
                    if src[j + 1] != "\n":
                        out[j] = " "
                    if src[j + 1] != "\n":
                        out[j + 1] = " "
                    j += 2
                    continue
                if src[j] != "\n":
                    out[j] = " "
                j += 1
            if j < n:
                out[j] = " "
                j += 1
            i = j
            continue
        i += 1
    return "".join(out)


def _extract_types_from_java(content: str) -> dict[str, list[str]]:
    """Attribute @JsonProperty annotations to the innermost enclosing
    class/record/interface by walking brace depth. Returns
    {TypeName: [wire_names]}.

    Two passes:
    - Structural (cleaned): strings + comments stripped so stray `{`
      or `class X` inside a string/comment can't confuse brace or
      decl tracking. Whitespace-replacement preserves offsets.
    - Annotations (raw): scanned against ORIGINAL content because
      @JsonProperty("wire_name") puts the name INSIDE a string
      literal — cleaned text would have zeroed it out.
    """
    cleaned = _strip_strings_and_comments(content)

    # Class declarations: (end_of_decl_token, type_name). Matched on
    # cleaned so `class X` inside a string can't false-match.
    decls: list[tuple[int, str]] = [
        (m.end(), m.group(1)) for m in _CLASS_DECL_RE.finditer(cleaned)
    ]

    # @JsonProperty annotations: match on the RAW content so the
    # wire-name string literal is preserved. Positions line up with
    # `cleaned` because string-stripping replaces in place without
    # changing length.
    props: list[tuple[int, str]] = [
        (m.start(), m.group(1)) for m in _JSON_PROPERTY_RE.finditer(content)
    ]

    # Walk the cleaned source, tracking a stack of (open_brace_pos, type_name).
    # When we see `class X` followed (after modifiers/generics) by `{`,
    # push X onto the stack at that depth. Pop on matching `}`.
    stack: list[str] = []
    # Map from brace_depth_at_open -> type_name, so we only pop type
    # frames when the matching `}` fires (not every random `{` in
    # method bodies / initializers).
    type_frames: dict[int, str] = {}
    depth = 0
    next_decl_idx = 0
    next_prop_idx = 0
    result: dict[str, list[str]] = {}

    i = 0
    n = len(cleaned)
    while i < n:
        c = cleaned[i]

        # Consume any pending annotation at or before this index.
        while next_prop_idx < len(props) and props[next_prop_idx][0] <= i:
            _, wire = props[next_prop_idx]
            if stack:
                result.setdefault(stack[-1], []).append(wire)
            next_prop_idx += 1

        if c == "{":
            # Is this the opening brace of the most recent unconsumed
            # class declaration? i.e. the declaration's class-name
            # token ended before this `{` and no other `{` has consumed it.
            if (
                next_decl_idx < len(decls)
                and decls[next_decl_idx][0] <= i
            ):
                name = decls[next_decl_idx][1]
                stack.append(name)
                type_frames[depth] = name
                next_decl_idx += 1
            depth += 1
        elif c == "}":
            depth -= 1
            if depth in type_frames:
                type_frames.pop(depth)
                if stack:
                    stack.pop()
        i += 1

    # Consume any trailing annotations (defensive; shouldn't happen).
    while next_prop_idx < len(props):
        _, wire = props[next_prop_idx]
        if stack:
            result.setdefault(stack[-1], []).append(wire)
        next_prop_idx += 1

    return {k: sorted(set(v)) for k, v in result.items() if v}


def discover_sdk_types() -> dict[str, list[str]]:
    """Walk src/main/java and return {TypeName: sorted_wire_field_names}.

    Nested classes are recognised — annotations are attributed to the
    innermost enclosing type, not the file's outer class. A file
    without any @JsonProperty contributes nothing.
    """
    result: dict[str, list[str]] = {}
    for java_file in sorted(SRC_MAIN_JAVA.rglob("*.java")):
        content = java_file.read_text()
        if "@JsonProperty" not in content:
            continue
        for name, fields in _extract_types_from_java(content).items():
            # Merge across files. Same-name types in different files
            # (shouldn't happen in this SDK but keep the rule explicit
            # and matching Python/Go/TS semantics) get their fields
            # unioned; the LAST-loaded file wins on a genuine clash.
            result[name] = fields
    return result


def empty_baseline() -> dict[str, Any]:
    return {
        "openapi_specs_sha": "",
        "cross_spec_duplicates": {},
        "intra_file_duplicates": {},
        "registered_types": [],
        "per_type_drift": {},
    }


def load_baseline() -> dict[str, Any]:
    if not BASELINE_PATH.exists():
        return empty_baseline()
    with BASELINE_PATH.open() as f:
        parsed = json.load(f)
    base = empty_baseline()
    base.update(parsed)
    base["cross_spec_duplicates"] = parsed.get("cross_spec_duplicates", {})
    base["intra_file_duplicates"] = parsed.get("intra_file_duplicates", {})
    base["registered_types"] = parsed.get("registered_types", [])
    base["per_type_drift"] = parsed.get("per_type_drift", {})
    return base


def write_baseline(baseline: dict[str, Any]) -> None:
    BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = BASELINE_PATH.with_suffix(f".json.tmp.{os.getpid()}")
    with tmp.open("w") as f:
        json.dump(baseline, f, indent=2, sort_keys=True)
        f.write("\n")
    tmp.replace(BASELINE_PATH)


def difference(a: list[str], b: list[str]) -> list[str]:
    bs = set(b)
    return sorted(x for x in a if x not in bs)
