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
package com.getaxonflow.sdk.authzen;

import static org.assertj.core.api.Assertions.assertThat;

import com.getaxonflow.sdk.authzen.codegen.AuthZENCodegen;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The generated sources stay inside google-java-format's column limit.
 *
 * <h2>What this test does NOT establish</h2>
 *
 * <p>It is not a formatting check. google-java-format is not on the test classpath — it is a plugin
 * dependency, and its current release needs JDK 17 while this SDK still builds on 11 — so this
 * asserts the ONE rule that can be checked without it, and says so rather than implying coverage it
 * does not have.
 *
 * <p>The reason the rule matters: the emitter reproduces the formatter's layout so that the
 * committed bytes and the formatter agree. An over-long line is the failure mode that makes them
 * disagree, and it is the one that arrives silently — a doc string grew, a type name got longer,
 * and nothing complains until somebody runs the formatter and the currency check goes red for a
 * reason nobody changed.
 */
class AuthZENGeneratedTypesRespectTheColumnLimitTest {

  private static final int COLUMN_LIMIT = 100;

  @Test
  @DisplayName("no generated line exceeds the column limit")
  void everyGeneratedLineFits() throws IOException {
    Map<String, String> rendered =
        AuthZENCodegen.render(
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir"))));
    assertThat(rendered).isNotEmpty();

    List<String> tooLong = new ArrayList<>();
    for (Map.Entry<String, String> file : rendered.entrySet()) {
      String[] lines = file.getValue().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].length() > COLUMN_LIMIT) {
          tooLong.add(
              file.getKey() + ":" + (i + 1) + " is " + lines[i].length() + " columns: " + lines[i]);
        }
      }
    }
    assertThat(tooLong).isEmpty();
  }

  @Test
  @DisplayName("no generated file has trailing whitespace or a blank line before its closing brace")
  void everyGeneratedFileIsTidy() throws IOException {
    Map<String, String> rendered =
        AuthZENCodegen.render(
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir"))));

    List<String> problems = new ArrayList<>();
    for (Map.Entry<String, String> file : rendered.entrySet()) {
      String content = file.getValue();
      if (!content.endsWith("}\n")) {
        problems.add(file.getKey() + " does not end with a closing brace and one newline");
      }
      if (content.endsWith("\n\n}\n") || content.contains("\n\n}\n")) {
        problems.add(file.getKey() + " has a blank line before a closing brace");
      }
      String[] lines = content.split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        if (!lines[i].equals(lines[i].replaceAll("\\s+$", ""))) {
          problems.add(file.getKey() + ":" + (i + 1) + " has trailing whitespace");
        }
      }
    }
    assertThat(problems).isEmpty();
  }
}
