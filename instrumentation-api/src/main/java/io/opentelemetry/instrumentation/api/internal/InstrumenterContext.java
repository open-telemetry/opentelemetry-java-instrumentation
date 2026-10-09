/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Helper class for sharing computed values between different {@link AttributesExtractor}s and
 * {@link SpanNameExtractor} called in the start phase of the {@link Instrumenter}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class InstrumenterContext {
  private static final ScopedThreadValue<InstrumenterContext> instrumenterContext =
      new ScopedThreadValue<>();

  private final Map<String, Object> map = new HashMap<>();

  private InstrumenterContext() {}

  @SuppressWarnings("unchecked") // we expect the caller to use the same type for a given key
  public static <T> T computeIfAbsent(String key, Function<String, T> function) {
    return (T) get().computeIfAbsent(key, function);
  }

  // visible for testing
  static Map<String, Object> get() {
    InstrumenterContext context = instrumenterContext.get();
    if (context == null) {
      context = new InstrumenterContext();
      instrumenterContext.set(context);
    }
    return context.map;
  }

  /** Starts an isolated, lazily allocated cache scope and returns the previous cache. */
  @Nullable
  public static InstrumenterContext enter() {
    return instrumenterContext.set(null);
  }

  /** Restores the cache returned by the matching call to {@link #enter()}. */
  public static void restore(@Nullable InstrumenterContext previous) {
    instrumenterContext.restore(previous);
  }

  public static void reset() {
    instrumenterContext.restore(null);
  }
}
