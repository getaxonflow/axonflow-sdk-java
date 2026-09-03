// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A leaf value, or a nested bag.
 *
 * <p>Nesting is not decoration: {@code context.args.query} and {@code
 * context.correlation.x-session-id} are LEAVES two levels down, and the refusal for an unresolvable
 * one has to name the leaf. A flat bag whose values were opaque JSON would report {@code
 * /evaluation/context/correlation} for a single unresolvable session id, which tells an operator to
 * go looking through an object rather than at a member.
 *
 * <p>Like {@link Attribute}, a final class with a private constructor: the two cases are closed and
 * a third cannot be added from outside.
 */
public final class AttributeValue {

  private final JsonNode json;
  private final AttributeMap nested;

  private AttributeValue(JsonNode json, AttributeMap nested) {
    this.json = json;
    this.nested = nested;
  }

  /**
   * A nested bag, whose own members each carry the three states.
   *
   * @param nested the bag
   * @return the value
   */
  public static AttributeValue of(AttributeMap nested) {
    return new AttributeValue(null, Objects.requireNonNull(nested, "a nested bag is not null"));
  }

  /**
   * A string leaf.
   *
   * @param value the string
   * @return the value
   */
  public static AttributeValue of(String value) {
    // TextNode.valueOf(null) returns null, which would build a KNOWN attribute
    // holding nothing - the collapse this type exists to prevent, one level
    // down. A caller with no string has Attribute.absent().
    return new AttributeValue(
        TextNode.valueOf(Objects.requireNonNull(value, "a known string is not null")), null);
  }

  /**
   * A boolean leaf.
   *
   * @param value the boolean
   * @return the value
   */
  public static AttributeValue of(boolean value) {
    return new AttributeValue(BooleanNode.valueOf(value), null);
  }

  /**
   * An integer leaf.
   *
   * @param value the number
   * @return the value
   */
  public static AttributeValue of(long value) {
    return new AttributeValue(
        value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
            ? IntNode.valueOf((int) value)
            : LongNode.valueOf(value),
        null);
  }

  /**
   * Any JSON, normalised.
   *
   * <p>A JSON object becomes a NESTED bag whose members are all {@code known}. Without the
   * normalisation there would be two representations of the same bytes — a raw object node and a
   * nested bag — and a round trip through the wire would silently move a value from one to the
   * other. One representation means equality on the Java value means equality on the wire.
   *
   * @param node the JSON
   * @return the value
   */
  public static AttributeValue of(JsonNode node) {
    Objects.requireNonNull(node, "a JSON value is not null");
    if (!node.isObject()) {
      return new AttributeValue(node, null);
    }
    AttributeMap bag = new AttributeMap();
    Iterator<Map.Entry<String, JsonNode>> fields = ((ObjectNode) node).fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      bag.putKnown(entry.getKey(), of(entry.getValue()));
    }
    return of(bag);
  }

  /**
   * Reads whichever case this is.
   *
   * @param onJson applied to a leaf
   * @param onNested applied to a nested bag
   * @param <R> the result type
   * @return whichever branch applies
   */
  public <R> R fold(Function<JsonNode, R> onJson, Function<AttributeMap, R> onNested) {
    return nested != null ? onNested.apply(nested) : onJson.apply(json);
  }

  /**
   * The nested bag, when this is one.
   *
   * @return the bag, or empty for a leaf
   */
  public Optional<AttributeMap> asNested() {
    return Optional.ofNullable(nested);
  }

  /**
   * The leaf JSON, when this is one.
   *
   * @return the node, or empty for a nested bag
   */
  public Optional<JsonNode> asJson() {
    return Optional.ofNullable(json);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AttributeValue)) {
      return false;
    }
    AttributeValue that = (AttributeValue) other;
    return Objects.equals(json, that.json) && Objects.equals(nested, that.nested);
  }

  @Override
  public int hashCode() {
    return Objects.hash(json, nested);
  }

  @Override
  public String toString() {
    return nested != null ? nested.toString() : String.valueOf(json);
  }
}
