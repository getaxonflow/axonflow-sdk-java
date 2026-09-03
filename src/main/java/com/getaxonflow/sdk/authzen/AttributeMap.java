// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A bag of attributes, ordered by key.
 *
 * <p>A {@code TreeMap} rather than a {@code HashMap} so the same bag always produces the same
 * bytes. An authorization request whose serialisation varies run to run cannot be compared, cached
 * or asserted on, and the difference would surface as a flaky test rather than as a decision.
 *
 * <p>Read {@link Attribute} first. The reason this is not a {@code Map<String, Object>} is that a
 * resolved attribute has three states, and the third one — "the source could not answer" — has no
 * wire representation, so the bag has to refuse it rather than encode it.
 */
@JsonSerialize(using = AttributeMap.Serializer.class)
@JsonDeserialize(using = AttributeMap.Deserializer.class)
public final class AttributeMap {

  private final TreeMap<String, Attribute<AttributeValue>> members = new TreeMap<>();

  /** An empty bag: the caller resolved no attributes at all. */
  public AttributeMap() {}

  /**
   * Whether the bag holds no members.
   *
   * <p>An empty bag and an absent bag are the same statement, "no attributes", which is why the
   * generated types hold an {@code AttributeMap} rather than a nullable one. A member whose value
   * is ABSENT still counts here: the caller said something about it, and dropping that distinction
   * would make a bag of three absent facts indistinguishable from a bag nobody filled in.
   *
   * @return true when no member has been recorded
   */
  public boolean isEmpty() {
    return members.isEmpty();
  }

  /**
   * How many members the bag holds, in any state.
   *
   * @return the member count
   */
  public int size() {
    return members.size();
  }

  /**
   * Records one attribute, REPLACING whatever was there.
   *
   * <p>This is the map operation, and it behaves like one: a caller writing here is making a
   * deliberate replacement. If what it replaces was an unresolved attribute, that fact is gone -
   * which is why the request builders do not use it. See {@link #record}.
   *
   * @param key the member name
   * @param value the attribute, in whichever of the three states it resolved to
   * @return this, for chaining
   */
  public AttributeMap put(String key, Attribute<AttributeValue> value) {
    members.put(
        Objects.requireNonNull(key, "a member name is not null"),
        Objects.requireNonNull(value, "use Attribute.absent() rather than null"));
    return this;
  }

  /**
   * Records a resolved value.
   *
   * @param key the member name
   * @param value the value
   * @return this, for chaining
   */
  public AttributeMap putKnown(String key, AttributeValue value) {
    return put(key, Attribute.known(value));
  }

  /**
   * Records a resolved string.
   *
   * @param key the member name
   * @param value the value
   * @return this, for chaining
   */
  public AttributeMap putKnown(String key, String value) {
    return put(key, Attribute.known(AttributeValue.of(value)));
  }

  /**
   * Records a nested bag.
   *
   * @param key the member name
   * @param value the bag
   * @return this, for chaining
   */
  public AttributeMap putKnown(String key, AttributeMap value) {
    return put(key, Attribute.known(AttributeValue.of(value)));
  }

  /**
   * Records that the source answered, and there is no value.
   *
   * @param key the member name
   * @return this, for chaining
   */
  public AttributeMap putAbsent(String key) {
    return put(key, Attribute.absent());
  }

  /**
   * Records that the source could not answer.
   *
   * @param key the member name
   * @param why what went wrong
   * @return this, for chaining
   */
  public AttributeMap putUnknown(String key, String why) {
    return put(key, Attribute.unknown(why));
  }

