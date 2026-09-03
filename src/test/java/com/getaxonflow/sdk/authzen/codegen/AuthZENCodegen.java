// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Writes the SDK's AuthZEN wire types from the platform's canonical surface artifact.
 *
 * <pre>
 *   ./scripts/gen-authzen-types.sh            # write the sources
 *   ./scripts/gen-authzen-types.sh --check    # fail if they are out of date
 * </pre>
 *
 * <h2>The generated files are committed</h2>
 *
 * <p>A consumer pulling the published jar must receive working types without running a generator,
 * so the output is committed. A committed generated file is only worth anything if something proves
 * it is the output of the current input, which {@code AuthZENGeneratedTypesAreCurrentTest} does: it
 * regenerates in memory and compares bytes, so editing either the artifact or a generated file
 * without the other fails CI.
 */
public final class AuthZENCodegen {

  private AuthZENCodegen() {}

  /**
   * Reads the artifact under {@code root} and renders every file it describes.
   *
   * <p>One method, shared by the command line and by the SDK's own currency test, so the two cannot
   * drift into slightly different pipelines - which is the failure that would let {@code --check}
   * pass while a committed file was stale.
   *
   * @param root the SDK root directory
   * @return file name to file content
   * @throws IOException if the artifact cannot be read
   */
  public static Map<String, String> render(Path root) throws IOException {
    byte[] raw = Files.readAllBytes(root.resolve(Emitter.SURFACE_PATH));
    return Emitter.emit(Surface.parse(raw));
  }

  /**
   * Every file in the output package that carries the generated marker.
   *
   * <p>Compared against {@link #render}'s key set, this is what catches a generated file that was
   * DELETED, or one left behind after a type was removed from the contract. A per-file byte
   * comparison alone cannot see either: it only ever looks at files the generator still produces.
   *
   * @param root the SDK root directory
   * @return the file names, sorted
   * @throws IOException if the output directory cannot be listed
   */
  public static java.util.Set<String> committedGeneratedFiles(Path root) throws IOException {
    Path dir = root.resolve(Emitter.OUTPUT_DIR);
    java.util.Set<String> out = new TreeSet<>();
    if (!Files.isDirectory(dir)) {
      return out;
    }
    try (java.util.stream.Stream<Path> files = Files.list(dir)) {
      for (Path p : (Iterable<Path>) files.sorted()::iterator) {
        if (!p.getFileName().toString().endsWith(".java")) {
          continue;
        }
        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        if (content.contains(Emitter.GENERATED_MARKER)) {
          out.add(p.getFileName().toString());
        }
      }
    }
    return out;
  }

  /**
   * Command-line entry point.
   *
   * @param args {@code --check} to verify, and optionally the SDK root
   * @throws IOException if the artifact or the output cannot be read or written
   */
  public static void main(String[] args) throws IOException {
    boolean check = false;
    Path root = Paths.get(".");
    for (String a : args) {
      if ("--check".equals(a)) {
        check = true;
      } else if (!a.startsWith("--")) {
        root = Paths.get(a);
      }
    }

    Map<String, String> rendered;
    try {
      rendered = render(root);
    } catch (Surface.SurfaceException e) {
      System.err.println("gen-authzen-types: " + e.getMessage());
      System.exit(1);
      return;
    }
    Path dir = root.resolve(Emitter.OUTPUT_DIR);

    if (check) {
      List<String> problems = new ArrayList<>();
      for (Map.Entry<String, String> e : rendered.entrySet()) {
        Path target = dir.resolve(e.getKey());
        if (!Files.isRegularFile(target)) {
          problems.add(e.getKey() + " is missing");
          continue;
        }
        String have = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        if (!have.equals(e.getValue())) {
          problems.add(e.getKey() + " is not what " + Emitter.SURFACE_PATH + " generates");
        }
      }
      for (String stale : committedGeneratedFiles(root)) {
        if (!rendered.containsKey(stale)) {
          problems.add(stale + " is generated but the artifact no longer describes it");
        }
      }
      if (!problems.isEmpty()) {
        System.err.println("gen-authzen-types: the committed types are not current:");
        for (String p : problems) {
          System.err.println("  - " + p);
        }
        System.err.println("Regenerate them in the same change:");
        System.err.println("  " + Emitter.REGENERATE_COMMAND);
        System.exit(1);
        return;
      }
      System.out.println("gen-authzen-types: " + rendered.size() + " file(s) are current.");
      return;
    }

    Files.createDirectories(dir);
    for (String stale : committedGeneratedFiles(root)) {
      if (!rendered.containsKey(stale)) {
        Files.delete(dir.resolve(stale));
        System.out.println("removed " + stale + " (no longer described by the artifact)");
      }
    }
    for (Map.Entry<String, String> e : rendered.entrySet()) {
      Files.write(dir.resolve(e.getKey()), e.getValue().getBytes(StandardCharsets.UTF_8));
    }
    System.out.println("wrote " + rendered.size() + " file(s) to " + dir);
  }
}
