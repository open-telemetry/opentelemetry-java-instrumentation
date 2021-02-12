/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class InstrumentationExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
  private static final long DEFAULT_TRACE_WAIT_TIMEOUT_SECONDS = 20;

  private final InstrumentationTestRunner testRunner;

  protected InstrumentationExtension(InstrumentationTestRunner testRunner) {
    this.testRunner = testRunner;
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    testRunner.beforeTestClass();
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    testRunner.clearAllExportedData();
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    testRunner.beforeTestClass();
  }

  /** Return a list of all captured spans. */
  public List<SpanData> spans() {
    return testRunner.getExportedSpans();
  }

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  public List<List<SpanData>> traces() {
    return TelemetryDataUtil.groupTraces(spans());
  }

  /** Return a list of all captured metrics. */
  public List<MetricData> metrics() {
    return testRunner.getExportedMetrics();
  }

  /**
   * Removes all captured telemetry data. After calling this method {@link #spans()}, {@link
   * #traces()} and {@link #metrics()} will return empty lists until more telemetry data is
   * captured.
   */
  public void clearData() {
    testRunner.clearAllExportedData();
  }

  /**
   * Wait until at least {@code numberOfTraces} traces are completed and return all captured traces.
   * Note that there may be more than {@code numberOfTraces} collected. By default this waits up to
   * 20 seconds, then times out.
   *
   * @throws TimeoutException when the operation times out
   * @throws InterruptedException when the current thread is interrupted
   */
  public List<List<SpanData>> waitForTraces(int numberOfTraces)
      throws TimeoutException, InterruptedException {
    return waitForTraces(numberOfTraces, DEFAULT_TRACE_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Wait until at least {@code numberOfTraces} traces are completed and return all captured traces.
   * Note that there may be more than {@code numberOfTraces} collected.
   *
   * @throws TimeoutException when the operation times out
   * @throws InterruptedException when the current thread is interrupted
   */
  public List<List<SpanData>> waitForTraces(int numberOfTraces, long timeout, TimeUnit unit)
      throws TimeoutException, InterruptedException {
    return TelemetryDataUtil.waitForTraces(this::spans, numberOfTraces, timeout, unit);
  }
}
