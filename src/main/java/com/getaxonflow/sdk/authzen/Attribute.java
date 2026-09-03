// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One resolved attribute, in one of THREE states — and {@code Optional} carries two.
 *
 * <p>Every attribute bag on the AuthZEN surface ({@code subject.properties}, {@code
 * action.properties}, {@code resource.properties}, {@code context}) holds facts the CALLER resolved
 * from somewhere else: an identity provider, a trace propagator, a session store. Resolving a fact
 * has three outcomes, and two of them are not the same thing:
 *
 * <ul>
 *   <li><b>known</b> — the source answered with a value.
 *   <li><b>absent</b> — the source answered, and the answer is that there is no value. This user
 *       has no department; this batch job has no session. Absent is ORDINARY RESOLVED DATA, and a
 *       decision made without it is a complete decision.
 *   <li><b>unknown</b> — the source could not answer. The identity provider timed out; the trace
 *       header was unreadable. A decision made without an unknown fact is a decision that MIGHT
 *       have gone the other way, reported as complete.
 * </ul>
 *
 * <p>Reaching for {@code Optional<T>} collapses the second and third into {@code empty()}, and the
 * collapse always resolves the wrong way: the unknown attribute gets dropped from the request, the
 * server evaluates without it, and the caller is handed a verdict that names every attribute it
 * weighed — including the one nobody could resolve. That is the exact failure the server's own
 * adapter refuses on its side of the wire ("accepting it would report that it was considered when
 * it was not"); this type is the same refusal on the client's side.
 *
 * <p>{@code Optional<Optional<T>>} is not the answer either. It is unreadable at a call site, it
 * invites {@code .flatMap()}, and the two empties are not distinguished by anything a reader can
 * see.
 *
 * <h2>What each state does to the wire</h2>
 *
 * <table border="1">
 *   <caption>state to wire to outcome</caption>
 *   <tr><th>state</th><th>wire</th><th>outcome</th></tr>
 *   <tr><td>known</td><td>the member, with its value</td><td>evaluated</td></tr>
 *   <tr><td>absent</td><td>the member is OMITTED</td><td>evaluated, without a fact that has no
 *       value</td></tr>
 *   <tr><td>unknown</td><td>never reaches the wire</td><td>refused before the round trip, NOT
 *       retryable</td></tr>
 * </table>
 *
 * <p>Absent and "never mentioned" are the same bytes, and that is correct: both say "there is no
 * such fact". JSON has no way to say "I could not find out", which is precisely why the type has to
 * carry it — the wire cannot.
 *
 * <p>The refusal an unknown attribute produces is NOT retryable, and that is the opposite of what
 * it first looks like. A source that could not answer this second may answer the next one - but
 * that is a statement about a DIFFERENT request. This one carries the unresolved attribute inside
 * it, so resending the identical bytes reproduces the identical refusal forever, and a {@code while
 * (e.isRetryable())} loop would burn its whole budget on it. Re-resolve the attribute and build a
 * new request; the SDK reports that with {@link AuthZENUnresolvedException} rather than through the
 * server's retryable {@code evaluation_unavailable}.
 *
 * <h2>Why this is a final class with private constructors</h2>
 *
 * <p>It is a sealed type in every way that matters on Java 11, which has no {@code sealed} keyword:
 * the constructor is private and the only three ways to make one are the factories below, so the
 * set of states is closed and a fourth cannot be added from outside. {@link #fold} is the reader
 * that will not compile until a caller has said what all three mean.
 *
 * @param <T> the resolved value's type
 */
public final class Attribute<T> {

  /** Which of the three states an attribute is in. */
  public enum State {
    /** The source answered with a value. */
    KNOWN,
    /** The source answered: there is no value. */
    ABSENT,
    /** The source could not answer. */
    UNKNOWN
  }

  private final State state;
  private final T value;
  private final String reason;

  private Attribute(State state, T value, String reason) {
    this.state = state;
    this.value = value;
    this.reason = reason;
  }

  /**
   * The source answered with a value.
   *
   * @param value the resolved value, which must not be null — a null "known" value is the collapse
   *     this type exists to prevent, expressed one level down
   * @param <T> the value's type
   * @return the attribute
   */
  public static <T> Attribute<T> known(T value) {
    return new Attribute<>(
        State.KNOWN, Objects.requireNonNull(value, "a known value is not null"), null);
  }

  /**
   * The source answered, and there is no value.
   *
   * @param <T> the value's type
   * @return the attribute
   */
  public static <T> Attribute<T> absent() {
    return new Attribute<>(State.ABSENT, null, null);
  }

  /**
   * The source could not answer.
   *
   * @param why what went wrong, which travels into the refusal so an operator sees the cause and
   *     not just the effect
   * @param <T> the value's type
   * @return the attribute
   */
  public static <T> Attribute<T> unknown(String why) {
    return new Attribute<>(
        State.UNKNOWN, null, Objects.requireNonNull(why, "a reason is required"));
  }

  /**
   * Reads all three states at once.
   *
   * <p>This is the accessor to reach for. It does not compile until the caller has decided what an
   * unresolvable attribute means for them, which is the decision {@code Optional} lets you skip.
   *
   * @param onKnown applied to the resolved value
   * @param onAbsent called when the source said there is none
   * @param onUnknown applied to the reason the source could not answer
   * @param <R> the result type
   * @return whichever branch applies
   */
  public <R> R fold(Function<T, R> onKnown, Supplier<R> onAbsent, Function<String, R> onUnknown) {
    switch (state) {
      case KNOWN:
        return onKnown.apply(value);
      case ABSENT:
        return onAbsent.get();
      default:
        return onUnknown.apply(reason);
    }
  }

  /**
   * Which state this is.
   *
   * @return the state
   */
  public State getState() {
    return state;
  }

  /**
   * The value, if the source answered with one.
   *
   * <p>This DOES collapse absent and unknown into an empty {@code Optional}, so it is for
   * inspection — logging, a debug view — and not for deciding what to send. Nothing built on it can
   * distinguish "there is no department" from "the directory was down"; {@link #fold} can.
   *
   * @return the value when known, empty otherwise
   */
  public Optional<T> asKnown() {
    return Optional.ofNullable(value);
  }

  /**
   * Why the source could not answer.
   *
   * @return the reason when unknown, empty otherwise
   */
  public Optional<String> getReason() {
    return Optional.ofNullable(reason);
  }

  /**
   * Whether the source answered with a value.
   *
   * @return true when known
   */
  public boolean isKnown() {
    return state == State.KNOWN;
  }

  /**
   * Whether the source answered that there is no value.
   *
   * @return true when absent
   */
  public boolean isAbsent() {
    return state == State.ABSENT;
  }

  /**
   * Whether the source could not answer.
   *
   * @return true when unknown
   */
  public boolean isUnknown() {
    return state == State.UNKNOWN;
  }

  /**
   * Applies {@code f} to a known value, leaving the other two states alone.
   *
   * @param f the mapping
   * @param <U> the mapped type
   * @return the mapped attribute
   */
  public <U> Attribute<U> map(Function<T, U> f) {
    if (state != State.KNOWN) {
      return new Attribute<>(state, null, reason);
    }
    U mapped = f.apply(value);
    if (mapped == null) {
      // A mapping that returns null is saying "there is no value" in the one
      // vocabulary this type refuses to accept it in. Reading it as ABSENT
      // would silently convert a resolved value into a resolved absence; the
      // caller has to say which they meant.
      throw new NullPointerException(
          "Attribute.map returned null; return Attribute.absent() or Attribute.unknown(why) "
              + "explicitly rather than mapping a known value to nothing");
    }
    return Attribute.known(mapped);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Attribute)) {
      return false;
    }
    Attribute<?> that = (Attribute<?>) other;
    return state == that.state
        && Objects.equals(value, that.value)
        && Objects.equals(reason, that.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, value, reason);
  }

  @Override
  public String toString() {
    switch (state) {
      case KNOWN:
        return "known(" + value + ")";
      case ABSENT:
        return "absent";
      default:
        return "unknown(" + reason + ")";
    }
  }
}
