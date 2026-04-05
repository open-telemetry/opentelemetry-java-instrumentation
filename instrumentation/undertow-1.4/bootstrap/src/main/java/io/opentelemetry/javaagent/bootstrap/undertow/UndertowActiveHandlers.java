/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.undertow;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicInteger;

/** Helper container for keeping track of request processing state in undertow. */
public final class UndertowActiveHandlers {
  private static final ContextKey<AtomicInteger> CONTEXT_KEY =
      ContextKey.named("opentelemetry-undertow-active-handlers");

  private UndertowActiveHandlers() {}

  /**
   * Attach to context.
   *
   * @param context server context
   * @param initialValue initial value for counter
   * @return new context
   */
  public static Context init(Context context, int initialValue) {
    return context.with(CONTEXT_KEY, new AtomicInteger(initialValue));
  }

  /**
   * Increment counter. No-op if the counter was never initialized via {@link #init}.
   *
   * @param context server context
   */
  public static void increment(Context context) {
    AtomicInteger counter = context.get(CONTEXT_KEY);
    if (counter != null) {
      counter.incrementAndGet();
    }
  }

  /**
   * Decrement counter.
   *
   * <p>Returns {@code 1} if the counter was never initialized via {@link #init}, so callers that
   * check {@code decrementAndGet() == 0} to end a span will safely no-op.
   *
   * @param context server context
   * @return value of counter after decrementing it, or {@code 1} if counter is absent
   */
  public static int decrementAndGet(Context context) {
    AtomicInteger counter = context.get(CONTEXT_KEY);
    if (counter == null) {
      return 1;
    }
    return counter.decrementAndGet();
  }
}
