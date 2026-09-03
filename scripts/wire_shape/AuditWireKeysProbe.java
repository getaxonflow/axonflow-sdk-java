// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Wire-key introspection probe for wire-shape Gate 5 (audit-surface binding, #3254).
 *
 * <p>Run by scripts/wire_shape/validate.py in java source-file mode against the COMPILED SDK
 * classes ({@code target/classes}) plus the resolved dependency classpath. For every
 * fully-qualified class name passed as an argument it asks Jackson - configured EXACTLY as
 * production configures it, by reflecting the private {@code AxonFlow.createObjectMapper()}
 * factory - for the wire property names, as the union of the serialization and deserialization
 * bean descriptions, and prints one JSON object of the shape {@code {SimpleName: {"keys":
 * [sorted wire keys], "deprecated": [subset whose backing member - field, getter, setter, or
 * creator parameter - carries {@code @Deprecated}]}}}. The {@code deprecated} set lets the
 * caller enforce the deprecation tie: an allowlisted fiction key must be visibly deprecated in
 * the model, not silently tolerated.
 *
 * <p>Why introspection instead of source-regex discovery: a regex over the source cannot resolve a
 * constant-valued annotation ({@code @JsonProperty(SOME_CONSTANT)}) and cannot see Jackson's
 * getter auto-detection (an unannotated public {@code getFoo()} serializes {@code foo} with no
 * {@code @JsonProperty} anywhere). Both were demonstrated as Gate 5 bypasses in review.
 *
 * <p><b>Stated scope - what this probe can and cannot certify.</b> {@code
 * BeanDescription.findProperties()} reports declared bean properties only. Jackson mechanisms that
 * add, rename, or replace wire keys outside that set are invisible to it: {@code @JsonUnwrapped}
 * (inlines a nested object's keys in place of the container's name), {@code @JsonAnyGetter} and
 * {@code @JsonAnySetter} (arbitrary top-level keys at runtime), {@code @JsonAlias} (extra
 * readable names), {@code @JsonValue} (replaces the whole object shape), and class-level
 * {@code @JsonSerialize}, {@code @JsonDeserialize}, {@code @JsonTypeInfo}, {@code @JsonAppend}
 * and {@code @JsonNaming} (custom or rewritten shapes). Rather than report a key set it cannot
 * vouch for, the probe REFUSES to certify a bound type that uses any of these: it scans the class
 * hierarchy (class-level annotations, fields, methods, constructors and their parameters) and
 * exits 2 naming the mechanism and member. The caller treats any non-zero exit as an unresolvable
 * binding and FAILS the gate - never skips. All three review-demonstrated round-2 bypasses (a
 * {@code @JsonUnwrapped} container named after a bound key, a {@code @JsonAnyGetter} map, a
 * {@code @JsonAlias} fiction key) land in this refusal.
 *
 * <p>Failure behavior: any unresolvable input (class not found, refused mechanism present, mapper
 * factory not reflectable, introspection error) prints the cause to stderr and exits 2.
 */
public final class AuditWireKeysProbe {

  private AuditWireKeysProbe() {}

  /** Member-level annotations that alter the wire key set invisibly to findProperties(). */
  private static final List<String> REFUSED_MEMBER_ANNOTATIONS =
      List.of(
          "com.fasterxml.jackson.annotation.JsonUnwrapped",
          "com.fasterxml.jackson.annotation.JsonAnyGetter",
          "com.fasterxml.jackson.annotation.JsonAnySetter",
          "com.fasterxml.jackson.annotation.JsonAlias",
          "com.fasterxml.jackson.annotation.JsonValue");

