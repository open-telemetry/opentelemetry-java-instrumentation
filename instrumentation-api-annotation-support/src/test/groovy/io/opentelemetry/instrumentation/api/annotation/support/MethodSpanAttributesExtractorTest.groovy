/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.cache.Cache
import spock.lang.Specification

import java.lang.reflect.Method
import java.util.function.Function

class MethodSpanAttributesExtractorTest extends Specification {

  def "extracts attributes for method with attribute names"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> { m, fn -> fn.apply(m) }
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> ["x", "y", "z"] as String[] },
      { r -> ["a", "b", "c"] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    1 * builder.put({ it.getKey() == "x" }, "a")
    1 * builder.put({ it.getKey() == "y" }, "b")
    1 * builder.put({ it.getKey() == "z" }, "c")
  }

  def "does not extract attributes for empty attribute name array"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> { m, fn -> fn.apply(m) }
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> new String[0] },
      { r -> ["a", "b", "c"] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    0 * builder.put(*_)
  }

  def "does not extract attributes for method with attribute names array with fewer elements than parameters"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> { m, fn -> fn.apply(m) }
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> ["x", "y"] as String[] },
      { r -> ["a", "b", "c"] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    0 * builder.put(*_)
  }

  def "extracts attributes for method with attribute names array with null element"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> { m, fn -> fn.apply(m) }
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> ["x", null, "z"] as String[] },
      { r -> ["a", "b", "c"] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    1 * builder.put({ it.getKey() == "x" }, "a")
    1 * builder.put({ it.getKey() == "z" }, "c")
    0 * builder.put(_, "b")
  }

  def "does not extracts attribute for method with null argument"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> { m, fn -> fn.apply(m) }
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> ["x", "y", "z"] as String[] },
      { r -> ["a", "b", null] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    1 * builder.put({ it.getKey() == "x" }, "a")
    1 * builder.put({ it.getKey() == "y" }, "b")
    0 * builder.put({ it.getKey() == "z" }, _)
  }

  def "applies cached bindings"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    AttributeBindings bindings = Mock {
      1 * isEmpty() >> false
    }
    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> bindings
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> throw new Exception() },
      { r -> ["a", "b", "c"] as String[] },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    1 * bindings.apply(_, ["a", "b", "c"])
  }

  def "does not apply cached empty bindings"() {
    given:
    def request = new Object()
    def context = Context.root()
    def method = TestClass.getDeclaredMethod("method", String, String, String)
    AttributesBuilder builder = Mock()

    AttributeBindings bindings = Mock {
      1 * isEmpty() >> true
    }
    Cache<Method, AttributeBindings> cache = Mock {
      1 * computeIfAbsent(method, _ as Function<Method, AttributeBindings>) >> bindings
    }

    def extractor = new MethodSpanAttributesExtractor<Object, Object>(
      { r -> method },
      { m, p -> throw new Exception() },
      { r -> throw new Exception() },
      cache
    )

    when:
    extractor.onStart(builder, context, request)

    then:
    0 * bindings.apply(_, _)
  }

  class TestClass {
    @SuppressWarnings("unused")
    void method(String x, String y, String z) {}
  }
}
