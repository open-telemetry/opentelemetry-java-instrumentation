/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker

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
    // although marker interfaces are removed from getInterfaces() result class is still assignable
    // to them
    VirtualFieldInstalledMarker.isAssignableFrom(TestClass)
    VirtualFieldAccessorMarker.isAssignableFrom(TestClass)
    TestClass.getInterfaces().length == 2
    TestClass.getInterfaces() == [Runnable, Serializable]
  }

  def "test generated serialVersionUID"() {
    // expected value is computed with serialver utility that comes with jdk
    expect:
    ObjectStreamClass.lookup(TestClass).getSerialVersionUID() == -1508684692096503670L

    and:
    TestClass.getDeclaredFields().length == 0
  }
}