  /** Class-level annotations that replace or rewrite the whole wire shape. */
  private static final List<String> REFUSED_CLASS_ANNOTATIONS =
      List.of(
          "com.fasterxml.jackson.databind.annotation.JsonSerialize",
          "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
          "com.fasterxml.jackson.annotation.JsonTypeInfo",
          "com.fasterxml.jackson.databind.annotation.JsonAppend",
          "com.fasterxml.jackson.databind.annotation.JsonNaming");

  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("usage: AuditWireKeysProbe <fully-qualified-class>...");
      System.exit(2);
    }
    try {
      ObjectMapper mapper = productionConfiguredMapper();
      TreeMap<String, TreeMap<String, TreeSet<String>>> result = new TreeMap<>();
      for (String fqcn : args) {
        Class<?> cls = Class.forName(fqcn);
        refuseUnintrospectableMechanisms(cls);
        JavaType type = mapper.constructType(cls);
        TreeSet<String> keys = new TreeSet<>();
        TreeSet<String> deprecated = new TreeSet<>();
        BeanDescription ser = mapper.getSerializationConfig().introspect(type);
        for (BeanPropertyDefinition p : ser.findProperties()) {
          keys.add(p.getName());
          if (isDeprecated(p)) {
            deprecated.add(p.getName());
          }
        }
        BeanDescription deser = mapper.getDeserializationConfig().introspect(type);
        for (BeanPropertyDefinition p : deser.findProperties()) {
          keys.add(p.getName());
          if (isDeprecated(p)) {
            deprecated.add(p.getName());
          }
        }
        TreeMap<String, TreeSet<String>> entry = new TreeMap<>();
        entry.put("keys", keys);
        entry.put("deprecated", deprecated);
        result.put(cls.getSimpleName(), entry);
      }
      System.out.println(mapper.writeValueAsString(result));
    } catch (Throwable t) {
      System.err.println("AuditWireKeysProbe FAILED: " + t.getMessage());
      System.exit(2);
    }
  }

  /**
   * Obtains a mapper configured exactly as production configures its own, by reflecting the
   * private {@code AxonFlow.createObjectMapper()} factory. Property discovery would be identical
   * under a bare {@code new ObjectMapper()} today, but would diverge SILENTLY the day production
   * gains a module, annotation introspector, or naming strategy - so the probe refuses to guess.
   * If the factory is renamed or removed this throws (exit 2, gate FAILS loudly);
   * {@code AxonFlow.createObjectMapper} carries the mirror note pointing back here.
   */
  private static ObjectMapper productionConfiguredMapper() throws Exception {
    Class<?> axonflow = Class.forName("com.getaxonflow.sdk.AxonFlow");
    Method factory = axonflow.getDeclaredMethod("createObjectMapper");
    factory.setAccessible(true);
    return (ObjectMapper) factory.invoke(null);
  }

  /**
   * A property is deprecated if ANY of its backing members (field, getter, setter, creator
   * parameter) carries {@code @Deprecated}. {@code java.lang.Deprecated} has runtime retention,
   * so the compiled classes carry it.
   */
  private static boolean isDeprecated(BeanPropertyDefinition p) {
    return (p.getField() != null && p.getField().getAnnotation(Deprecated.class) != null)
        || (p.getGetter() != null && p.getGetter().getAnnotation(Deprecated.class) != null)
        || (p.getSetter() != null && p.getSetter().getAnnotation(Deprecated.class) != null)
        || (p.getConstructorParameter() != null
            && p.getConstructorParameter().getAnnotation(Deprecated.class) != null);
  }

  private static void refuseUnintrospectableMechanisms(Class<?> cls) {
    for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
      refuse(cls, c, "class " + c.getSimpleName(), REFUSED_CLASS_ANNOTATIONS);
      for (Field f : c.getDeclaredFields()) {
        refuse(cls, f, "field " + f.getName(), REFUSED_MEMBER_ANNOTATIONS);
      }
      for (Method m : c.getDeclaredMethods()) {
        refuse(cls, m, "method " + m.getName(), REFUSED_MEMBER_ANNOTATIONS);
      }
      for (Constructor<?> k : c.getDeclaredConstructors()) {
        refuse(cls, k, "constructor", REFUSED_MEMBER_ANNOTATIONS);
        for (Parameter p : k.getParameters()) {
          refuse(cls, p, "constructor parameter " + p.getName(), REFUSED_MEMBER_ANNOTATIONS);
        }
      }
    }
  }

  private static void refuse(
      Class<?> boundType, AnnotatedElement element, String where, List<String> refusedNames) {
    for (Annotation a : element.getAnnotations()) {
      if (refusedNames.contains(a.annotationType().getName())) {
        throw new IllegalStateException(
            "bound type "
                + boundType.getName()
                + " uses @"
                + a.annotationType().getSimpleName()
                + " on "
                + where
                + " - this mechanism alters the wire key set in ways "
                + "BeanDescription.findProperties() cannot see, so the probe refuses to certify "
                + "the type (Gate 5 fails rather than reporting a key set it cannot vouch for). "
                + "Remove the mechanism from the audit surface, or extend the probe to derive "
                + "the real key set for it first.");
      }
    }
  }
}