  /**
   * Records one attribute, DECLINING to overwrite an unresolved one.
   *
   * <p>This is the write the request builders use, at every level, and the rule is uniform: an
   * unknown at {@code key} survives and the new value is not written.
   *
   * <p>The rule has to be uniform because the alternative was measured. An earlier version guarded
   * only the PARENT key - so {@code writeQuery} refused to replace an unresolved {@code
   * context.args}, and then wrote {@code query} into it unguarded. A caller that recorded "nobody
   * could read the request body" and then wrote a recovered partial query produced a
   * complete-looking envelope one level down, which is verbatim the scenario the guard was added to
   * prevent.
   *
   * <p>A declined write is not silent: the unknown that survived refuses the envelope at its own
   * pointer, carrying the reason the caller gave, so the request is never sent.
   *
   * @param key the member name
   * @param value the attribute
   * @return true when the value was written, false when an unresolved attribute was preserved
   */
  public boolean record(String key, Attribute<AttributeValue> value) {
    Objects.requireNonNull(key, "a member name is not null");
    Objects.requireNonNull(value, "use Attribute.absent() rather than null");
    if (holdsUnresolved(key)) {
      return false;
    }
    members.put(key, value);
    return true;
  }

  /**
   * Whether {@code key} holds an attribute nobody could resolve.
   *
   * <p>ONE place, used by both writes. The rule was duplicated across {@link #record} and {@link
   * #nestedForWrite} and the duplication was not academic: a mutation pass could not tell the two
   * apart, so a mutant aimed at one silently hit the other and a guard nothing was holding in place
   * looked covered.
   *
   * @param key the member name
   * @return true when the member is in the unknown state
   */
  private boolean holdsUnresolved(String key) {
    Attribute<AttributeValue> existing = members.get(key);
    return existing != null && existing.isUnknown();
  }

  /**
   * Reads one attribute.
   *
   * @param key the member name
   * @return the attribute, or empty when the bag has no such member
   */
  public Optional<Attribute<AttributeValue>> get(String key) {
    return Optional.ofNullable(members.get(key));
  }

  /**
   * The members, in key order.
   *
   * @return an unmodifiable view
   */
  public Map<String, Attribute<AttributeValue>> asMap() {
    return Collections.unmodifiableMap(members);
  }

  /**
   * The nested bag at {@code key}, ready to be written into - unless writing there would ERASE an
   * unresolved attribute.
   *
   * <p>Used by the request builders to write {@code context.args.query} and {@code
   * context.correlation.<key>} without a caller having to assemble the nesting.
   *
   * @param key the member name
   * @return the nested bag, live (mutating it mutates this one), or empty when the key holds an
   *     unresolved attribute the write must not overwrite
   */
  Optional<AttributeMap> nestedForWrite(String key) {
    if (holdsUnresolved(key)) {
      // The one state a later write must not overwrite. The caller has already
      // said nobody could resolve this member; quietly replacing it with a fresh
      // bag would produce a complete-looking request whose missing fact nothing
      // records. Declining the write leaves the UNKNOWN in place, so validate()
      // refuses the envelope at that member and the request is never sent.
      return Optional.empty();
    }
    Attribute<AttributeValue> existing = members.get(key);
    if (existing != null && existing.isKnown()) {
      Optional<AttributeMap> bag = existing.asKnown().flatMap(AttributeValue::asNested);
      if (bag.isPresent()) {
        return bag;
      }
    }
    // ABSENT, or a leaf: both are resolved statements carrying no
    // unresolvability to lose, so last-write-wins is what a caller expects.
    AttributeMap fresh = new AttributeMap();
    putKnown(key, fresh);
    return Optional.of(fresh);
  }

