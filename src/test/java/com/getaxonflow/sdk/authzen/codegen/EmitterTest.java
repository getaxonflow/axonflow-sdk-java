// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The emitter's own tests.
 *
 * <p>These are about the EMITTER, not about the AuthZEN surface: they feed it artifacts the real
 * one is not, and assert it refuses rather than generating something plausible. The check that the
 * committed output matches the real artifact lives in {@code AuthZENGeneratedTypesAreCurrentTest}.
 */
class EmitterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A minimal artifact that parses and emits, as the base for mutations. */
  private static ObjectNode baseArtifact() {
    try {
      return (ObjectNode)
          MAPPER.readTree(
              "{"
                  + "\"artifact\":\"axonflow-authzen-surface\","
                  + "\"artifact_version\":1,"
                  + "\"profile_header\":\"X-Axonflow-AuthZEN-Profile\","
                  + "\"route\":{\"method\":\"POST\",\"path\":\"/api/v1/access/evaluation\"},"
                  + "\"profile\":\"axonflow-authzen-profile-2026-08-29\","
                  + "\"contract_schema_version\":\"2026-08-29\","
                  + "\"source_schema_id\":\"https://example.invalid/schema.json\","
                  + "\"source_schema_sha256\":\"sha256:00\","
                  + "\"enums\":[{\"name\":\"state\",\"values\":[\"ALLOW\",\"DENY\"]}],"
                  + "\"types\":["
                  + "{\"name\":\"authzen_leaf\",\"fields\":["
                  + "{\"name\":\"id\",\"required\":true,\"type\":{\"kind\":\"string\"}}]},"
                  + "{\"name\":\"authzen_holder\",\"fields\":["
                  + "{\"name\":\"leaf\",\"required\":false,"
                  + "\"type\":{\"kind\":\"ref\",\"ref\":\"authzen_leaf\"},"
                  + "\"requires_members\":[\"id\"]},"
                  + "{\"name\":\"props\",\"required\":false,\"type\":{\"kind\":\"object\"}}]}"
                  + "]}");
    } catch (Exception e) {
      throw new AssertionError("the fixture is JSON", e);
    }
  }

  private static Map<String, String> render(ObjectNode artifact) {
    try {
      return Emitter.emit(Surface.parse(MAPPER.writeValueAsBytes(artifact)));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError("the fixture serialises", e);
    }
  }

  private static String rejection(ObjectNode artifact) {
    try {
      render(artifact);
    } catch (Surface.SurfaceException e) {
      return e.getMessage();
    }
    throw new AssertionError("the emitter accepted an artifact it should have refused");
  }

  @Test
  @DisplayName("the base artifact emits, so every rejection below is about its own mutation")
  void baseArtifactEmits() {
    // Without this, a mutation test could pass because the BASE was already
    // broken — the rejection would be real and the mutation irrelevant.
    Map<String, String> out = render(baseArtifact());
    assertThat(out).containsKeys("AuthZENLeaf.java", "AuthZENHolder.java", "AuthZENState.java");
    assertThat(out.get("AuthZENLeaf.java")).contains("public final class AuthZENLeaf");
  }

  @Test
  @DisplayName("an artifact member the emitter does not understand is refused")
  void unknownArtifactMemberIsRefused() {
    ObjectNode a = baseArtifact();
    ((ObjectNode) a.get("types").get(0).get("fields").get(0)).put("max_length", 64);
    assertThat(rejection(a)).contains("max_length");
  }

  @Test
  @DisplayName("a reference to an undeclared type is refused")
  void danglingTypeReferenceIsRefused() {
    ObjectNode a = baseArtifact();
    ((ObjectNode) a.get("types").get(1).get("fields").get(0).get("type"))
        .put("ref", "authzen_missing");
    assertThat(rejection(a)).contains("authzen_missing");
  }

  @Test
  @DisplayName("a reference to an undeclared enum is refused")
  void danglingEnumReferenceIsRefused() {
    ObjectNode a = baseArtifact();
    ObjectNode type = (ObjectNode) a.get("types").get(0).get("fields").get(0);
    type.putObject("type").put("kind", "enum").put("enum", "nope");
    assertThat(rejection(a)).contains("nope");
  }

  @Test
  @DisplayName("an unsupported type kind is refused rather than rendered as anything")
  void unsupportedKindIsRefused() {
    ObjectNode a = baseArtifact();
    ObjectNode field = (ObjectNode) a.get("types").get(0).get("fields").get(0);
    field.putObject("type").put("kind", "decimal");
    assertThat(rejection(a)).contains("decimal");
  }

  @Test
  @DisplayName("a duplicate type name is refused")
  void duplicateTypeIsRefused() {
    ObjectNode a = baseArtifact();
    ((com.fasterxml.jackson.databind.node.ArrayNode) a.get("types")).add(a.get("types").get(0));
    assertThat(rejection(a)).contains("twice");
  }

  @Test
  @DisplayName("a duplicate enum value is refused")
  void duplicateEnumValueIsRefused() {
    ObjectNode a = baseArtifact();
    ObjectNode e = (ObjectNode) a.get("enums").get(0);
    e.putArray("values").add("ALLOW").add("ALLOW");
    assertThat(rejection(a)).contains("twice");
  }

  @Test
  @DisplayName("an exactly-one-of group naming a field that does not exist is refused")
  void exactlyOneOfGhostMemberIsRefused() {
    ObjectNode a = baseArtifact();
    ObjectNode holder = (ObjectNode) a.get("types").get(1);
    holder.putArray("exactly_one_of").addArray().add("leaf").add("ghost");
    assertThat(rejection(a)).contains("ghost");
  }

  @Test
  @DisplayName("an exactly-one-of group with a single member is refused")
  void exactlyOneOfSingletonIsRefused() {
    // A one-member group is not a constraint, it is a required field written in
    // a way that reads as a choice.
    ObjectNode a = baseArtifact();
    ObjectNode holder = (ObjectNode) a.get("types").get(1);
    holder.putArray("exactly_one_of").addArray().add("leaf");
    assertThat(rejection(a)).contains("exactly-one-of group");
  }

  @Test
  @DisplayName("requires_members is checked against the referenced type, not the declaring one")
  void requiresMembersIsCheckedAgainstTheTarget() {
    // `requires_members` names a member of the type the field POINTS AT. A typo
    // there emits a validator reading a member that does not exist, which fails
    // as a compile error in generated code rather than as a statement about the
    // artifact.
    ObjectNode a = baseArtifact();
    ObjectNode field = (ObjectNode) a.get("types").get(1).get("fields").get(0);
    field.putArray("requires_members").add("identifier");
    String message = rejection(a);
    assertThat(message).contains("identifier");
    assertThat(message).contains("authzen_leaf");
  }

  @Test
  @DisplayName("an artifact that is not this surface is refused")
  void foreignArtifactIsRefused() {
    ObjectNode a = baseArtifact();
    a.put("artifact", "something-else");
    assertThat(rejection(a)).contains("something-else");
  }

  @Test
  @DisplayName("a future artifact format version is refused rather than generated through")
  void futureFormatVersionIsRefused() {
    ObjectNode a = baseArtifact();
    a.put("artifact_version", 2);
    assertThat(rejection(a)).contains("deliberate migration");
  }

  @Test
  @DisplayName("an object member becomes the three-valued bag and never a raw map")
  void objectMembersBecomeAttributeMaps() {
    // The one emission rule with a security argument behind it. A JSON `object`
    // in this artifact is a bag of attributes the CALLER resolved, and a plain
    // map has no way to say "the source could not answer" — so rendering it as
    // one would collapse absent and unknown at the type level, before any code
    // had a chance to keep them apart.
    String holder = render(baseArtifact()).get("AuthZENHolder.java");
    assertThat(holder).contains("private AttributeMap props = new AttributeMap()");
    assertThat(holder).doesNotContain("Map<String, Object>");
  }

  @Test
  @DisplayName("no generated class carries the no-op strictness annotation")
  void noGeneratedClassCarriesTheNoOpAnnotation() {
    // A regression guard on a mistake this emitter already made once. An
    // earlier version put @JsonIgnoreProperties(ignoreUnknown = false) on every
    // generated class and a comment claiming it made unknown members fatal. It
    // does not: that value is Jackson's DEFAULT, and whether declining to
    // ignore becomes a failure is the mapper's FAIL_ON_UNKNOWN_PROPERTIES,
    // which the SDK's shared mapper turns off. A decision carrying an extra
    // member decoded straight through it.
    //
    // The strictness now lives in AxonFlow.authzenReader, where it takes
    // effect, and AuthZENSurfaceTest#unknownResponseMemberIsRefused is what
    // proves it does. This asserts the annotation does not come back, because
    // a no-op that reads as a guarantee is worse than no annotation at all.
    for (Map.Entry<String, String> file : render(baseArtifact()).entrySet()) {
      assertThat(file.getValue()).as("%s", file.getKey()).doesNotContain("JsonIgnoreProperties");
    }
  }

  @Test
  @DisplayName("generation is deterministic over repeated runs")
  void generationIsDeterministic() {
    ObjectNode a = baseArtifact();
    Map<String, String> first = render(a);
    for (int i = 1; i < 16; i++) {
      assertThat(render(a)).as("emission %d differed from the first", i).isEqualTo(first);
    }
  }

  @Test
  @DisplayName("a member rename changes the output, so a drift cannot pass the byte comparison")
  void aRenameChangesTheOutput() {
    // The survivor case for the regeneration guard: it proves the check CAN go
    // red. A guard that has only ever been observed passing is a guard nobody
    // has established is connected to anything.
    String before = render(baseArtifact()).get("AuthZENHolder.java");
    ObjectNode drifted = baseArtifact();
    ((ObjectNode) drifted.get("types").get(1).get("fields").get(1)).put("name", "attributes");
    String after = render(drifted).get("AuthZENHolder.java");

    assertThat(after).isNotEqualTo(before);
    assertThat(after).contains("private AttributeMap attributes");
    assertThat(after).doesNotContain("private AttributeMap props");
  }

  @Test
  @DisplayName("every generated list getter hands back an unmodifiable view")
  void everyListGetterIsUnmodifiable() {
    // These types are the READ model for a decision an enforcement point acts
    // on. AuthZENDecision#getObligations() wraps, but it reads through
    // AuthZENResponseContext#getObligations(), which handed the internal list
    // back unwrapped - so the same state stayed writable one getter deeper and
    // an obligation could be added to, or a mandatory one removed from, a
    // decision already handed out.
    //
    // Asserted by RENDERING rather than against the five array members today's
    // artifact happens to declare: a sixth added tomorrow is covered without
    // anybody extending a list. The byte-comparison gate cannot catch this on
    // its own - delete the wrapping from the emitter, regenerate, and the
    // committed files are exactly what the emitter now produces.
    ObjectNode a = baseArtifact();
    ObjectNode listField = MAPPER.createObjectNode();
    listField.put("name", "leaves");
    listField.put("required", false);
    listField
        .putObject("type")
        .put("kind", "array")
        .putObject("items")
        .put("kind", "ref")
        .put("ref", "authzen_leaf");
    ((com.fasterxml.jackson.databind.node.ArrayNode) a.get("types").get(1).get("fields"))
        .add(listField);

    Map<String, String> out = render(a);
    String holder = out.get("AuthZENHolder.java");
    assertThat(holder).contains("import java.util.Collections;");
    assertThat(holder)
        .contains(
            "  public List<AuthZENLeaf> getLeaves() {\n"
                + "    return leaves == null ? null : Collections.unmodifiableList(leaves);\n"
                + "  }");

    // The class check: nowhere in the whole emission does a List getter return
    // the field bare. A single wrapped getter beside an unwrapped one is the
    // exact shape this defect had.
    int listGetters = 0;
    for (Map.Entry<String, String> file : out.entrySet()) {
      String[] lines = file.getValue().split("\n", -1);
      for (int i = 0; i < lines.length - 1; i++) {
        if (!lines[i].startsWith("  public List<") || !lines[i].endsWith("() {")) {
          continue;
        }
        listGetters++;
        assertThat(lines[i + 1])
            .as("%s: %s returns its backing list unwrapped", file.getKey(), lines[i].trim())
            .contains("Collections.unmodifiableList(");
      }
    }
    assertThat(listGetters)
        .as("no List getter was emitted at all, so this assertion saw nothing")
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("an empty surface is refused rather than producing an empty SDK")
  void emptySurfaceIsRefused() {
    ObjectNode a = baseArtifact();
    a.putArray("types");
    assertThatThrownBy(() -> render(a))
        .isInstanceOf(Surface.SurfaceException.class)
        .hasMessageContaining("empty surface");
  }
}
