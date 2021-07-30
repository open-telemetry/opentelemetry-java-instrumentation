/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import spock.lang.Specification

import java.lang.reflect.Method
import java.util.function.Function

class MethodCacheTest extends Specification {

  def "get item in the cache"() {
    given:
    def cache = new MethodCache<String>()
    def method = TestClass.getDeclaredMethod("method")
    def value = "Value"
    cache.put(method, value)

    when:
    def result = cache.get(method)

    then:
    result == value
  }

  def "get item in the cache by equivalent Method instance"() {
    given:
    def cache = new MethodCache<String>()
    def method1 = TestClass.getDeclaredMethod("method")
    def method2 = TestClass.getDeclaredMethod("method")
    def value = "Value"
    cache.put(method1, value)

    when:
    def result = cache.get(method2)

    then:
    !method1.is(method2)
    result == value
  }

  def "gets returns null if item is not in the cache"() {
    given:
    def cache = new MethodCache<String>()
    def method = TestClass.getDeclaredMethod("method")

    when:
    def result = cache.get(method)

    then:
    result == null
  }

  def "computes item if absent"() {
    given:
    def cache = new MethodCache<String>()
    def method = TestClass.getDeclaredMethod("method")
    def value = "Value"
    Function<Method, String> fn = Mock()

    when:
    def result = cache.computeIfAbsent(method, fn)

    then:
    result == value
    1 * fn.apply(method) >> value
  }

  def "computes item if absent only once"() {
    given:
    def cache = new MethodCache<String>()
    def method = TestClass.getDeclaredMethod("method")
    def value = "Value"
    Function<Method, String> fn = Mock()

    when:
    def result1 = cache.computeIfAbsent(method, fn)
    def result2 = cache.computeIfAbsent(method, fn)

    then:
    result1 == value
    result2 == value
    1 * fn.apply(method) >> value
  }

  def "does not compute item when already in cache"() {
    given:
    def cache = new MethodCache<String>()
    def method = TestClass.getDeclaredMethod("method")
    def value = "Value"
    cache.put(method, value)
    Function<Method, String> fn = Mock()

    when:
    def result = cache.computeIfAbsent(method, fn)

    then:
    result == value
    0 * fn.apply(method)
  }

  class TestClass {
    void method() { }
  }
}
