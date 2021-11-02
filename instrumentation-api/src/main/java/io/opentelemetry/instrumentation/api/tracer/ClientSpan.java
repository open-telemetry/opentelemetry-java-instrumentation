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
 * This class encapsulates the context key for storing the current {@link SpanKind#CLIENT} span in
 * the {@link Context}.
 */
public final class ClientSpan {

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
    return SpanKey.ALL_CLIENTS.fromContextOrNull(context);
  }

  public static Context with(Context context, Span clientSpan) {
    return SpanKey.ALL_CLIENTS.storeInContext(context, clientSpan);
  }

  private ClientSpan() {}
}