  /**
   * Refuses a bag holding an attribute nobody could resolve.
   *
   * <p>{@code at} is the JSON Pointer this bag sits at, so the refusal names the member the way the
   * server names it — and the FIRST unresolvable member in key order, so the same bag always
   * produces the same refusal rather than one that depends on iteration luck.
   *
   * <p>The alternative — sending the request without the member — is the fail-open this type exists
   * to prevent: the server would evaluate a complete-looking request, the audit row would record a
   * decision made on the attributes present, and nothing anywhere would record that one of them was
   * never resolved.
   *
   * @param at the JSON Pointer this bag sits at
   * @throws AuthZENRefusedException when a member is in the unknown state
   */
  public void validate(String at) {
    for (Map.Entry<String, Attribute<AttributeValue>> entry : members.entrySet()) {
      String pointer = at + "/" + entry.getKey();
      Attribute<AttributeValue> attribute = entry.getValue();
      if (attribute.isUnknown()) {
        throw AuthZENRefusedException.of(
            AuthZENErrorCode.EVALUATION_UNAVAILABLE,
            pointer,
            "the attribute \""
                + entry.getKey()
                + "\" could not be resolved ("
                + attribute.getReason().orElse("no reason given")
                + "); sending the request without it would obtain a decision that weighed every"
                + " attribute except the one nobody could read, and report it as complete");
      }
      if (attribute.isKnown()) {
        Optional<AttributeMap> nested = attribute.asKnown().flatMap(AttributeValue::asNested);
        if (nested.isPresent()) {
          nested.get().validate(pointer);
        }
      }
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AttributeMap)) {
      return false;
    }
    return members.equals(((AttributeMap) other).members);
  }

  @Override
  public int hashCode() {
    return members.hashCode();
  }

  @Override
  public String toString() {
    return members.toString();
  }

  /**
   * Absent members are omitted; an unknown member is a serialisation FAILURE.
   *
   * <p>{@link AttributeMap#validate} is the check a caller is meant to hit, and it produces a typed
   * refusal with a pointer. This is the backstop underneath it: a future code path that encoded an
   * envelope without validating it first cannot quietly drop the unresolved member, because there
   * is no encoding of "unknown" for it to fall back to.
   */
  public static final class Serializer extends JsonSerializer<AttributeMap> {

    @Override
    public void serialize(AttributeMap bag, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeStartObject();
      for (Map.Entry<String, Attribute<AttributeValue>> entry : bag.members.entrySet()) {
        Attribute<AttributeValue> attribute = entry.getValue();
        if (attribute.isAbsent()) {
          continue;
        }
        if (attribute.isUnknown()) {
          throw new JsonMappingException(
              gen,
              "the attribute \""
                  + entry.getKey()
                  + "\" could not be resolved ("
                  + attribute.getReason().orElse("no reason given")
                  + ") and has no wire representation; validate the envelope before encoding it");
        }
        gen.writeFieldName(entry.getKey());
        AttributeValue value = attribute.asKnown().orElseThrow(IllegalStateException::new);
        Optional<AttributeMap> nested = value.asNested();
        if (nested.isPresent()) {
          serialize(nested.get(), gen, provider);
        } else {
          gen.writeTree(value.asJson().orElseThrow(IllegalStateException::new));
        }
      }
      gen.writeEndObject();
    }

    /**
     * Lets {@code @JsonInclude(NON_EMPTY)} omit a bag nobody filled in.
     *
     * <p>Without it Jackson has no way to ask a custom type whether it is empty, and every request
     * would carry {@code "properties": {}} — which is not wrong, but is a member the caller did not
     * write, on a surface that refuses members the caller did not mean.
     */
    @Override
    public boolean isEmpty(SerializerProvider provider, AttributeMap value) {
      return value == null || value.isEmpty();
    }
  }

  /**
   * Every member present on the wire decodes as KNOWN.
   *
   * <p>There is no decoding that yields absent or unknown, and there should not be: both are
   * statements about a RESOLUTION the sender performed, and the wire carries the result of that
   * resolution rather than the resolution itself.
   */
  public static final class Deserializer extends StdDeserializer<AttributeMap> {

    private static final long serialVersionUID = 1L;

    /** Jackson requires a no-argument constructor. */
    public Deserializer() {
      super(AttributeMap.class);
    }

    @Override
    public AttributeMap deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      JsonNode node = parser.getCodec().readTree(parser);
      AttributeMap bag = new AttributeMap();
      if (node == null || !node.isObject()) {
        return bag;
      }
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        bag.putKnown(entry.getKey(), AttributeValue.of(entry.getValue()));
      }
      return bag;
    }
  }
}
