/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

@Deprecated
public abstract class ClientDecorator extends BaseDecorator {

  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  static final ContextKey<Span> CONTEXT_CLIENT_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-auto-client-span-key");

  /**
   * Returns a new {@link Context} forked from the {@linkplain Context#current()} current context}
   * with the {@link Span} set.
   */
  public static Context currentContextWith(Span clientSpan) {
    Context context = Context.current();
    if (clientSpan.getSpanContext().isValid()) {
      context = context.with(CONTEXT_CLIENT_SPAN_KEY, clientSpan);
    }
    return context.with(clientSpan);
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * Context}, or an invalid {@link Span} otherwise.
   */
  public static Span getOrCreateSpan(String name, Tracer tracer) {
    Context context = Context.current();
    Span clientSpan = context.get(CONTEXT_CLIENT_SPAN_KEY);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return Span.getInvalid();
    }

    return tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(context).startSpan();
  }

  @Override
  public Span afterStart(Span span) {
    assert span != null;
    return super.afterStart(span);
  }
}
