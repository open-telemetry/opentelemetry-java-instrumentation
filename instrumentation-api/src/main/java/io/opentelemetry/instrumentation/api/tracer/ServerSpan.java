/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class encapsulates the context key for storing the current {@link SpanKind#SERVER} span in
 * the {@link Context}.
 */
public final class ServerSpan {
  // Keeps track of the server span for the current trace.
  private static final ContextKey<Span> KEY =
      ContextKey.named("opentelemetry-traces-server-span-key");

  /**
   * Returns span of type {@link SpanKind#SERVER} from the given context or {@code null} if not
   * found.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  static Context with(Context context, Span serverSpan) {
    return context.with(KEY, serverSpan);
  }

  private ServerSpan() {}
}
