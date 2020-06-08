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
   * Returns a new {@link Context} forked from the {@linkplain Context#current()} current context}
   * with the {@link Span} set.
   */
  public static Context currentContextWith(final Span clientSpan) {
    Context context = Context.current();
    if (clientSpan.getContext().isValid()) {
      context = context.withValue(CONTEXT_CLIENT_SPAN_KEY, clientSpan);
    }
    return TracingContextUtils.withSpan(clientSpan, context);
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
