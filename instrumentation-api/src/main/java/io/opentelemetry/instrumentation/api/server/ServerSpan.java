/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import javax.annotation.Nullable;

/**
 * This class encapsulates the context key for storing the current {@link SpanKind#SERVER} span in
 * the {@link Context}.
 */
public final class ServerSpan {

  /**
   * Returns span of type {@link SpanKind#SERVER} from the given context or {@code null} if not
   * found.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return SpanKey.SERVER.fromContextOrNull(context);
  }

  private ServerSpan() {}
}
