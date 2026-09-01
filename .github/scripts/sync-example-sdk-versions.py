#!/usr/bin/env python3
"""Pin every example's axonflow-sdk dependency to the version in the root pom.

WHY THIS EXISTS AS A SCRIPT RATHER THAN AN INLINE STEP.

The root pom declares no <modules>, so `mvn package` at the root never descends
into examples/. Each example is a standalone project whose SDK dependency names
a version coordinate, and left alone that coordinate resolves from Maven Central
- which means an example either tests a PUBLISHED build rather than this one, or
fails outright when the surface it demonstrates is not published yet.

There used to be one copy of this rewrite inlined in integration.yml, pointed at
examples/basic only, and a second consumer would have had to copy it. Two copies
of a regex over a pom is exactly the shape where a later fix lands on one path
and the other silently keeps resolving from Central.

WHAT IT DOES NOT DO. It does not install anything. Run

    mvn install -DskipTests

first: the rewrite makes the examples ASK for the local version, and only the
install puts that version in the local repository for them to find.

Usage:
    sync-example-sdk-versions.py <version> [pom ...]

With no pom arguments it rewrites every examples/*/pom.xml it finds, so an
example added tomorrow is covered without editing a workflow. Every named or
discovered pom MUST contain the dependency; a pom whose layout drifted past the
pattern is an error rather than a silent skip, because a skipped rewrite is
indistinguishable from a successful one in the log.
"""

import pathlib
import re
import sys

DEPENDENCY = re.compile(
    r"(<artifactId>axonflow-sdk</artifactId>\s*<version>)[^<]+(</version>)"
)


def main(argv):
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2

    version = argv[1]
    if not version or version.startswith("-"):
        print(f"error: {version!r} is not a version", file=sys.stderr)
        return 2

    if len(argv) > 2:
        poms = [pathlib.Path(a) for a in argv[2:]]
    else:
        poms = sorted(pathlib.Path("examples").glob("*/pom.xml"))
        if not poms:
            print(
                "error: no examples/*/pom.xml found. Run this from the repository "
                "root; an empty match is not a pass.",
                file=sys.stderr,
            )
            return 1

    failed = False
    for pom in poms:
        if not pom.is_file():
            print(f"error: {pom} does not exist", file=sys.stderr)
            failed = True
            continue
        before = pom.read_text()
        after, count = DEPENDENCY.subn(rf"\g<1>{version}\g<2>", before)
        if count < 1:
            print(
                f"error: {pom} declares no axonflow-sdk dependency in the expected "
                f"layout. Either the pom drifted or the example stopped depending "
                f"on the SDK; both need a human, not a skip.",
                file=sys.stderr,
            )
            failed = True
            continue
        if after != before:
            pom.write_text(after)
            print(f"{pom}: rewrote {count} SDK dependency version -> {version}")
        else:
            print(f"{pom}: already at {version} (no-op)")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
