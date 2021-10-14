/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.util.ContextStorageCloser;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
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

  /**
   * Waits for the assertion applied to all metrics of the given instrumentation and metric name to
   * pass.
   */
  public void waitAndAssertMetrics(
      String instrumentationName, String metricName, Consumer<ListAssert<MetricData>> assertion) {
    await()
        .untilAsserted(
            () ->
                assertion.accept(
                    assertThat(metrics())
                        .filteredOn(
                            data ->
                                data.getInstrumentationLibraryInfo()
                                        .getName()
                                        .equals(instrumentationName)
                                    && data.getName().equals(metricName))));
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
   * Note that there may be more than {@code numberOfTraces} collected. By default this waits up to
   * 20 seconds, then times out.
   */
  public List<List<SpanData>> waitForTraces(int numberOfTraces) {
    return testRunner.waitForTraces(numberOfTraces);
  }

  @SafeVarargs
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    testRunner.waitAndAssertTraces(assertions);
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
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithClientSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
    testRunner.runWithClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testRunner.runWithClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithServerSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
    testRunner.runWithServerSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithServerSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testRunner.runWithServerSpan(spanName, callback);
  }

  protected InstrumentationTestRunner getTestRunner() {
    return testRunner;
  }
}
