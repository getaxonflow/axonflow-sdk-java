/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getaxonflow.sdk.authzen.codegen;

import java.util.ArrayList;
import java.util.List;

/**
 * The two google-java-format rules the emitter has to reproduce.
 *
 * <h2>Why the emitter matches the formatter instead of calling it</h2>
 *
 * <p>The generated sources are committed, and a test regenerates them in memory and compares bytes.
 * If the committed file were the FORMATTER's output while the emitter produced something the
 * formatter would rewrite, then the first person to run {@code mvn fmt:format} — or the first
 * google-java-format upgrade — would leave the repository in a state where the currency check is
 * red for a reason nobody changed.
 *
 * <p>Calling the formatter from the emitter would fix that by coupling the committed bytes to
 * whichever formatter version last ran, which trades one version-drift problem for another and adds
 * a dependency the published SDK does not need. google-java-format 1.28 also requires JDK 17, and
 * this SDK's CI still builds on 11.
 *
 * <p>So the emitter emits what the formatter would emit, and {@code
 * AuthZENGeneratedTypesRespectTheColumnLimitTest} is what keeps the two honest for the rule a
 * test can check without the formatter on the classpath: no generated line exceeds the column
 * limit. That is not the whole of google-java-format, and the test says so rather than implying
 * it is.
 */
final class Layout {

  /** google-java-format's column limit. */
  static final int MAX_WIDTH = 100;

  private Layout() {}

  /**
   * RULE 1 — a Javadoc block with one short paragraph and no tags is written on one line.
   *
   * <p>Otherwise: paragraphs filled greedily to the column limit, a bare {@code *} between them,
   * and the tag block last.
   *
   * @param indent the block's indentation
   * @param paragraphs the prose, one entry per paragraph, already carrying any {@code <p>} prefix
   * @param tags the {@code @param} / {@code @return} / {@code @throws} lines, in order
   * @return the rendered block, ending in a newline
   */
  static String javadoc(String indent, List<String> paragraphs, List<String> tags) {
    if (tags.isEmpty() && paragraphs.size() == 1) {
      String single = indent + "/** " + collapse(paragraphs.get(0)) + " */";
      if (single.length() <= MAX_WIDTH) {
        return single + "\n";
      }
    }
    StringBuilder b = new StringBuilder(indent).append("/**\n");
    int width = MAX_WIDTH - indent.length() - 3;
    for (int i = 0; i < paragraphs.size(); i++) {
      if (i > 0) {
        b.append(indent).append(" *\n");
      }
      for (String line : fill(paragraphs.get(i), width)) {
        b.append(indent).append(" * ").append(line).append("\n");
      }
    }
    if (!tags.isEmpty()) {
      if (!paragraphs.isEmpty()) {
        b.append(indent).append(" *\n");
      }
      for (String tag : tags) {
        List<String> lines = fill(tag, width);
        b.append(indent).append(" * ").append(lines.get(0)).append("\n");
        for (int i = 1; i < lines.size(); i++) {
          // A wrapped tag continues at a four-space hanging indent, which is
          // what distinguishes the continuation from a new tag.
          b.append(indent).append(" *     ").append(lines.get(i)).append("\n");
        }
      }
    }
    return b.append(indent).append(" */\n").toString();
  }

  /**
   * RULE 2 — a call or a declaration head is written on one line when it fits; otherwise its
   * arguments move to ONE continuation line indented four further; otherwise one argument per line.
   *
   * @param indent the statement's indentation
   * @param head everything up to and including the open parenthesis
   * @param args the arguments, already rendered
   * @param tail everything from the close parenthesis on
   * @return the rendered call, ending in a newline
   */
  static String call(String indent, String head, List<String> args, String tail) {
    String joined = String.join(", ", args);
    String oneLine = indent + head + joined + tail;
    if (oneLine.length() <= MAX_WIDTH) {
      return oneLine + "\n";
    }
    String continued = indent + "    " + joined + tail;
    if (continued.length() <= MAX_WIDTH) {
      return indent + head + "\n" + continued + "\n";
    }
    StringBuilder b = new StringBuilder(indent).append(head).append("\n");
    for (int i = 0; i < args.size(); i++) {
      b.append(indent).append("    ").append(args.get(i));
      b.append(i == args.size() - 1 ? tail : ",").append("\n");
    }
    return b.toString();
  }

  /** Collapses runs of whitespace, so a paragraph written across source lines fills correctly. */
  static String collapse(String s) {
    return s.trim().replaceAll("\\s+", " ");
  }

  /** Fills text greedily to {@code width}, never breaking a word. */
  static List<String> fill(String text, int width) {
    String[] words = collapse(text).split(" ");
    List<String> out = new ArrayList<>();
    if (words.length == 0 || words[0].isEmpty()) {
      out.add("");
      return out;
    }
    StringBuilder cur = new StringBuilder(words[0]);
    for (int i = 1; i < words.length; i++) {
      if (cur.length() + 1 + words[i].length() > width) {
        out.add(cur.toString());
        cur = new StringBuilder(words[i]);
        continue;
      }
      cur.append(' ').append(words[i]);
    }
    out.add(cur.toString());
    return out;
  }
}
