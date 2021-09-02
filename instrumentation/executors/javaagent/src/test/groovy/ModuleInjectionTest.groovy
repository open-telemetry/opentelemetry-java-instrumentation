/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import javax.swing.*

/**
 * This class tests that we correctly add module references when instrumenting
 */
class ModuleInjectionTest extends AgentInstrumentationSpecification {
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
