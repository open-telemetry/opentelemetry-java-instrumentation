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

final class ClientSpan {
  // Keeps track of the client span in a subtree corresponding to a client request.
  private static final ContextKey<Span> KEY =
      ContextKey.named("opentelemetry-traces-client-span-key");

  /**
   * Returns span of type {@link SpanKind#CLIENT} from the given context or {@code null} if not
   * found.
   */
  @Nullable
  static Span fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  static Context with(Context context, Span clientSpan) {
    return context.with(KEY, clientSpan);
  }

  private ClientSpan() {}
}
