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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builders for the two envelope shapes.
 *
 * <p>They exist for one reason the generated types cannot serve: {@code context.args.query} and
 * {@code context.correlation.<key>} are LEAVES two levels inside an attribute bag, and assembling
 * that nesting by hand at every call site is both tedious and the kind of thing that gets it wrong
 * in one place out of five.
 *
 * <p>Both take {@link Attribute} rather than a plain {@code String}, because a gateway does not
 * always have the value in hand: a request whose body failed to decode has a query nobody could
 * read, and a batch job has no session id to correlate with. Those are different facts, and this is
 * where the caller says which.
 *
 * <pre>{@code
 * AuthZENRequest request =
 *     AuthZENEvaluation.of(
 *             new AuthZENSubject("gateway", "llm-gateway-01"),
 *             new AuthZENAction("llm.completion"),
 *             new AuthZENResource("llm", "llm"))
 *         .query(Attribute.known(userPrompt))
 *         .correlation("x-session-id", Attribute.known(sessionId))
 *         .build();
 * }</pre>
 */
public final class AuthZENEvaluation {

  /** The context member carrying what the policy engine inspects. */
  public static final String CONTEXT_ARGS = "args";

  /** The member under {@code args} carrying the content itself. */
  public static final String ARGS_QUERY = "query";

  /** The context member carrying audit correlation keys. */
  public static final String CONTEXT_CORRELATION = "correlation";

  private AuthZENEvaluation() {}

  /**
   * One subject performing one action on one resource.
   *
   * @param subject who is asking
   * @param action what they are asking to do
   * @param resource what they are asking to do it to
   * @return a builder
   */
  public static SingleBuilder of(
      AuthZENSubject subject, AuthZENAction action, AuthZENResource resource) {
    return new SingleBuilder(subject, action, resource);
  }

  /**
   * Several preconditions of ONE operation.
   *
   * @param evaluations the entries, each inheriting any member it omits from the shared base
   * @return a builder
   */
  public static BulkBuilder over(List<AuthZENRequest> evaluations) {
    return new BulkBuilder(new ArrayList<>(evaluations));
  }

  /**
   * Several preconditions of ONE operation.
   *
   * @param evaluations the entries
   * @return a builder
   */
  public static BulkBuilder over(AuthZENRequest... evaluations) {
    return over(Arrays.asList(evaluations));
  }

  /**
   * Writes {@code context.args.query}, creating the nested bag when it is not there.
   *
   * <p>If {@code context.args} OR {@code context.args.query} already holds an UNRESOLVED attribute,
   * the write is DECLINED and the unknown stays. The rule applies at BOTH levels: guarding only the
   * parent left the defect reachable one level down, which is where a caller would actually hit it. Overwriting it would be the fail-open this whole surface exists to prevent,
   * arriving through its own builder: a caller that had recorded "nobody could read the request
   * body" and then wrote a recovered partial query over it would have produced a complete-looking
   * envelope, passed validation, and been handed a verdict that named every attribute it weighed.
   * Leaving the unknown in place means the envelope is refused at that member and never sent.
   */
  private static void writeQuery(AttributeMap context, Attribute<String> query) {
    context
        .nestedForWrite(CONTEXT_ARGS)
        .ifPresent(args -> args.record(ARGS_QUERY, query.map(AttributeValue::of)));
  }

  /**
   * Writes one {@code context.correlation.<key>}, creating the nested bag when needed.
   *
   * <p>Declines the write over an unresolved {@code context.correlation}, for the reason in {@link
   * #writeQuery}.
   */
  private static void writeCorrelation(AttributeMap context, String key, Attribute<String> value) {
    context
        .nestedForWrite(CONTEXT_CORRELATION)
        .ifPresent(correlation -> correlation.record(key, value.map(AttributeValue::of)));
  }

  /** Builds the singular envelope member. */
  public static final class SingleBuilder {

    private final AuthZENRequest request;

    private SingleBuilder(AuthZENSubject subject, AuthZENAction action, AuthZENResource resource) {
      this.request =
          new AuthZENRequest().setSubject(subject).setAction(action).setResource(resource);
    }

    /**
     * The content the policy engine inspects, at {@code context.args.query}.
     *
     * @param query the content, or why it could not be read
     * @return this, for chaining
     */
    public SingleBuilder query(Attribute<String> query) {
      writeQuery(request.getContext(), query);
      return this;
    }

    /**
     * One audit correlation key, at {@code context.correlation.<key>}.
     *
     * <p>The deployment records an allowlisted, capped set of these; a key it does not record is
     * refused by name rather than dropped, because telling a caller a key was captured when it was
     * not is the same lie in both directions.
     *
     * @param key the correlation key
     * @param value the value, or why it could not be resolved
     * @return this, for chaining
     */
    public SingleBuilder correlation(String key, Attribute<String> value) {
      writeCorrelation(request.getContext(), key, value);
      return this;
    }

    /**
     * The assembled request.
     *
     * @return the request
     */
    public AuthZENRequest build() {
      return request;
    }
  }

  /** Builds the plural envelope member. */
  public static final class BulkBuilder {

    private final AuthZENBulk bulk;

    private BulkBuilder(List<AuthZENRequest> evaluations) {
      this.bulk = new AuthZENBulk(evaluations);
    }

    /**
     * The subject every entry inherits unless it names its own.
     *
     * @param subject the shared subject
     * @return this, for chaining
     */
    public BulkBuilder subject(AuthZENSubject subject) {
      bulk.setSubject(subject);
      return this;
    }

    /**
     * The action every entry inherits unless it names its own.
     *
     * @param action the shared action
     * @return this, for chaining
     */
    public BulkBuilder action(AuthZENAction action) {
      bulk.setAction(action);
      return this;
    }

    /**
     * The resource every entry inherits unless it names its own.
     *
     * @param resource the shared resource
     * @return this, for chaining
     */
    public BulkBuilder resource(AuthZENResource resource) {
      bulk.setResource(resource);
      return this;
    }

    /**
     * The shared {@code context.args.query} every entry inherits.
     *
     * @param query the content, or why it could not be read
     * @return this, for chaining
     */
    public BulkBuilder query(Attribute<String> query) {
      writeQuery(bulk.getContext(), query);
      return this;
    }

    /**
     * A shared audit correlation key.
     *
     * @param key the correlation key
     * @param value the value, or why it could not be resolved
     * @return this, for chaining
     */
    public BulkBuilder correlation(String key, Attribute<String> value) {
      writeCorrelation(bulk.getContext(), key, value);
      return this;
    }

    /**
     * The assembled bulk.
     *
     * @return the bulk
     */
    public AuthZENBulk build() {
      return bulk;
    }
  }
}
