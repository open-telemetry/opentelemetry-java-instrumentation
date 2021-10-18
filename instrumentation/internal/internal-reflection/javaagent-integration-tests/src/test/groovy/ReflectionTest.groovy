/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.lang.reflect.Field
import java.lang.reflect.Method

class ReflectionTest extends AgentInstrumentationSpecification {

  def "test our fields and methods are not visible with reflection"() {
    when:
    TestClass test = new TestClass()

    then:
    test.testMethod() == "instrumented"
    test.testMethod2() == "instrumented"

    and:
    def fieldFound = false
    for (Field field : TestClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        fieldFound = true
      }
    }
    fieldFound == false

    and:
    def methodFound = false
    for (Method method : TestClass.getDeclaredMethods()) {
      if (method.getName().contains("__opentelemetry")) {
        methodFound = true
      }
    }
    methodFound == false

    and:
    def interfaceClass = TestClass.getInterfaces().find {
      it.getName().contains("VirtualFieldAccessor\$")
    }
    interfaceClass != null
    def interfaceMethodFound = false
    for (Method method : interfaceClass.getDeclaredMethods()) {
      if (method.getName().contains("__opentelemetry")) {
        interfaceMethodFound = true
      }
    }
    interfaceMethodFound == false
  }
}
