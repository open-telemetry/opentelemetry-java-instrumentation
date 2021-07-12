/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;

/**
 * This interface defines a common set of operations for interaction with OpenTelemetry SDK and
 * traces & metrics exporters.
 *
 * @see LibraryTestRunner
 * @see AgentTestRunner
 */
public interface InstrumentationTestRunner {
  void beforeTestClass();

  void afterTestClass();

  void clearAllExportedData();

  OpenTelemetry getOpenTelemetry();

  List<SpanData> getExportedSpans();

  List<MetricData> getExportedMetrics();

  boolean forceFlushCalled();

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  default <E extends Exception> void runWithSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
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
  <T, E extends Throwable> T runWithSpan(String spanName, ThrowingSupplier<T, E> callback) throws E;

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  default <E extends Throwable> void runWithClientSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithClientSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithClientSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E;

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  default <E extends Throwable> void runWithServerSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithServerSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithServerSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E;
}
