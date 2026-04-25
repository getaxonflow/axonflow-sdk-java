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
- Record-parameter annotations (`record Foo(@JsonProperty("x") int x)`)
  are attributed to the record because the parser tracks pending
  decls between their token and their body `{`.
- Anonymous inner classes (`new Foo() { @JsonProperty("x") ... }`) do
  NOT have a `class` keyword, so their annotations leak to the
  enclosing type. This SDK does not put `@JsonProperty` inside
  anonymous inners; if that ever lands, the `<anon>` sentinel pattern
  is the place to add coverage.
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

# Matches a Java instance field declaration that uses Jackson's
# default field-name mapping (no @JsonProperty alias). The pattern:
#   - the leading modifier sequence (group 1) — captured so we can
#     post-filter declarations that include `static` (constants
#     don't go on the wire)
#   - a type expression: identifier with optional `.qualifier`,
#     optional `<...>` generics, optional `[]` array
#   - the field name (group 2)
#   - terminated by `;` or `=` (initializer)
#
# `static` IS allowed in the modifier set (otherwise the regex would
# anchor at `final` in `private static final ...`); we drop the
# match in code when `static` appears in group 1.
# Methods are excluded by the absence of `(` before the terminator.
_FIELD_DECL_RE = re.compile(
    r"((?:(?:private|protected|public|static|final|transient|volatile)\s+)+)"
    r"(?:[\w.]+(?:\s*<[^<>;]*>)?(?:\s*\[\s*\])?)\s+"
    r"(\w+)\s*[;=]"
)
# Cap on lookback distance from a field declaration to a
# preceding @JsonProperty(...) annotation: if the most recent
# annotation sits within this many characters of the field's
# declaration position, the field is considered "annotated" and we
# defer to the @JsonProperty value rather than the field name.
# 250 chars covers an annotation on the line immediately above plus
# JavaDoc and other annotations like @JsonInclude in between.
_JSON_PROPERTY_LOOKBACK = 250


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
        # Java 15+ text block: `"""..."""`. Without explicit handling,
        # the leading `"""` looks like quote-emptyString-quote, then
        # content runs as live source until a stray `"` rebalances.
        if c == '"' and src[i : i + 3] == '"""':
            j = i + 3
            out[i] = out[i + 1] = out[i + 2] = " "
            while j < n and src[j : j + 3] != '"""':
                if src[j] != "\n":
                    out[j] = " "
                j += 1
            if j < n:
                out[j] = out[j + 1] = out[j + 2] = " "
                j += 3
            i = j
            continue
        # String / char literal. Handles escapes; preserves newlines so
        # offsets line up with the original text. Java string literals
        # cannot span newlines (text blocks above are the only multi-
        # line form), so a bare newline mid-quote is a malformed source
        # we let fall through.
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
    # changing length. Filter out matches whose `@` falls inside a
    # blanked range (string literal, comment, text block) — the
    # cleaner replaces those with whitespace, so cleaned[m.start()]
    # is a space rather than the original `@`.
    annot_props: list[tuple[int, str]] = [
        (m.start(), m.group(1))
        for m in _JSON_PROPERTY_RE.finditer(content)
        if m.start() < len(cleaned) and cleaned[m.start()] == "@"
    ]

    # Plain (unannotated) field declarations. Jackson defaults to
    # using the field name as the wire key when @JsonProperty is
    # absent. Without this discovery, the wire-shape gate
    # under-counts SDK fields wherever the SDK relies on Jackson's
    # default name-mapping (which is correct for fields whose Java
    # name already matches the wire — `id`, `threshold`, `message`,
    # etc.). Match on the cleaned text so commented-out or in-string
    # field-shaped patterns don't false-fire. Skip declarations
    # whose modifier sequence contains `static` — those are
    # class-level constants, never serialized.
    field_decls = [
        fm for fm in _FIELD_DECL_RE.finditer(cleaned)
        if "static" not in fm.group(1)
    ]

    # Build the merged property stream. Each entry is (position,
    # wire_name). For plain field decls, "absorb" any @JsonProperty
    # whose position falls within the look-back window — that pair
    # is annotated and the annotation's wire name takes precedence.
    consumed_annot_indices: set[int] = set()
    plain_props: list[tuple[int, str]] = []
    for fm in field_decls:
        field_pos = fm.start()
        field_name = fm.group(2)
        # Find the latest annotation that hasn't been consumed and is
        # within the lookback window.
        annotated = False
        for idx, (ap, _) in enumerate(annot_props):
            if idx in consumed_annot_indices:
                continue
            if ap >= field_pos:
                break
            if field_pos - ap <= _JSON_PROPERTY_LOOKBACK:
                consumed_annot_indices.add(idx)
                annotated = True
                # Don't break — multiple annotations can stack
                # (e.g. @JsonInclude + @JsonProperty); keep marking
                # them consumed so they don't double-attribute.
        if not annotated:
            plain_props.append((field_pos, field_name))

    # The full property stream is annotation-derived names + plain
    # field names, ordered by source position so the depth walker
    # attributes them in declaration order.
    props: list[tuple[int, str]] = sorted(annot_props + plain_props)

    # Walk the cleaned source. Two queues: decls already seen but
    # whose body `{` hasn't been hit yet (pending), and active type
    # frames (stack). Annotations attribute to the innermost pending
    # decl (record-parameter case) if any, otherwise the innermost
    # active frame. This keeps record(@JsonProperty(...) X x) {} from
    # silently dropping its annotations.
    pending_decls: list[str] = []
    stack: list[tuple[int, str]] = []  # (depth_at_open, type_name)
    depth = 0
    next_decl_idx = 0
    next_prop_idx = 0
    result: dict[str, list[str]] = {}

    i = 0
    n = len(cleaned)
    while i < n:
        # Promote any decls whose name token has now been passed.
        while next_decl_idx < len(decls) and decls[next_decl_idx][0] <= i:
            pending_decls.append(decls[next_decl_idx][1])
            next_decl_idx += 1

        # Consume any annotation at this position. Pending-decl wins
        # so record params attribute correctly.
        while next_prop_idx < len(props) and props[next_prop_idx][0] <= i:
            _, wire = props[next_prop_idx]
            owner = (
                pending_decls[-1] if pending_decls
                else stack[-1][1] if stack
                else None
            )
            if owner is not None:
                result.setdefault(owner, []).append(wire)
            next_prop_idx += 1

        c = cleaned[i]
        if c == "{":
            if pending_decls:
                # This brace opens the body of the oldest pending decl.
                name = pending_decls.pop(0)
                stack.append((depth, name))
            depth += 1
        elif c == "}":
            depth -= 1
            if stack and stack[-1][0] == depth:
                stack.pop()
        i += 1

    # Consume any trailing annotations (defensive; shouldn't happen).
    while next_prop_idx < len(props):
        _, wire = props[next_prop_idx]
        owner = (
            pending_decls[-1] if pending_decls
            else stack[-1][1] if stack
            else None
        )
        if owner is not None:
            result.setdefault(owner, []).append(wire)
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
    try:
        with BASELINE_PATH.open() as f:
            parsed = json.load(f)
    except json.JSONDecodeError as e:
        # Truncated mid-write, hand-edited and corrupted, or otherwise
        # not parseable. Fail loudly with a regeneration hint instead
        # of letting the validator bail with an opaque traceback.
        raise SystemExit(
            f"❌ tests/fixtures/wire-shape-baseline.json is malformed "
            f"({e.__class__.__name__}: {e}).\n"
            f"   Regenerate via:\n"
            f"     python3 scripts/wire_shape/refresh.py "
            f"<axonflow community specs dir>"
        ) from None
    base = empty_baseline()
    base.update(parsed)
    return base


def write_baseline(baseline: dict[str, Any]) -> None:
    BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = BASELINE_PATH.with_suffix(f".json.tmp.{os.getpid()}")
    try:
        with tmp.open("w") as f:
            json.dump(baseline, f, indent=2, sort_keys=True)
            f.write("\n")
        tmp.replace(BASELINE_PATH)
    except BaseException:
        # If anything fails between open() and replace(), don't leave
        # a stray .tmp.<pid> sidecar — the next run from the same PID
        # would collide and confuse a future writer.
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass
        raise


def difference(a: list[str], b: list[str]) -> list[str]:
    bs = set(b)
    return sorted(x for x in a if x not in bs)
