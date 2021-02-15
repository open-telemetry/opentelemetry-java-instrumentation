/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.trace.data.SpanData
import spock.lang.Specification

/**
 * Base class for test specifications that are shared between instrumentation libraries and agent.
 * The methods in this class are implemented by {@link AgentTestTrait} and
 * {@link LibraryTestTrait}.
 */
abstract class InstrumentationSpecification extends Specification {
  abstract InstrumentationTestRunner testRunner()

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

  def cleanupSpec() {
    testRunner().afterTestClass()
  }

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  List<List<SpanData>> getTraces() {
    TelemetryDataUtil.groupTraces(testRunner().getExportedSpans())
  }

  /** Return a list of all captured metrics. */
  List<MetricData> getMetrics() {
    testRunner().getExportedMetrics()
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
}
