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
trait LibraryTestTrait {

  static InstrumentationTestRunner instrumentationTestRunner
  static InMemorySpanExporter testWriter

  void runnerSetupSpec() {
    instrumentationTestRunner = new InstrumentationTestRunnerImpl()
    testWriter = InstrumentationTestRunner.testExporter
  }

  void runnerSetup() {
    instrumentationTestRunner.beforeTest()
  }

  void runnerCleanupSpec() {
  }

  boolean forceFlushCalled() {
    return instrumentationTestRunner.forceFlushCalled()
  }

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
