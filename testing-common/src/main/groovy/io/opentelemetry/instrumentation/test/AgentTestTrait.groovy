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

  def setupSpec() {
    agentTestRunner = new AgentTestRunnerImpl()
    testWriter = AgentTestRunner.TEST_WRITER

    agentTestRunner.setupBeforeTests()

    childSetupSpec()
  }

  def setup() {
    agentTestRunner.beforeTest()

    childSetup()
  }

  def cleanupSpec() {
    AgentTestRunner.agentCleanup()

    childCleanupSpec()
  }

  /**
   * Initialization method called once per individual test. Equivalent to Spock's {@code setup} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetup() {}

  /**
   * Initialization method called once per test class. Equivalent to Spock's {@code setupSpec} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetupSpec() {}

  /**
   * Cleanup method called once per test class. Equivalent to Spock's {@code cleanupSpec} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childCleanupSpec() {}

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
