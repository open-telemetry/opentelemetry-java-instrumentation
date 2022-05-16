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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.awaitility.core.ConditionTimeoutException;

/**
 * This interface defines a common set of operations for interaction with OpenTelemetry SDK and
 * traces & metrics exporters.
 *
 * @see LibraryTestRunner
 * @see AgentTestRunner
 */
public abstract class InstrumentationTestRunner {

  private final TestInstrumenters testInstrumenters;

  protected InstrumentationTestRunner(OpenTelemetry openTelemetry) {
    testInstrumenters = new TestInstrumenters(openTelemetry);
  }

  public abstract void beforeTestClass();

  public abstract void afterTestClass();

  public abstract void clearAllExportedData();

  public abstract OpenTelemetry getOpenTelemetry();

  public abstract List<SpanData> getExportedSpans();

  public abstract List<MetricData> getExportedMetrics();

  public abstract List<LogData> getExportedLogs();

  public abstract boolean forceFlushCalled();

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  public final List<List<SpanData>> traces() {
    return TelemetryDataUtil.groupTraces(getExportedSpans());
  }

  public final List<List<SpanData>> waitForTraces(int numberOfTraces) {
    try {
      return TelemetryDataUtil.waitForTraces(
          this::getExportedSpans, numberOfTraces, 20, TimeUnit.SECONDS);
    } catch (TimeoutException | InterruptedException e) {
      throw new AssertionError("Error waiting for " + numberOfTraces + " traces", e);
    }
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator, Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(traceComparator, Arrays.asList(assertions));
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(Arrays.asList(assertions));
  }

  public final <T extends Consumer<TraceAssert>> void waitAndAssertTraces(Iterable<T> assertions) {
    waitAndAssertTraces(null, assertions);
  }

  private <T extends Consumer<TraceAssert>> void waitAndAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator, Iterable<T> assertions) {
    List<T> assertionsList = new ArrayList<>();
    assertions.forEach(assertionsList::add);

    try {
      await().untilAsserted(() -> doAssertTraces(traceComparator, assertionsList));
    } catch (ConditionTimeoutException e) {
      // Don't throw this failure since the stack is the awaitility thread, causing confusion.
      // Instead, just assert one more time on the test thread, which will fail with a better stack
      // trace.
      // TODO(anuraaga): There is probably a better way to do this.
      doAssertTraces(traceComparator, assertionsList);
    }
  }

  private <T extends Consumer<TraceAssert>> void doAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator, List<T> assertionsList) {
    List<List<SpanData>> traces = waitForTraces(assertionsList.size());
    if (traceComparator != null) {
      traces.sort(traceComparator);
    }
    TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(assertionsList);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public final <E extends Exception> void runWithSpan(String spanName, ThrowingRunnable<E> callback)
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
  public final <T, E extends Throwable> T runWithSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public final <E extends Throwable> void runWithHttpClientSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithHttpClientSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public final <T, E extends Throwable> T runWithHttpClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithHttpClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP SERVER span with name {@code
   * spanName}.
   */
  public final <E extends Throwable> void runWithHttpServerSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithHttpServerSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP SERVER span with name {@code
   * spanName}.
   */
  public final <T, E extends Throwable> T runWithHttpServerSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithHttpServerSpan(spanName, callback);
  }

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  public final <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return testInstrumenters.runWithNonRecordingSpan(callback);
  }
}
