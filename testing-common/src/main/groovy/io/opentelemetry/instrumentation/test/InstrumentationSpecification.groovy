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

  boolean forceFlushCalled() {
    return testRunner().forceFlushCalled()
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
