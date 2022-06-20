/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * A helper class that allow setting a processing function that will be applied to the spans created
 * while the context with processing function is active.
 */
public final class ContextSpanProcessorUtil {
  private static final ContextKey<BiConsumer<Context, Span>> KEY =
      ContextKey.named("opentelemetry-context-span-processor");

  private ContextSpanProcessorUtil() {}

  /** Returns currently set span processor function from context or null. */
  // instrumented by ContextSpanProcessorUtilInstrumentation
  @Nullable
  public static BiConsumer<Context, Span> fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  /**
   * Store a span processor function in context. This processor will be applied to all spans created
   * while the returned context is active. Processing function is called from {@code
   * io.opentelemetry.sdk.trace.SpanProcessor#onStart} and should adhere to the same rules as the
   * onStart method.
   */
  // instrumented by ContextSpanProcessorUtilInstrumentation
  public static Context storeInContext(Context context, BiConsumer<Context, Span> processor) {
    return context.with(KEY, processor);
  }
}
