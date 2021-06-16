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
 * This class encapsulates the context key for storing the current {@link SpanKind#CLIENT} span in
 * the {@link Context}.
 */
public final class ClientSpan {
  // Keeps track of the client span in a subtree corresponding to a client request.
  private static final ContextKey<Span> KEY =
      ContextKey.named("opentelemetry-traces-client-span-key");

  /** Returns true when a {@link SpanKind#CLIENT} span is present in the passed {@code context}. */
  public static boolean exists(Context context) {
    return fromContextOrNull(context) != null;
  }

  /**
   * Returns span of type {@link SpanKind#CLIENT} from the given context or {@code null} if not
   * found.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  public static Context with(Context context, Span clientSpan) {
    return context.with(KEY, clientSpan);
  }

  private ClientSpan() {}
}
