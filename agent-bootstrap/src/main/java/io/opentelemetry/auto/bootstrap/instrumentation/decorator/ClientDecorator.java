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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;

public abstract class ClientDecorator extends BaseDecorator {

  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  static final Context.Key<Span> CONTEXT_CLIENT_SPAN_KEY =
      Context.key("opentelemetry-trace-auto-client-span-key");

  /**
   * Creates a {@link Context} with the provided {@link Span} and sets it as the current {@link
   * Context}
   *
   * @return a {@link Scope} that will restore the previous context. All callers of this method must
   *     also call {@link Scope#close()} when this next context is no longer needed.
   */
  public static Scope currentContextWith(final Span clientSpan) {
    return ContextUtils.withScopedContext(withSpan(clientSpan, Context.current()));
  }

  /** Returns a new {@link Context} forked from {@code context} with the {@link Span} set. */
  public static Context withSpan(final Span clientSpan, final Context context) {
    if (!clientSpan.getContext().isValid()) {
      return TracingContextUtils.withSpan(clientSpan, context);
    }
    return TracingContextUtils.withSpan(
        clientSpan, context.withValue(CONTEXT_CLIENT_SPAN_KEY, clientSpan));
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * Context}, or an invalid {@link Span} otherwise.
   */
  public static Span getOrCreateSpan(String name, Tracer tracer) {
    final Context context = Context.current();
    final Span clientSpan = CONTEXT_CLIENT_SPAN_KEY.get(context);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    final Span current = TracingContextUtils.getSpan(context);
    return tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(current).startSpan();
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    return super.afterStart(span);
  }
}
