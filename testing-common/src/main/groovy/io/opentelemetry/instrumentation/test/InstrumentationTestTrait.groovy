/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test


import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter

/**
 * A trait which initializes instrumentation library tests, including a test span exporter. All
 * library tests should implement this trait.
 */
trait InstrumentationTestTrait {

  static InstrumentationTestRunner instrumentationTestRunner
  static InMemorySpanExporter testWriter

  def setupSpec() {
    instrumentationTestRunner = new InstrumentationTestRunnerImpl()
    testWriter = InstrumentationTestRunner.testExporter

    childSetupSpec()
  }

  def setup() {
    instrumentationTestRunner.beforeTest()

    childSetup()
  }

  boolean forceFlushCalled() {
    return instrumentationTestRunner.forceFlushCalled()
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

  static class InstrumentationTestRunnerImpl extends InstrumentationTestRunner {}
}
