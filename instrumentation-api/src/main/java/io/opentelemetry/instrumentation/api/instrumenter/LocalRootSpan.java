/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/**
 * A local root span is a span that either does not have a parent span (it is the root span of a
 * trace), or its parent span is a remote span (context was propagated from another application).
 */
public final class LocalRootSpan {

  private static final ContextKey<Span> KEY =
      ContextKey.named("opentelemetry-traces-local-root-span");

  /**
   * Returns the local root span from the current context or {@linkplain Span#getInvalid() invalid
   * span} if there is no local root span.
   */
  public static Span current() {
    return fromContext(Context.current());
  }

  /**
   * Returns the local root span from the given context or {@linkplain Span#getInvalid() invalid
   * span} if there is no local root span in the context.
   */
  public static Span fromContext(Context context) {
    Span span = fromContextOrNull(context);
    return span == null ? Span.getInvalid() : span;
  }

  /**
   * Returns the local root span from the given context or {@code null} if there is no local root
   * span in the context.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  static boolean isLocalRoot(Context parentContext) {
    SpanContext spanContext = Span.fromContext(parentContext).getSpanContext();
    return !spanContext.isValid() || spanContext.isRemote();
  }

  static Context store(Context context, Span span) {
    return context.with(KEY, span);
  }
}
