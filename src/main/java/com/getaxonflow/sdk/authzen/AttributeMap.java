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
   * Records one attribute.
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
   * Returns the nested bag at {@code key}, creating it when there is not one.
   *
   * <p>Used by the request builders to write {@code context.args.query} and {@code
   * context.correlation.<key>} without a caller having to assemble the nesting.
   *
   * @param key the member name
   * @return the nested bag, which is live: mutating it mutates this one
   */
  AttributeMap nested(String key) {
    Attribute<AttributeValue> existing = members.get(key);
    if (existing != null && existing.isKnown()) {
      Optional<AttributeMap> bag = existing.asKnown().flatMap(AttributeValue::asNested);
      if (bag.isPresent()) {
        return bag.get();
      }
    }
    AttributeMap fresh = new AttributeMap();
    putKnown(key, fresh);
    return fresh;
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
