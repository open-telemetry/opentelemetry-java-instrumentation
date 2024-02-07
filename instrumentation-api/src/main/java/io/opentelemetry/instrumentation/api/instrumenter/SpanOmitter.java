package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

/**
 * Allows providing custom filters for the creation of {@link Span}s in an {@link Instrumenter}.
 */
public interface SpanOmitter {
  /**
   * Called right before creating a {@link Span}.
   * @param context The parent context of the {@link Span} that's about to get created.
   * @return TRUE to avoid creating a {@link Span}, FALSE to proceed with the {@link Span} creation.
   */
  boolean shouldOmit(Context context);
}
