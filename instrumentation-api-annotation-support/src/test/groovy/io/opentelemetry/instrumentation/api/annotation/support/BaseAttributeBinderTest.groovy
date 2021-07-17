/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import io.opentelemetry.instrumentation.api.tracer.AttributeSetter
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Method
import java.lang.reflect.Parameter

class BaseAttributeBinderTest extends Specification {
  @Shared
  Method method = TestClass.getDeclaredMethod("method", String, String, String)

  @Shared
  Object[] args = [ "a", "b", "c" ]

  AttributeSetter setter = Mock()

  def "returns empty bindings for null attribute names array"() {
    given:
    def binder = new TestAttributeBinder(null)

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    bindings.isEmpty()
    0 * setter.setAttribute(*spock.lang.Specification._)
  }

  def "returns empty bindings for empty attribute names array"() {
    given:
    def binder = new TestAttributeBinder(new String[0])

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    bindings.isEmpty()
    0 * setter.setAttribute(*spock.lang.Specification._)
  }

  def "returns empty bindings for attribute names array with all null elements"() {
    given:
    def binder = new TestAttributeBinder([ null, null, null ] as String[])

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    bindings.isEmpty()
    0 * setter.setAttribute(*spock.lang.Specification._)
  }

  def "returns empty bindings for attribute names array with fewer elements than parameters"() {
    given:
    def binder = new TestAttributeBinder([ "x", "y" ] as String[])

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    bindings.isEmpty()
    0 * setter.setAttribute(*spock.lang.Specification._)
  }

  def "returns bindings for attribute names array"() {
    given:
    def binder = new TestAttributeBinder([ "x", "y", "z" ] as String[])

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    !bindings.isEmpty()
    1 * setter.setAttribute({ it.getKey() == "x" }, "a")
    1 * setter.setAttribute({ it.getKey() == "y" }, "b")
    1 * setter.setAttribute({ it.getKey() == "z" }, "c")
  }

  def "returns bindings for attribute names with null name"() {
    given:
    def binder = new TestAttributeBinder([ "x", null, "z" ] as String[])

    when:
    AttributeBindings bindings = binder.bind(method)
    bindings.apply(setter, args)

    then:
    !bindings.isEmpty()
    1 * setter.setAttribute({ it.getKey() == "x" }, "a")
    0 * setter.setAttribute(spock.lang.Specification._, "b")
    1 * setter.setAttribute({ it.getKey() == "z" }, "c")
  }

  class TestAttributeBinder extends BaseAttributeBinder {
    final String[] attributeNames

    TestAttributeBinder(String[] attributeNames) {
      this.attributeNames = attributeNames
    }

    @Override
    protected String[] attributeNamesForParameters(Method method, Parameter[] parameters) {
      return attributeNames
    }
  }

  class TestClass {
    void method(String x, String y, String z) { }
  }
}
