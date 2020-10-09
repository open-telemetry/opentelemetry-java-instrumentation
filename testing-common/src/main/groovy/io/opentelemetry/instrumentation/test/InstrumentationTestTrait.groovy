/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import com.google.common.base.Predicate
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * A trait which initializes instrumentation library tests, including a test span exporter. All
 * library tests should implement this trait.
 */
trait InstrumentationTestTrait {

  static InstrumentationTestRunner instrumentationTestRunner
  static InMemoryExporter testWriter

  def setupSpec() {
    instrumentationTestRunner = new InstrumentationTestRunnerImpl()
    testWriter = InstrumentationTestRunner.TEST_WRITER

    childSetupSpec()
  }

  def setup() {
    instrumentationTestRunner.beforeTest()

    childSetup()
  }

  /**
   * Initialization method called once per test class. Equivalent to Spock's {@code setupSpec} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetupSpec() {}

  /**
   * Initialization method called once per individual test. Equivalent to Spock's {@code setup} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetup() {}

  void assertTraces(final int size,
                    @ClosureParams(
                      value = SimpleType,
                      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    instrumentationTestRunner.assertTraces(size, spec)
  }

  void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    instrumentationTestRunner.assertTracesWithFilter(size, spec)
  }

  static class InstrumentationTestRunnerImpl extends InstrumentationTestRunner {}
}
