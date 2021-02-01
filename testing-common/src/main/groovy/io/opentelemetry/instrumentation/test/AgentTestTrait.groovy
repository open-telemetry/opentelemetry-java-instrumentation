/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test


import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert

/**
 * A trait which initializes agent tests, including bytecode manipulation and a test span exporter.
 * All agent tests should implement this trait.
 */
trait AgentTestTrait {

  static AgentTestRunner agentTestRunner
  static InMemoryExporter testWriter

  void runnerSetupSpec() {
    agentTestRunner = new AgentTestRunnerImpl()
    testWriter = AgentTestRunner.TEST_WRITER

    agentTestRunner.setupBeforeTests()
  }

  void runnerSetup() {
    agentTestRunner.beforeTest()
  }

  void runnerCleanupSpec() {
    AgentTestRunner.agentCleanup()
  }

  void assertTraces(final int size,
                    @ClosureParams(
                      value = SimpleType,
                      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    AgentTestRunner.assertTraces(size, spec)
  }

  static class AgentTestRunnerImpl extends AgentTestRunner {}
}
