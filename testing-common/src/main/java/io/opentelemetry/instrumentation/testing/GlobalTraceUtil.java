/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;

/**
 * Can be useful when you need to start a span {@link
 * io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension} is not available.
 */
public final class GlobalTraceUtil {

  private static final TestInstrumenters testInstrumenters =
      new TestInstrumenters(GlobalOpenTelemetry.get());

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public static <E extends Exception> void runWithSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public static <T, E extends Throwable> T runWithSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithSpan(spanName, callback);
  }

  private GlobalTraceUtil() {}
}
