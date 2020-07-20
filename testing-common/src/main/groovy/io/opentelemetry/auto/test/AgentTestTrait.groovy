/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.test

import com.google.common.base.Predicate
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.trace.data.SpanData

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

    AgentTestRunner.agentSetup()
    agentTestRunner.setupBeforeTests()

    childSetupSpec()
  }

  def setup() {
    agentTestRunner.beforeTest()

    childSetup()
  }

  def cleanupSpec() {
    agentTestRunner.cleanUpAfterTests()
    AgentTestRunner.agentCleanup()
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
                      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    AgentTestRunner.assertTraces(size, spec)
  }

  void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    AgentTestRunner.assertTracesWithFilter(size, excludes, spec)
  }

  static class AgentTestRunnerImpl extends AgentTestRunner {}
}
