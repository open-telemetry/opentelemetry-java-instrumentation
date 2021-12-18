/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import org.awaitility.core.ConditionTimeoutException;

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

  List<LogData> getExportedLogs();

  boolean forceFlushCalled();

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  default List<List<SpanData>> traces() {
    return TelemetryDataUtil.groupTraces(getExportedSpans());
  }

  default List<List<SpanData>> waitForTraces(int numberOfTraces) {
    try {
      return TelemetryDataUtil.waitForTraces(
          this::getExportedSpans, numberOfTraces, 20, TimeUnit.SECONDS);
    } catch (TimeoutException | InterruptedException e) {
      throw new AssertionError("Error waiting for " + numberOfTraces + " traces", e);
    }
  }

  default void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    try {
      await()
          .untilAsserted(
              () -> {
                List<List<SpanData>> traces = waitForTraces(assertions.length);
                TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(assertions);
              });
    } catch (ConditionTimeoutException e) {
      // Don't throw this failure since the stack is the awaitility thread, causing confusion.
      // Instead, just assert one more time on the test thread, which will fail with a better stack
      // trace.
      // TODO(anuraaga): There is probably a better way to do this.
      List<List<SpanData>> traces = waitForTraces(assertions.length);
      TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(assertions);
    }
  }

  default void waitAndAssertTraces(Iterable<? extends Consumer<TraceAssert>> assertions) {
    waitAndAssertTraces(
        StreamSupport.stream(assertions.spliterator(), false).toArray(Consumer[]::new));
  }

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

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback) throws E;
}
