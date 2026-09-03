// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The repository distributes under exactly one licence, and says so in exactly one way.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>This SDK spent its whole life declaring Apache-2.0 while every other AxonFlow interface
 * repository declared MIT, and nobody noticed until someone read all twenty-two repositories'
 * {@code licenseInfo} side by side. Drift of that kind is invisible from inside the repository that
 * has it: nothing fails, nothing warns, and the wrong string is simply copied forward by the next
 * file created from a template.
 *
 * <p>Relicensing without this test would leave the tree in exactly the state that produced the
 * problem. Three separate leaks were found while doing it, and only one of them was the Apache
 * header the work was commissioned for:
 *
 * <ul>
 *   <li>247 source files carrying an Apache-2.0 header, in six different shapes;
 *   <li>four files carrying the platform's own source-available licence, copied in from the
 *       platform repository -- one of them in {@code src/main}, i.e. inside the published jar;
 *   <li>the POM's {@code <licenses>} block, which is the line Maven Central actually reads and the
 *       one a {@code mvn dependency:list --licenses} downstream sees.
 * </ul>
 *
 * <h2>What is asserted, and how wide each rule really is</h2>
 *
 * <p>The SPDX rule is the strong one, because it is closed under the syntax rather than over a list
 * of phrasings: every SPDX identifier tag anywhere in the tree, IN ANY CASE, must name {@code MIT},
 * whatever licence a future copy-paste brings with it. The prose rule is a backstop and is only as
 * wide as {@link #FORBIDDEN_PHRASES} -- an enumerated list, therefore incomplete by construction,
 * which is why it is not the rule this test leans on.
 *
 * <p>The needles are assembled by concatenation so that this file's own list is not a hit for the
 * scan it drives. A guard whose marker string collides with the prose beside it either fails
 * against itself or has to exempt itself, and an exemption is a hole. That is not hypothetical
 * here: the first version of this class spelled both needles out in this very comment, and the
 * guard's first run failed against its own documentation.
 *
 * <p>Absence of a declaration is deliberately NOT an error. A file with no licence header inside a
 * repository with one LICENSE is unambiguous; a file declaring a DIFFERENT licence is the defect.
 * That exemption is wider than it may look, so it is stated in full rather than summarised: <b>46
 * of the repository's {@code .java} files carry no SPDX identifier at all</b> -- 20 generated
 * AuthZEN types (which carry a provenance header by design), 16 {@code runtime-e2e} harnesses, one
 * under {@code tests/}, and nine hand-written sources. <b>28 of the 46 are under {@code
 * src/main}</b>, so they ship in the sources jar with no licence statement of their own. Requiring
 * a declaration to be PRESENT is a different and stronger property than requiring no declaration to
 * CONTRADICT, and only the latter is what protects the licence; closing the former is its own
 * change.
 */
class LicenseMetadataTest {

  /** The one licence this repository distributes under. */
  private static final String LICENSE_NAME = "MIT License";

  private static final String LICENSE_URL = "https://opensource.org/licenses/MIT";

  private static final String SPDX_TAG = "SPDX" + "-License-Identifier:";

  /**
   * Comment terminators that can follow an identifier on the same line. An identifier is read from
   * a line of SOURCE, and a block or markup comment closes after it -- {@code /*}&nbsp;{@code ...:
   * MIT}&nbsp;{@code *}{@code /} or {@code <!-- ...: MIT -->}. Comparing the raw remainder of the
   * line would report a correctly-MIT file as a contradiction: a false positive, in the direction
   * that gets a guard deleted rather than fixed.
   */
  private static final List<String> COMMENT_TERMINATORS = Arrays.asList("*/", "-->", "#>", "--%>");

  /**
   * Names of path SEGMENTS that are build output or VCS metadata rather than source. Matched
   * segment-wise, not as a path prefix: there are five nested Maven modules under {@code
   * examples/}, so a prefix test on {@code target/} reads {@code examples/basic/target/**} as if it
   * were source. That is defensive rather than observed — the 16 files it currently excludes trip
   * no rule — but the exposure grows with whatever a future example depends on, and the developer
   * who hits it would be running {@code mvn test} at the root after {@code mvn package} in an
   * example, which is not an exotic thing to do.
   */
  private static final List<String> NOT_SOURCE = Arrays.asList(".git", "target");

  /**
   * Licence prose this repository must not be distributing under. Assembled piecewise; see the
   * class comment. Enumerated, hence a backstop rather than the primary rule.
   */
  private static final List<String> FORBIDDEN_PHRASES =
      Arrays.asList(
          "Apache" + " License, Version 2.0",
          // The same licence without the "Version", which is how prose usually names it
          // and which the comma-bearing form does not contain as a substring.
          "Apache" + " License 2.0",
          "Business" + " Source License",
          "GNU" + " General Public License",
          "Mozilla" + " Public License");

  /**
   * Files that must appear in the scan. These are not a count -- a floor is a number someone tunes
   * until it passes. Each anchor is a fact about the repository that pins one root the walk claims
   * to cover, so a walk that silently stopped short of {@code examples/} or {@code scripts/} fails
   * here rather than passing over an empty set.
   */
  private static final List<String> ANCHORS =
      Arrays.asList(
          "pom.xml",
          "LICENSE",
          "README.md",
          "CHANGELOG.md",
          "src/main/java/com/getaxonflow/sdk/AxonFlow.java",
          "src/test/java/com/getaxonflow/sdk/AxonFlowTest.java",
          "examples/basic/src/main/java/com/getaxonflow/examples/Basic.java",
          "scripts/wire_shape/AuditWireKeysProbe.java",
          "runtime-e2e/read_path_identity/ReadPathIdentityTest.java");

  private static Path repoRoot() {
    return Paths.get(System.getProperty("basedir", System.getProperty("user.dir")));
  }

  /** Every scannable file, keyed by its repository-relative path. */
  private static Map<String, String> tree() throws IOException {
    Path root = repoRoot();
    Map<String, String> out = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(root)) {
      List<Path> files = new ArrayList<>();
      walk.filter(Files::isRegularFile).forEach(files::add);
      files.sort(Path::compareTo);
      for (Path p : files) {
        Path rel = root.relativize(p);
        if (isNotSource(rel)) {
          continue;
        }
        // ISO-8859-1 never throws on arbitrary bytes, so a file this test was not
        // expecting cannot turn a licence assertion into a decoding error.
        out.put(
            rel.toString().replace(File.separatorChar, '/'),
            new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1));
      }
    }
    return out;
  }

  private static boolean isNotSource(Path rel) {
    for (Path segment : rel) {
      if (NOT_SOURCE.contains(segment.toString())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The Maven Wrapper is Apache-2.0 and stays Apache-2.0; vendoring it does not relicense it, and
   * rewriting its header would be a misstatement about someone else's code. Matched exactly rather
   * than by prefix, so a future top-level path merely BEGINNING {@code mvnw} is not exempted by
   * accident.
   */
  private static boolean isThirdParty(String rel) {
    return rel.equals("mvnw") || rel.equals("mvnw.cmd") || rel.startsWith(".mvn/");
  }

  /**
   * EVERY SPDX identifier declared on a line, in order; empty if the line declares none.
   *
   * <p>Every occurrence, not the first. Reading only the first is how the first version of this
   * method turned a false POSITIVE into a false NEGATIVE, which is the worse direction and the one
   * that gets shipped: a line reading {@code <!-- ...: MIT --> <!-- ...: Apache-2.0 -->} was
   * truncated at the first terminator, reported as plain {@code MIT}, and the Apache declaration
   * beside it passed the guard in silence. Before that "fix" the same line was caught, for the
   * accidental reason that its raw remainder did not equal {@code MIT}.
   *
   * <p>Each value runs to whichever comes first: a comment terminator, the next tag, or the end of
   * the line. A value that is not a well-formed SPDX expression is returned AS IS rather than
   * cleaned up, so it fails the MIT comparison and is reported with its raw text — an unparseable
   * declaration is a thing to look at, never a thing to quietly accept.
   *
   * @see #COMMENT_TERMINATORS
   */
  static List<String> declaredIdentifiers(String line) {
    List<String> found = new ArrayList<>();
    int from = 0;
    while (true) {
      int at = indexOfTagIgnoringCase(line, from);
      if (at < 0) {
        return found;
      }
      int valueStart = at + SPDX_TAG.length();
      int end = line.length();
      for (String terminator : COMMENT_TERMINATORS) {
        int candidate = line.indexOf(terminator, valueStart);
        if (candidate >= 0 && candidate < end) {
          end = candidate;
        }
      }
      int nextTag = indexOfTagIgnoringCase(line, valueStart);
      if (nextTag >= 0 && nextTag < end) {
        end = nextTag;
      }
      found.add(line.substring(valueStart, end).trim());
      from = valueStart;
    }
  }

  /**
   * The next occurrence of the tag at or after {@code from}, ignoring case.
   *
   * <p>Case-insensitive because the class comment claims this rule is closed under the SYNTAX, and
   * a case-sensitive {@code indexOf} makes that claim false: a hand-written header spelling the tag
   * in lower case walks straight past a guard whose own documentation says nothing gets past it. A
   * guard narrower than its own comment is worse than a narrow guard, because the comment is what
   * the next person relies on — and this rule is about to be copied into four other repositories.
   *
   * <p>The tag is deliberately NOT spelled out in any case in this file. Making the scan
   * case-insensitive turned every lowercase mention of it into a hit, and the first run after the
   * change failed on this very paragraph — the third time this class has caught its own
   * documentation, and a free positive control that the fix does what it claims.
   *
   * <p>Implemented with {@link String#regionMatches} rather than by lowercasing the line, because
   * {@code toLowerCase} is not length-preserving for every input and the returned index is used to
   * slice the ORIGINAL string.
   */
  private static int indexOfTagIgnoringCase(String line, int from) {
    for (int i = Math.max(0, from); i + SPDX_TAG.length() <= line.length(); i++) {
      if (line.regionMatches(true, i, SPDX_TAG, 0, SPDX_TAG.length())) {
        return i;
      }
    }
    return -1;
  }

  @Test
  @DisplayName("the scan reaches every root it claims to cover")
  void theScanReachesEveryRoot() throws IOException {
    Map<String, String> tree = tree();
    assertThat(tree.keySet())
        .as("the walk missed a root, which would make every rule below vacuous over it")
        .containsAll(ANCHORS);
  }

  @Test
  @DisplayName("LICENSE is the MIT text")
  void licenseFileIsMit() throws IOException {
    String license =
        new String(Files.readAllBytes(repoRoot().resolve("LICENSE")), StandardCharsets.UTF_8);
    // `.gitattributes` forces LF for two paths only, so LICENSE arrives CRLF on a
    // Windows checkout. Comparing the raw first line there fails with the message
    // `expected "MIT License" but was "MIT License"` -- the \r is invisible and the
    // assertion appears to deny itself. Strip it: the line ending is not the subject.
    assertThat(license.split("\n", -1)[0].replace("\r", "")).isEqualTo(LICENSE_NAME);
    assertThat(license)
        .as("the MIT permission grant, not just a file that starts with the right words")
        .contains("Permission is hereby granted, free of charge");
  }

  @Test
  @DisplayName("the POM declares MIT, which is what Maven Central publishes")
  void pomDeclaresMit() throws Exception {
    // GitHub reads LICENSE; Maven Central reads this block, and it is the one that
    // reaches every downstream `mvn dependency:list --licenses`. They can disagree.
    Document doc =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(repoRoot().resolve("pom.xml").toFile());
    NodeList licenses = doc.getElementsByTagName("license");
    assertThat(licenses.getLength()).as("exactly one <license> in the POM").isEqualTo(1);
    Element license = (Element) licenses.item(0);
    assertThat(license.getElementsByTagName("name").item(0).getTextContent().trim())
        .isEqualTo(LICENSE_NAME);
    assertThat(license.getElementsByTagName("url").item(0).getTextContent().trim())
        .isEqualTo(LICENSE_URL);
  }

  @Test
  @DisplayName("every SPDX identifier in the tree names MIT")
  void everySpdxIdentifierNamesMit() throws IOException {
    List<String> wrong = new ArrayList<>();
    int seen = 0;
    for (Map.Entry<String, String> file : tree().entrySet()) {
      if (isThirdParty(file.getKey())) {
        continue;
      }
      for (String line : file.getValue().split("\n", -1)) {
        for (String declared : declaredIdentifiers(line)) {
          seen++;
          if (!"MIT".equals(declared)) {
            wrong.add(file.getKey() + ": " + declared);
          }
        }
      }
    }
    assertThat(wrong)
        .as("a licence identifier that contradicts this repository's LICENSE")
        .isEmpty();
    // Without this, a walk that read nothing would satisfy the assertion above.
    assertThat(seen)
        .as("no SPDX identifier was read at all, so the rule above proved nothing")
        .isPositive();
  }

  @Test
  @DisplayName("no file carries the prose of another licence")
  void noForeignLicenceProse() throws IOException {
    List<String> hits = new ArrayList<>();
    for (Map.Entry<String, String> file : tree().entrySet()) {
      if (isThirdParty(file.getKey()) || "LICENSE".equals(file.getKey())) {
        continue;
      }
      for (String phrase : FORBIDDEN_PHRASES) {
        if (file.getValue().contains(phrase)) {
          hits.add(file.getKey() + ": " + phrase);
        }
      }
    }
    assertThat(hits).as("licence prose that contradicts this repository's LICENSE").isEmpty();
  }

  @Test
  @DisplayName("the identifier reader survives every comment syntax, in BOTH directions")
  void theIdentifierReaderHandlesEveryCommentSyntax() {
    // A recogniser has two failure directions and needs a case for each. Rows are built
    // from SPDX_TAG rather than written out, so this test's own cases are not hits for
    // the tree scan it describes.
    //
    // ACCEPTS: MIT however the surrounding comment closes. Reading the raw remainder of
    // the line would make `/* ...: MIT */` parse as "MIT */" and report a correctly
    // licensed file as a contradiction -- a false positive whose message denies what
    // the file plainly says.
    assertThat(declaredIdentifiers("// " + SPDX_TAG + " MIT")).containsExactly("MIT");
    assertThat(declaredIdentifiers(" * " + SPDX_TAG + " MIT")).containsExactly("MIT");
    assertThat(declaredIdentifiers("/* " + SPDX_TAG + " MIT */")).containsExactly("MIT");
    assertThat(declaredIdentifiers("<!-- " + SPDX_TAG + " MIT -->")).containsExactly("MIT");
    assertThat(declaredIdentifiers("# " + SPDX_TAG + " MIT")).containsExactly("MIT");
    // Every terminator in COMMENT_TERMINATORS is exercised, so dropping one from the
    // list fails here rather than silently narrowing what the reader understands.
    assertThat(declaredIdentifiers("<%-- " + SPDX_TAG + " MIT --%>")).containsExactly("MIT");
    assertThat(declaredIdentifiers("<# " + SPDX_TAG + " MIT #>")).containsExactly("MIT");
    // CASE. The class comment claims this rule is closed under the syntax; a
    // case-sensitive scan makes that false, and a hand-written lowercase header
    // then walks past a guard documented as letting nothing through.
    assertThat(declaredIdentifiers("// " + SPDX_TAG.toLowerCase(Locale.ROOT) + " Apache-2.0"))
        .containsExactly("Apache-2.0");
    assertThat(declaredIdentifiers("// " + SPDX_TAG.toUpperCase(Locale.ROOT) + " BUSL-1.1"))
        .containsExactly("BUSL-1.1");
    assertThat(declaredIdentifiers("// " + SPDX_TAG.toLowerCase(Locale.ROOT) + " MIT"))
        .containsExactly("MIT");

    // STILL CATCHES: a foreign identifier is not laundered by the same handling.
    assertThat(declaredIdentifiers("/* " + SPDX_TAG + " Apache-2.0 */"))
        .containsExactly("Apache-2.0");
    assertThat(declaredIdentifiers("<!-- " + SPDX_TAG + " BUSL-1.1 -->"))
        .containsExactly("BUSL-1.1");
    assertThat(declaredIdentifiers("// " + SPDX_TAG + " MIT OR GPL-3.0"))
        .containsExactly("MIT OR GPL-3.0");

    // THE FALSE-NEGATIVE DIRECTION, which is the one that ships. A foreign declaration
    // sharing a line with a compliant one must not be swallowed by the first value:
    // truncating at the first terminator reported this whole line as plain "MIT" and
    // let the Apache declaration beside it pass in silence.
    assertThat(
            declaredIdentifiers(
                "<!-- " + SPDX_TAG + " MIT --> <!-- " + SPDX_TAG + " Apache-2.0 -->"))
        .containsExactly("MIT", "Apache-2.0");
    assertThat(declaredIdentifiers("/* " + SPDX_TAG + " MIT */ /* " + SPDX_TAG + " BUSL-1.1 */"))
        .containsExactly("MIT", "BUSL-1.1");
    // ...including when the two tags abut with no terminator between them at all.
    assertThat(declaredIdentifiers(SPDX_TAG + " MIT " + SPDX_TAG + " Apache-2.0"))
        .containsExactly("MIT", "Apache-2.0");

    // A line that declares nothing yields nothing, so `seen` counts only real ones.
    assertThat(declaredIdentifiers("import java.util.List;")).isEmpty();
  }

  @Test
  @DisplayName("the third-party exemption is exactly the vendored wrapper, and nothing else")
  void theThirdPartyExemptionIsExact() {
    // This exemption is load-bearing: mvnw and mvnw.cmd both carry an Apache-2.0
    // header, so the prose rule fires on them unless they are excluded. That makes
    // an over-broad exemption dangerous in the quiet direction -- it would excuse
    // real files -- and nothing pinned it until round 2 mutated `equals` back to
    // `startsWith("mvnw")` and the whole suite stayed green.
    assertThat(isThirdParty("mvnw")).isTrue();
    assertThat(isThirdParty("mvnw.cmd")).isTrue();
    assertThat(isThirdParty(".mvn/wrapper/maven-wrapper.properties")).isTrue();
    // A path that merely BEGINS with the wrapper's name is our own file, not theirs.
    assertThat(isThirdParty("mvnw-notes.md")).isFalse();
    assertThat(isThirdParty("mvnwrapper/Custom.java")).isFalse();
    assertThat(isThirdParty(".mvnrc")).isFalse();
    assertThat(isThirdParty("src/main/java/com/getaxonflow/sdk/AxonFlow.java")).isFalse();
  }

  @Test
  @DisplayName("build output under a nested module is not scanned")
  void nestedBuildOutputIsNotScanned() {
    // There are five nested Maven modules under examples/. A prefix test on
    // "target/" excludes only the root one, so a developer who ran `mvn package`
    // in an example and then `mvn test` at the root would have the licence gate
    // assert against shaded jars and unpacked dependencies.
    assertThat(isNotSource(Paths.get("examples/basic/target/classes/X.class"))).isTrue();
    assertThat(isNotSource(Paths.get("target/classes/X.class"))).isTrue();
    assertThat(isNotSource(Paths.get(".git/config"))).isTrue();
    // ...while a real source path that merely CONTAINS the word is still scanned.
    assertThat(isNotSource(Paths.get("src/main/java/com/getaxonflow/sdk/targeting/X.java")))
        .isFalse();
    assertThat(isNotSource(Paths.get("src/main/java/com/getaxonflow/sdk/AxonFlow.java"))).isFalse();
  }

  @Test
  @DisplayName("the phrase rule can actually fire")
  void thePhraseRuleCanFire() {
    // The rule above asserts an ABSENCE across a tree that is currently clean, so on
    // its own it would pass identically if `contains` never matched anything. This
    // runs the same predicate over a string that does contain the prose.
    String planted = "/*\n * Licensed under the " + "Apache" + " License, Version 2.0\n */";
    assertThat(FORBIDDEN_PHRASES.stream().anyMatch(planted::contains))
        .as("the forbidden-phrase predicate does not match text that plainly contains a phrase")
        .isTrue();
  }
}
