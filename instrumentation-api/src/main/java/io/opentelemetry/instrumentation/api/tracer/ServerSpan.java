/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKey;
import javax.annotation.Nullable;

/**
 * This class encapsulates the context key for storing the current {@link SpanKind#SERVER} span in
 * the {@link Context}.
 */
public final class ServerSpan {

  /**
   * Returns true when a {@link SpanKind#SERVER} span is present in the passed {@code context}.
   *
   * @deprecated This method should not be used directly; it's functionality is encapsulated inside
   *     the {@linkplain io.opentelemetry.instrumentation.api.instrumenter.Instrumenter Instrumenter
   *     API}.
   */
  @Deprecated
  public static boolean exists(Context context) {
    return fromContextOrNull(context) != null;
  }

  /**
   * Returns span of type {@link SpanKind#SERVER} from the given context or {@code null} if not
   * found.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return SpanKey.SERVER.fromContextOrNull(context);
  }

  /**
   * Marks the span as the server span in the passed context.
   *
   * @deprecated This method should not be used directly; it's functionality is encapsulated inside
   *     the {@linkplain io.opentelemetry.instrumentation.api.instrumenter.Instrumenter Instrumenter
   *     API}.
   */
  @Deprecated
  public static Context with(Context context, Span serverSpan) {
    return SpanKey.SERVER.storeInContext(context, serverSpan);
  }

  private ServerSpan() {}
}
