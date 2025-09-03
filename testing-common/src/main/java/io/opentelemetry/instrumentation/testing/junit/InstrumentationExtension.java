/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import io.opentelemetry.instrumentation.testing.util.ContextStorageCloser;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.LogRecordDataAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class InstrumentationExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {

  private final InstrumentationTestRunner testRunner;

  protected InstrumentationExtension(InstrumentationTestRunner testRunner) {
    this.testRunner = testRunner;
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    testRunner.beforeTestClass();
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    testRunner.clearAllExportedData();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    ContextStorage storage = ContextStorage.get();
    ContextStorageCloser.close(storage);
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    testRunner.clearAllExportedData();
    testRunner.afterTestClass();
  }

  /** Return the {@link OpenTelemetry} instance used to produce telemetry data. */
  public OpenTelemetry getOpenTelemetry() {
    return testRunner.getOpenTelemetry();
  }

  /** Return a list of all captured spans. */
  public List<SpanData> spans() {
    return testRunner.getExportedSpans();
  }

  /** Return a list of all captured metrics. */
  public List<MetricData> metrics() {
    return testRunner.getExportedMetrics();
  }

  /** Return a list of all captured logs. */
  public List<LogRecordData> logRecords() {
    return testRunner.getExportedLogRecords();
  }

  /**
   * Waits for the assertion applied to all metrics of the given instrumentation and metric name to
   * pass.
   */
  public final void waitAndAssertMetrics(
      String instrumentationName, String metricName, Consumer<ListAssert<MetricData>> assertion) {
    testRunner.waitAndAssertMetrics(instrumentationName, metricName, assertion);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertMetrics(
      String instrumentationName, Consumer<MetricAssert>... assertions) {
    testRunner.waitAndAssertMetrics(instrumentationName, assertions);
  }

  /**
   * Removes all captured telemetry data. After calling this method {@link #spans()} and {@link
   * #metrics()} will return empty lists until more telemetry data is captured.
   */
  public void clearData() {
    testRunner.clearAllExportedData();
  }

  /**
   * Wait until at least {@code numberOfTraces} traces are completed and return all captured traces.
   * Note that there may be more than {@code numberOfTraces} collected. This waits up to 20 seconds,
   * then times out.
   */
  public List<List<SpanData>> waitForTraces(int numberOfTraces) {
    return testRunner.waitForTraces(numberOfTraces);
  }

  /**
   * Wait until at least {@code numberOfLogRecords} log records are completed and return all
   * captured log records. Note that there may be more than {@code numberOfLogRecords} collected.
   * This waits up to 20 seconds, then times out.
   */
  public List<LogRecordData> waitForLogRecords(int numberOfLogRecords) {
    return testRunner.waitForLogRecords(numberOfLogRecords);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator, Consumer<TraceAssert>... assertions) {
    testRunner.waitAndAssertSortedTraces(traceComparator, assertions);
  }

  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator,
      Iterable<? extends Consumer<TraceAssert>> assertions) {
    testRunner.waitAndAssertSortedTraces(traceComparator, assertions);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTracesWithoutScopeVersionVerification(
      Consumer<TraceAssert>... assertions) {
    testRunner.waitAndAssertTracesWithoutScopeVersionVerification(assertions);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    testRunner.waitAndAssertTraces(assertions);
  }

  public final void waitAndAssertTraces(Iterable<? extends Consumer<TraceAssert>> assertions) {
    testRunner.waitAndAssertTraces(assertions);
  }

  private void doWaitAndAssertLogRecords(List<Consumer<LogRecordDataAssert>> assertions) {
    List<LogRecordData> logRecordDataList = waitForLogRecords(assertions.size());
    Iterator<Consumer<LogRecordDataAssert>> assertionIterator = assertions.iterator();
    for (LogRecordData logRecordData : logRecordDataList) {
      assertionIterator.next().accept(assertThat(logRecordData));
    }
  }

  public final void waitAndAssertLogRecords(
      Iterable<? extends Consumer<LogRecordDataAssert>> assertions) {
    List<Consumer<LogRecordDataAssert>> assertionsList = new ArrayList<>();
    assertions.forEach(assertionsList::add);
    doWaitAndAssertLogRecords(assertionsList);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertLogRecords(Consumer<LogRecordDataAssert>... assertions) {
    doWaitAndAssertLogRecords(Arrays.asList(assertions));
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public <E extends Exception> void runWithSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
    testRunner.runWithSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return testRunner.runWithSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithHttpClientSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    testRunner.runWithHttpClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithHttpClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testRunner.runWithHttpClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithHttpServerSpan(ThrowingRunnable<E> callback) throws E {
    testRunner.runWithHttpServerSpan(callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithHttpServerSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return testRunner.runWithHttpServerSpan(callback);
  }

  /** Returns whether forceFlush was called. */
  public boolean forceFlushCalled() {
    return testRunner.forceFlushCalled();
  }

  /** Returns the {@link OpenTelemetrySdk} initialized for library tests. */
  public OpenTelemetrySdk getOpenTelemetrySdk() {
    if (testRunner instanceof LibraryTestRunner) {
      return ((LibraryTestRunner) testRunner).getOpenTelemetrySdk();
    }
    throw new IllegalStateException("Can only be called from library instrumentation tests.");
  }

  protected InstrumentationTestRunner getTestRunner() {
    return testRunner;
  }
}
