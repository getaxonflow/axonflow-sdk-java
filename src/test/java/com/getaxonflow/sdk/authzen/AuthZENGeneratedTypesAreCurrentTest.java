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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.getaxonflow.sdk.authzen.codegen.AuthZENCodegen;
import com.getaxonflow.sdk.authzen.codegen.Emitter;
import com.getaxonflow.sdk.authzen.codegen.Surface;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The committed {@code com.getaxonflow.sdk.authzen} wire types are what the vendored artifact
 * generates.
 *
 * <p>This is the whole reason the generated files may be committed at all. A committed generated
 * file that nothing checks is a hand-written file with a misleading header: it drifts from its
 * input on the first edit, and the header goes on claiming it did not.
 */
class AuthZENGeneratedTypesAreCurrentTest {

  private static Path sdkRoot() {
    // Surefire runs with the module directory as the working directory, and
    // basedir is set explicitly so a run from an IDE with a different working
    // directory behaves the same.
    String basedir = System.getProperty("basedir", System.getProperty("user.dir"));
    return Paths.get(basedir);
  }

  private static String committed(String fileName) throws IOException {
    Path path = sdkRoot().resolve(Emitter.OUTPUT_DIR).resolve(fileName);
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("every committed type is byte-for-byte what the vendored artifact generates")
  void committedTypesMatchTheArtifact() throws IOException {
    Map<String, String> rendered = AuthZENCodegen.render(sdkRoot());
    assertThat(rendered).isNotEmpty();
    for (Map.Entry<String, String> entry : rendered.entrySet()) {
      assertThat(committed(entry.getKey()))
          .as(
              "%s is not what %s generates. Regenerate them in the same change:%n  %s%n"
                  + "If you edited the generated file by hand, edit the emitter instead. If you "
                  + "edited the artifact, it is vendored from the platform's canonical contract "
                  + "and should be replaced wholesale, not patched.",
              entry.getKey(), Emitter.SURFACE_PATH, Emitter.REGENERATE_COMMAND)
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  @DisplayName("no generated file is left behind after the artifact stops describing it")
  void noStaleGeneratedFilesRemain() throws IOException {
    // A per-file byte comparison cannot see this: it only ever looks at files
    // the generator still produces. A type removed from the contract would go
    // on compiling, and go on being part of this SDK's public API.
    Map<String, String> rendered = AuthZENCodegen.render(sdkRoot());
    Set<String> committed = AuthZENCodegen.committedGeneratedFiles(sdkRoot());
    assertThat(committed)
        .as("a file carrying the generated marker that the artifact no longer describes")
        .containsExactlyInAnyOrderElementsOf(rendered.keySet());
  }

  @Test
  @DisplayName("regenerating repeatedly produces the same bytes")
  void generationIsDeterministic() throws IOException {
    // A leaked map ordering here would make the check above fail on pull
    // requests that touched none of this, and the usual response to a check
    // that fails at random is to delete it. Sixteen runs, because one
    // repetition proves nothing about an ordering stable within a process.
    Map<String, String> first = AuthZENCodegen.render(sdkRoot());
    for (int i = 1; i < 16; i++) {
      assertThat(AuthZENCodegen.render(sdkRoot()))
          .as("emission %d differed from the first", i)
          .isEqualTo(first);
    }
  }

  @Test
  @DisplayName("a planted field-shape drift makes the currency check fail")
  void aFieldShapeDriftIsDetected() throws IOException {
    // The SURVIVOR case. The check above has only ever been observed passing,
    // and a check that has never been seen to fail is a check nobody has
    // established is connected to its subject. This plants a drift of exactly
    // the shape a contract change would have — one member renamed — and asserts
    // the byte comparison notices.
    //
    // In memory: writing the drift to disk would leave the tree mutated if the
    // test were killed part-way.
    byte[] raw = Files.readAllBytes(sdkRoot().resolve(Emitter.SURFACE_PATH));
    Surface surface = Surface.parse(raw);
    Surface.TypeDecl subject =
        surface.types.stream()
            .filter(t -> "authzen_subject".equals(t.name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the artifact declares authzen_subject"));
    Surface.FieldDecl properties =
        subject.fields.stream()
            .filter(f -> "properties".equals(f.name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("authzen_subject declares properties"));
    properties.name = "attributes";

    Map<String, String> drifted = Emitter.emit(surface);
    String after = drifted.get("AuthZENSubject.java");

    assertNotEquals(
        committed("AuthZENSubject.java"),
        after,
        "a renamed member produced byte-identical output, so the currency check cannot see a "
            + "field-shape drift at all");
    assertThat(after)
        .as("the planted drift did not reach the emitted type, so this test proves nothing")
        .contains("private AttributeMap attributes");
  }

  @Test
  @DisplayName("the generated header names the contract the constants come from")
  void theHeaderNamesTheContract() throws IOException {
    // The generated header names a profile and a schema digest. Those are the
    // strings a support engineer compares against a server's response, so they
    // have to come from the artifact rather than from a constant somebody
    // updated by hand.
    byte[] raw = Files.readAllBytes(sdkRoot().resolve(Emitter.SURFACE_PATH));
    Surface surface = Surface.parse(raw);
    String contract = committed("AuthZENContract.java");

    assertThat(contract).contains(surface.profile);
    assertThat(contract).contains(surface.sourceSchemaSha256);
    assertThat(AuthZENContract.PROFILE_V1)
        .as("the constant the client negotiates with is not the artifact's profile")
        .isEqualTo(surface.profile);
    assertThat(AuthZENContract.SCHEMA_VERSION).isEqualTo(surface.contractSchemaVersion);
  }
}
