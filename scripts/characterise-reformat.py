#!/usr/bin/env python3
"""Characterise a mechanical reformat: which files changed CODE, and which only comments.

WHY THIS EXISTS, AND WHY `git diff -w` IS NOT IT.

A 39-file formatter commit is unreviewable by eye, so the reviewer needs a
measurement rather than an assurance. The obvious one is wrong: `git diff -w`
ignores whitespace *within* a line, but google-java-format **rewraps**, moving
tokens between lines. Run against this reformat it reports 30 of 39 files as
substantive, which is a false alarm that would send a reviewer hunting through
noise. I ran it first, wrote "whitespace-only" into the same command as the
check, and had to throw the conclusion away.

What this does instead, per file, against a base ref:

  1. Strip comments (block and line), respecting string and char literals so a
     `//` inside a string is not mistaken for a comment.
  2. Collapse all whitespace.
  3. Compare imports as a SET, separately from the body, because the formatter
     sorts imports and a reordering is semantically inert in Java.

A file is reported as CODE-CHANGED only if its comment-free, whitespace-free
body differs, or its import SET differs. Anything else is reflow.

This does not prove semantic equivalence — nothing short of comparing bytecode
does, and the suite is the real evidence. It bounds where a reviewer must look.

Usage:  ./scripts/characterise-reformat.py [base-ref]     # default origin/main
Exit 1 if any file's body changed, so it can gate as well as report.
"""

import re
import subprocess
import sys


def strip_comments(text: str) -> str:
    """Remove comments; leave string and char literals intact."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c in '"\'':
            quote = c
            out.append(c)
            i += 1
            while i < n:
                if text[i] == "\\":          # escape: take both characters
                    out.append(text[i : i + 2])
                    i += 2
                    continue
                out.append(text[i])
                i += 1
                if text[i - 1] == quote:
                    break
            continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


IMPORT = re.compile(r"^\s*import\s+[^;]+;", re.M)


def split(text: str):
    """(sorted import set, comment-free whitespace-free body)."""
    imports = {re.sub(r"\s+", " ", m.strip()) for m in IMPORT.findall(text)}
    body = IMPORT.sub("", text)
    return imports, re.sub(r"\s+", "", strip_comments(body))


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
    files = subprocess.run(
        ["git", "diff", "--name-only", base, "--", "*.java"],
        capture_output=True, text=True, check=True,
    ).stdout.split()

    if not files:
        print(f"no .java files differ from {base}")
        return 0

    reflow, import_only, changed = [], [], []
    for f in files:
        old = subprocess.run(["git", "show", f"{base}:{f}"],
                             capture_output=True, text=True).stdout
        with open(f, encoding="utf-8") as fh:
            new = fh.read()
        oi, ob = split(old)
        ni, nb = split(new)
        if ob != nb:
            changed.append(f)
        elif oi != ni:
            added, removed = sorted(ni - oi), sorted(oi - ni)
            import_only.append((f, added, removed))
        else:
            reflow.append(f)

    print(f"=== {len(files)} .java files differ from {base}")
    print(f"  comments/whitespace only ...... {len(reflow)}")
    print(f"  imports changed, body same .... {len(import_only)}")
    for f, added, removed in import_only:
        print(f"      {f}")
        if added:
            print(f"        added:   {added}")
        if removed:
            print(f"        removed: {removed}")
    print(f"  BODY CHANGED .................. {len(changed)}")
    for f in changed:
        print(f"      {f}")

    if changed:
        print("\nFAIL: a mechanical reformat must not change any body", file=sys.stderr)
        return 1
    print("\nPASS: no file's code body changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
