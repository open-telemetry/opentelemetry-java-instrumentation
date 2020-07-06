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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert

trait InstrumentationTestTrait {

  static InstrumentationTestRunner INSTRUMENTATION_TEST_RUNNER
  static InMemoryExporter TEST_WRITER

  def setupSpec() {
    INSTRUMENTATION_TEST_RUNNER = new InstrumentationTestRunnerImpl()
    TEST_WRITER = InstrumentationTestRunner.TEST_WRITER
  }

  def childSetupSpec() {
  }

  def setup() {
    INSTRUMENTATION_TEST_RUNNER.beforeTest()
  }

  def childSetup() {
  }

  void assertTraces(final int size,
                    @ClosureParams(
                      value = SimpleType.class,
                      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    INSTRUMENTATION_TEST_RUNNER.assertTraces(size, spec)
  }

  static class InstrumentationTestRunnerImpl extends InstrumentationTestRunner {}
}
