/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner
import io.opentelemetry.instrumentation.testing.util.ContextStorageCloser
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier
import io.opentelemetry.sdk.logs.data.LogData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Rule
import org.junit.rules.Timeout
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * Base class for test specifications that are shared between instrumentation libraries and agent.
 * The methods in this class are implemented by {@link AgentTestTrait} and
 * {@link LibraryTestTrait}.
 */
abstract class InstrumentationSpecification extends Specification {
  abstract InstrumentationTestRunner testRunner()

  @Rule
  public Timeout testTimeout = new Timeout(10, TimeUnit.MINUTES)

  def setupSpec() {
    testRunner().beforeTestClass()
  }

  /**
   * Clears all data exported during a test.
   */
  def setup() {
    assert !Span.current().getSpanContext().isValid(): "Span is active before test has started: " + Span.current()
    testRunner().clearAllExportedData()
  }

  def cleanup() {
    ContextStorage storage = ContextStorage.get()
    ContextStorageCloser.close(storage)
  }

  def cleanupSpec() {
    testRunner().afterTestClass()
  }

  /** Return the {@link OpenTelemetry} instance used to produce telemetry data. */
  OpenTelemetry getOpenTelemetry() {
    testRunner().openTelemetry
  }

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  List<List<SpanData>> getTraces() {
    TelemetryDataUtil.groupTraces(testRunner().getExportedSpans())
  }

  /** Return a list of all captured metrics. */
  List<MetricData> getMetrics() {
    testRunner().getExportedMetrics()
  }

  /** Return a list of all captured logs. */
  List<LogData> getLogs() {
    testRunner().getExportedLogs()
  }

  /**
   * Removes all captured telemetry data. After calling this method {@link #getTraces()} and
   * {@link #getMetrics()} will return empty lists until more telemetry data is captured.
   */
  void clearExportedData() {
    testRunner().clearAllExportedData()
  }

  boolean forceFlushCalled() {
    return testRunner().forceFlushCalled()
  }

  /**
   * Wait until at least {@code numberOfTraces} traces are completed and return all captured traces.
   * Note that there may be more than {@code numberOfTraces} collected. By default this waits up to
   * 20 seconds, then times out.
   */
  List<List<SpanData>> waitForTraces(int numberOfTraces) {
    TelemetryDataUtil.waitForTraces({ testRunner().getExportedSpans() }, numberOfTraces)
  }

  void ignoreTracesAndClear(int numberOfTraces) {
    waitForTraces(numberOfTraces)
    clearExportedData()
  }

  void assertTraces(
    final int size,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces({ testRunner().getExportedSpans() }, size, spec)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  def <T> T runWithSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithSpan(spanName, (ThrowingSupplier) callback)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  def <T> T runWithHttpClientSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithHttpClientSpan(spanName, (ThrowingSupplier) callback)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  def <T> T runWithHttpServerSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithHttpServerSpan(spanName, (ThrowingSupplier) callback)
  }
}
