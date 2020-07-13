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

trait AgentTestTrait {

  static AgentTestRunner AGENT_TEST_RUNNER
  static InMemoryExporter TEST_WRITER

  def setupSpec() {
    AGENT_TEST_RUNNER = new AgentTestRunnerImpl()
    TEST_WRITER = AgentTestRunner.TEST_WRITER

    AgentTestRunner.agentSetup()
    AGENT_TEST_RUNNER.setupBeforeTests()

    childSetupSpec()
  }

  def setup() {
    AGENT_TEST_RUNNER.beforeTest()

    childSetup()
  }

  def cleanupSpec() {
    AGENT_TEST_RUNNER.cleanUpAfterTests()
    AgentTestRunner.agentCleanup()
  }

  // Work around https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
  def childSetup() {}

  def childSetupSpec() {}

  def childCleanupSpec() {}

  void assertTraces(final int size,
                    @ClosureParams(
                      value = SimpleType.class,
                      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    AgentTestRunner.assertTraces(size, spec)
  }

  void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType.class,
      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    AgentTestRunner.assertTracesWithFilter(size, excludes, spec);
  }

  static class AgentTestRunnerImpl extends AgentTestRunner {}
}