// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every guard script in {@code scripts/} is invoked by a workflow step that will run.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code scripts/verify-formatting-gate.sh} was written to pin the property issue #220 was filed
 * for -- that the {@code lint} job's formatting step FAILS on a violation rather than printing
 * "skipped" under a green tick. It was thorough, and <b>nothing ran it</b>: a repository-wide
 * search found one file containing its name, itself. The property was pinned on paper and unpinned
 * in practice, which is the same failure the script was written to prevent, one level up.
 *
 * <h2>Two things the first version of this class got wrong</h2>
 *
 * <p><b>It grepped raw bytes while its own comment claimed it checked {@code run:} lines.</b> A
 * guard narrower than its own comment is worse than a narrow guard. R3 killed it with the two
 * mutants a real regression actually looks like: commenting the step out, and deleting the step
 * while leaving {@code # TODO: someday wire scripts/verify-formatting-gate.sh into this workflow}.
 * Both passed. A TODO satisfied the guard. Comment lines are now stripped and only the content of
 * {@code run:} scalars is searched.
 *
 * <p><b>Its list was the two scripts that prompted the issue.</b> {@code scripts/mutation-gate.sh}
 * describes itself as a gate in its first line, had zero workflow references, and was not in the
 * list -- a census bounded by its own trigger, which is the defect class this whole lane exists to
 * fix, committed inside the fix for it. The list is now <b>derived</b>: every {@code scripts/*.sh}
 * must be wired unless it carries an explicit opt-out marker naming a reason. A script added
 * tomorrow is covered without anyone remembering this file.
 *
 * <h2>Scope, stated rather than implied</h2>
 *
 * <p>This asserts <b>wiring</b>, not correctness: that the script's name appears in the content of
 * a {@code run:} scalar. Whether the script's own assertions are any good is that script's
 * business. It does not evaluate {@code if:} conditions, so a step guarded by an always-false
 * condition would still count as wired -- a real limitation, named here because the previous
 * version's unnamed limitation is what R3 found.
 */
class GuardsAreWiredTest {

  /**
   * A script declares itself exempt by carrying this marker, with a reason after it. Opt-OUT rather
   * than opt-in: the default is that a script in {@code scripts/} runs in CI, so a new guard is
   * covered automatically and an exception has to be argued in the file where the author is.
   */
  private static final String EXEMPT_MARKER = "ci-wiring-exempt:";

  private static Path repoRoot() {
    return Paths.get(System.getProperty("basedir", System.getProperty("user.dir")));
  }

  /**
   * The concatenated content of every {@code run:} scalar across every workflow.
   *
   * <p>Deliberately not the raw file. A name inside a comment, a {@code name:}, or a commented-out
   * step is not an invocation, and treating it as one is how the previous version passed both of
   * R3's mutants. Full-line comments are dropped first; then each {@code run:} contributes either
   * its inline value or its indented block scalar.
   */
  static String runScalars(String workflowYaml) {
    StringBuilder out = new StringBuilder();
    String[] lines = workflowYaml.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.trim().startsWith("#")) {
        continue;
      }
      int at = line.indexOf("run:");
      if (at < 0 || !line.substring(0, at).trim().replace("-", "").isEmpty()) {
        continue;
      }
      int indent = line.indexOf('-') >= 0 ? line.indexOf('-') : at;
      out.append(line.substring(at + 4)).append('\n');
      // A block scalar continues while lines are blank or indented past the key.
      for (int j = i + 1; j < lines.length; j++) {
        String next = lines[j];
        if (next.trim().isEmpty()) {
          continue;
        }
        int lead = next.length() - next.stripLeading().length();
        if (lead <= indent) {
          break;
        }
        if (!next.trim().startsWith("#")) {
          out.append(next).append('\n');
        }
      }
    }
    return out.toString();
  }

  private static String allRunScalars() throws IOException {
    StringBuilder all = new StringBuilder();
    try (Stream<Path> files = Files.walk(repoRoot().resolve(".github/workflows"))) {
      List<Path> yml = new ArrayList<>();
      files.filter(Files::isRegularFile).forEach(yml::add);
      for (Path p : yml) {
        all.append(runScalars(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)));
      }
    }
    return all.toString();
  }

  /** Every shell script under {@code scripts/}, as repo-relative names. */
  private static List<Path> scripts() throws IOException {
    try (Stream<Path> files = Files.walk(repoRoot().resolve("scripts"))) {
      List<Path> out = new ArrayList<>();
      files
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".sh"))
          .forEach(out::add);
      out.sort(Path::compareTo);
      return out;
    }
  }

  @Test
  @DisplayName("every script is wired into a workflow, or says why it is not")
  void everyScriptIsWiredOrExempt() throws IOException {
    String runs = allRunScalars();
    List<String> unwired = new ArrayList<>();
    int considered = 0;
    for (Path script : scripts()) {
      String name = script.getFileName().toString();
      String body = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
      if (body.contains(EXEMPT_MARKER)) {
        continue;
      }
      considered++;
      if (!runs.contains(name)) {
        unwired.add(name);
      }
    }
    assertThat(unwired)
        .as(
            "these scripts are not run by any workflow and carry no '%s <reason>' marker, so "
                + "whatever each one pins is unpinned in practice",
            EXEMPT_MARKER)
        .isEmpty();
    // Derived from the enumeration, not a tuned number: if every script were
    // exempt, or scripts/ were empty, the assertion above would be vacuous.
    assertThat(considered)
        .as("no script was considered at all, so the assertion above proved nothing")
        .isPositive();
  }

  @Test
  @DisplayName("a name in a comment, or a commented-out step, is not an invocation")
  void aCommentIsNotAnInvocation() {
    // The two mutants R3 used to kill the previous version. Both are what a real
    // regression looks like, rather than what a test author imagines one to be.
    String todoOnly =
        "# TODO: someday wire scripts/verify-formatting-gate.sh into this workflow\n"
            + "jobs:\n  lint:\n    steps:\n      - run: echo hello\n";
    assertThat(runScalars(todoOnly))
        .as("a TODO mentioning a script satisfied the previous version of this guard")
        .doesNotContain("verify-formatting-gate.sh");

    String commentedOut =
        "jobs:\n  lint:\n    steps:\n"
            + "      # - name: Verify the formatting gate can fail\n"
            + "      #   run: ./scripts/verify-formatting-gate.sh\n"
            + "      - run: echo hello\n";
    assertThat(runScalars(commentedOut))
        .as("a commented-out step is not an invocation")
        .doesNotContain("verify-formatting-gate.sh");

    // ...and the control, so the exclusions above cannot be satisfied by a parser
    // that returns nothing at all.
    String real =
        "jobs:\n  lint:\n    steps:\n"
            + "      - name: Verify\n        run: ./scripts/verify-formatting-gate.sh\n";
    assertThat(runScalars(real))
        .as("the parser does not see a real invocation, so the exclusions prove nothing")
        .contains("verify-formatting-gate.sh");

    // A block scalar is the shape most steps in this repo actually use.
    String block =
        "jobs:\n  lint:\n    steps:\n"
            + "      - name: Verify\n        run: |\n          set -e\n"
            + "          ./scripts/verify-formatting-gate.sh\n";
    assertThat(runScalars(block)).contains("verify-formatting-gate.sh");
  }

  @Test
  @DisplayName("an exempt script must give a reason")
  void anExemptScriptMustGiveAReason() throws IOException {
    // The marker is an escape hatch, and an escape hatch with no argument in it
    // is just a way to turn the guard off one file at a time.
    List<String> bare = new ArrayList<>();
    for (Path script : scripts()) {
      String body = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
      int at = body.indexOf(EXEMPT_MARKER);
      if (at < 0) {
        continue;
      }
      String reason = body.substring(at + EXEMPT_MARKER.length()).split("\n", 2)[0].trim();
      if (reason.length() < 20) {
        bare.add(script.getFileName() + " -> " + (reason.isEmpty() ? "(none)" : reason));
      }
    }
    assertThat(bare).as("'%s' with no substantive reason after it", EXEMPT_MARKER).isEmpty();
  }
}
