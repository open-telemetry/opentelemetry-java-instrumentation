/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LocalRootServerSpan {

  private static final ContextKey<Span> KEY =
      ContextKey.named("opentelemetry-traces-local-root-server-span");

  /**
   * Returns the local root server span from the given context or {@code null} if there is no local
   * root server span in the context.
   */
  @Nullable
  public static Span fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  public static Context store(Context context, Span span) {
    return context.with(KEY, span);
  }

  private LocalRootServerSpan() {}
}
