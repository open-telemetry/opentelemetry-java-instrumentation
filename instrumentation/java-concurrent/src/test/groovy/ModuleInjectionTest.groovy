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

import io.opentelemetry.auto.test.AgentTestRunner

import javax.swing.*

/**
 * This class tests that we correctly add module references when instrumenting
 */
class ModuleInjectionTest extends AgentTestRunner {
  /**
   * There's nothing special about RepaintManager other than
   * it's in a module (java.desktop) that doesn't read the "unnamed module" and it
   * creates an instrumented runnable in its constructor
   */
  def "test instrumenting java.desktop class"() {
    when:
    new RepaintManager()

    then:
    noExceptionThrown()
  }
}
