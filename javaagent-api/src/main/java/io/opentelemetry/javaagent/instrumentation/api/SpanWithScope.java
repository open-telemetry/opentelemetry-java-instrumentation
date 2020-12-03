/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * This is deprecated.
 *
 * <p>Originally, we used {@code SpanWithScope} to pass the {@link Span} and {@link Scope} between
 * {@code @Advice.OnMethodEnter} and {@code @Advice.OnMethodExit}, e.g.
 *
 * <pre>
 *   &#64;Advice.OnMethodEnter(...)
 *     public static SpanWithScope onEnter(...) {
 *     ...
 *     Span span = ...
 *     return new SpanWithScope(span, span.makeCurrent());
 *   }
 *
 *   &#64;Advice.OnMethodExit(...)
 *   public static void stopSpan(
 *       ...
 *       &#64;Advice.Enter final SpanWithScope spanWithScope) {
 *     Span span = spanWithScope.getSpan();
 *     ...
 *     span.end();
 *     spanWithScope.closeScope();
 *   }
 * </pre>
 *
 * <p>We are (slowly) migrating to a new pattern using `@Advice.Local`:
 *
 * <pre>
 *   &#64;Advice.OnMethodEnter(...)
 *   public static void onEnter(
 *       ...
 *       &#64;Advice.Local("otelSpan") Span span,
 *       &#64;Advice.Local("otelScope") Scope scope) {
 *     ...
 *     span = ...
 *     scope = ...
 *   }
 *
 *   &#64;Advice.OnMethodExit
 *   public static void onExit(
 *       ...
 *       &#64;Advice.Local("otelSpan") Span span,
 *       &#64;Advice.Local("otelScope") Scope scope) {
 *       ...
 *     span.end();
 *     scope.close();
 *   }
 * </pre>
 *
 * <p>This new pattern has the following benefits:
 *
 * <ul>
 *   <li>The new pattern is more efficient since it doesn't require instantiating the {@code
 *       SpanWithScope} holder object
 *   <li>The new pattern extends nicely in the common case where we also need to pass {@link
 *       CallDepth} between the methods
 * </ul>
 *
 * @deprecated see above
 */
@Deprecated
public class SpanWithScope {
  private final Span span;
  private final Scope scope;

  public SpanWithScope(Span span, Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public Span getSpan() {
    return span;
  }

  public void closeScope() {
    scope.close();
  }
}
