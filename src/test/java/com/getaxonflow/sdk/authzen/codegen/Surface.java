// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen.codegen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The language-neutral AuthZEN surface artifact, as this emitter reads it.
 *
 * <p>These declarations mirror the platform's producer side. They are a SUBSET on purpose: an
 * emitter must fail on an artifact member it does not understand rather than generate around it,
 * which is why {@link #parse} rejects unknown members instead of ignoring them. A member the
 * platform added and this SDK silently omitted is the declared-but-never-emitted class arriving
 * through the very generator built to prevent it.
 *
 * <p>This class is TEST-SCOPED. A code generator has no business being compiled into a published
 * SDK, and keeping it out of {@code src/main} means it cannot become part of the API by accident.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public final class Surface {

  @JsonProperty("artifact")
  public String artifact;

  @JsonProperty("artifact_version")
  public int artifactVersion;

  @JsonProperty("profile")
  public String profile;

  @JsonProperty("contract_schema_version")
  public String contractSchemaVersion;

  @JsonProperty("source_schema_id")
  public String sourceSchemaId;

  @JsonProperty("source_schema_sha256")
  public String sourceSchemaSha256;

  @JsonProperty("enums")
  public List<EnumDecl> enums = new ArrayList<>();

  @JsonProperty("types")
  public List<TypeDecl> types = new ArrayList<>();

  /** A closed set of string values. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  public static final class EnumDecl {
    @JsonProperty("name")
    public String name;

    @JsonProperty("doc")
    public String doc = "";

    @JsonProperty("values")
    public List<String> values = new ArrayList<>();
  }

  /** One object shape. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  public static final class TypeDecl {
    @JsonProperty("name")
    public String name;

    @JsonProperty("doc")
    public String doc = "";

    @JsonProperty("fields")
    public List<FieldDecl> fields = new ArrayList<>();

    @JsonProperty("exactly_one_of")
    public List<List<String>> exactlyOneOf = new ArrayList<>();
  }

  /** One member of a type. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  public static final class FieldDecl {
    @JsonProperty("name")
    public String name;

    @JsonProperty("doc")
    public String doc = "";

    @JsonProperty("required")
    public boolean required;

    @JsonProperty("type")
    public TypeRef type;

    @JsonProperty("min_items")
    public int minItems;

    @JsonProperty("min_length")
    public int minLength;

    @JsonProperty("requires_members")
    public List<String> requiresMembers = new ArrayList<>();

    @JsonProperty("const")
    public String constant = "";
  }

  /** A field's type. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  public static final class TypeRef {
    @JsonProperty("kind")
    public String kind;

    @JsonProperty("ref")
    public String ref = "";

    @JsonProperty("enum")
    public String enumName = "";

    @JsonProperty("items")
    public TypeRef items;

    @JsonProperty("value")
    public TypeRef value;
  }

  /** Why an artifact could not be read, or does not hang together. */
  public static final class SurfaceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SurfaceException(String message) {
      super(message);
    }
  }

  private static SurfaceException fail(String message) {
    return new SurfaceException(message);
  }

  /**
   * Decodes the artifact STRICTLY and checks that it hangs together.
   *
   * <p>Every reference must resolve inside the document. A dangling ref would otherwise become a
   * Java type name that does not exist, and the failure would surface as a compile error in
   * generated code rather than as a statement about the artifact.
   *
   * <p>The mapper is built here rather than reused from the SDK. {@code AxonFlow}'s mapper sets
   * {@code FAIL_ON_UNKNOWN_PROPERTIES} to false, which is right for a wire response from a server
   * that may be newer, and exactly wrong for an artifact this generator must refuse to guess about.
   *
   * @param raw the artifact bytes
   * @return the parsed surface
   */
  public static Surface parse(byte[] raw) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    Surface s;
    try {
      s = mapper.readValue(raw, Surface.class);
    } catch (IOException e) {
      throw fail("parsing the surface artifact: " + e.getMessage());
    }

    Set<String> typeNames = new HashSet<>();
    for (TypeDecl t : s.types) {
      if (!typeNames.add(t.name)) {
        throw fail("the artifact declares the type \"" + t.name + "\" twice");
      }
    }
    Set<String> enumNames = new HashSet<>();
    for (EnumDecl e : s.enums) {
      if (!enumNames.add(e.name)) {
        throw fail("the artifact declares the enum \"" + e.name + "\" twice");
      }
      if (e.values.isEmpty()) {
        throw fail("enum \"" + e.name + "\" has no values");
      }
      Set<String> seen = new HashSet<>();
      for (String v : e.values) {
        if (!seen.add(v)) {
          throw fail("enum \"" + e.name + "\" declares the value \"" + v + "\" twice");
        }
      }
    }
    for (TypeDecl t : s.types) {
      if (t.fields.isEmpty()) {
        throw fail("type \"" + t.name + "\" has no fields");
      }
      Set<String> fieldNames = new HashSet<>();
      for (FieldDecl f : t.fields) {
        if (!fieldNames.add(f.name)) {
          throw fail("type \"" + t.name + "\" declares the field \"" + f.name + "\" twice");
        }
        checkRef(t.name + "." + f.name, f.type, typeNames, enumNames);
        if (!f.requiresMembers.isEmpty()) {
          // `requires_members` names a member of the type the field POINTS AT,
          // not of the declaring type. Checking it against the wrong one would
          // let a typo through and emit a validator reading a field that does
          // not exist.
          if (f.type == null || !"ref".equals(f.type.kind)) {
            throw fail(
                t.name
                    + "."
                    + f.name
                    + " declares requires_members but is not a reference to a declared type");
          }
          Optional<TypeDecl> target =
              s.types.stream().filter(x -> x.name.equals(f.type.ref)).findFirst();
          if (!target.isPresent()) {
            throw fail(
                t.name + "." + f.name + " references the type \"" + f.type.ref + "\", undeclared");
          }
          for (String m : f.requiresMembers) {
            boolean known = target.get().fields.stream().anyMatch(tf -> tf.name.equals(m));
            if (!known) {
              throw fail(
                  t.name
                      + "."
                      + f.name
                      + " requires the member \""
                      + m
                      + "\", which \""
                      + target.get().name
                      + "\" does not declare");
            }
          }
        }
      }
      for (List<String> group : t.exactlyOneOf) {
        if (group.size() < 2) {
          throw fail(
              "type \""
                  + t.name
                  + "\" has an exactly-one-of group with "
                  + group.size()
                  + " member(s)");
        }
        for (String m : group) {
          if (!fieldNames.contains(m)) {
            throw fail(
                "type \""
                    + t.name
                    + "\" names \""
                    + m
                    + "\" in an exactly-one-of group but has no such field");
          }
        }
      }
    }
    return s;
  }

  private static void checkRef(String where, TypeRef tr, Set<String> types, Set<String> enums) {
    if (tr == null || tr.kind == null) {
      throw fail(where + " has no type kind");
    }
    switch (tr.kind) {
      case "ref":
        if (!types.contains(tr.ref)) {
          throw fail(
              where
                  + " references the type \""
                  + tr.ref
                  + "\", which the artifact does not define");
        }
        return;
      case "enum":
        if (!enums.contains(tr.enumName)) {
          throw fail(
              where
                  + " references the enum \""
                  + tr.enumName
                  + "\", which the artifact does not define");
        }
        return;
      case "array":
        if (tr.items == null) {
          throw fail(where + " is an array with no item type");
        }
        checkRef(where + "[]", tr.items, types, enums);
        return;
      case "map":
        if (tr.value == null) {
          throw fail(where + " is a map with no value type");
        }
        checkRef(where + "{}", tr.value, types, enums);
        return;
      case "string":
      case "bool":
      case "int":
      case "object":
        return;
      default:
        throw fail(where + " has the unsupported type kind \"" + tr.kind + "\"");
    }
  }
}
