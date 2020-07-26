/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.auto.instrumentation.api;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

/**
 * This is deprecated.
 *
 * <p>Originally, we used {@code SpanWithScope} to pass the {@link Span} and {@link Scope} between
 * {@code @Advice.OnMethodEnter} and {@code @Advice.OnMethodExit}, e.g.
 *
 * <pre>
 *   &#64;Advice.OnMethodEnter(...)
 *   public static CallDepth onEnter(
 *       ...
 *       &#64;Advice.Local("otelSpan") Span span,
 *       &#64;Advice.Local("otelScope") Scope scope) {
 *     ...
 *     span = ...
 *     scope = ...
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
 * But we are (slowly) migrating to a new style using {@code @Advice.Local} that doesn't require
 * {@code SpanWithScope} anymore, e.g.
 *
 * <pre>
 *   &#64;Advice.OnMethodEnter(...)
 *   public static CallDepth onEnter(
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
 * @deprecated
 */
@Deprecated
public class SpanWithScope {
  private final Span span;
  private final Scope scope;

  public SpanWithScope(final Span span, final Scope scope) {
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
