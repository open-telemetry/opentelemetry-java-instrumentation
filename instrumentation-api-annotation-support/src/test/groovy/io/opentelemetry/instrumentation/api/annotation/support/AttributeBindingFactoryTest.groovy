/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.instrumentation.api.tracer.AttributeSetter
import spock.lang.Specification

class AttributeBindingFactoryTest extends Specification {

  AttributeSetter setter = Mock()

  def "creates attribute binding for String"() {
    when:
    AttributeBindingFactory.createBinding("key", String).apply(setter, "value")

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING && it.getKey() == "key" }, "value")
  }

  def "creates attribute binding for int"() {
    when:
    AttributeBindingFactory.createBinding("key", int).apply(setter, 1234)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG && it.getKey() == "key" }, 1234L)
  }

  def "creates attribute binding for Integer"() {
    when:
    AttributeBindingFactory.createBinding("key", Integer).apply(setter, 1234)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG && it.getKey() == "key" }, 1234L)
  }

  def "creates attribute binding for long"() {
    when:
    AttributeBindingFactory.createBinding("key", long).apply(setter, 1234L)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG && it.getKey() == "key" }, 1234L)
  }

  def "creates attribute binding for Long"() {
    when:
    AttributeBindingFactory.createBinding("key", Long).apply(setter, 1234L)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG && it.getKey() == "key" }, 1234L)
  }

  def "creates attribute binding for float"() {
    when:
    AttributeBindingFactory.createBinding("key", float).apply(setter, 1234.0F)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE && it.getKey() == "key" }, 1234.0)
  }

  def "creates attribute binding for Float"() {
    when:
    AttributeBindingFactory.createBinding("key", Float).apply(setter, 1234.0F)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE && it.getKey() == "key" }, 1234.0)
  }

  def "creates attribute binding for double"() {
    when:
    AttributeBindingFactory.createBinding("key", double).apply(setter, Double.valueOf(1234.0))

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE && it.getKey() == "key" }, Double.valueOf(1234.0))
  }

  def "creates attribute binding for Double"() {
    when:
    AttributeBindingFactory.createBinding("key", Double).apply(setter, Double.valueOf(1234.0))

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE && it.getKey() == "key" }, Double.valueOf(1234.0))
  }

  def "creates attribute binding for boolean"() {
    when:
    AttributeBindingFactory.createBinding("key", boolean).apply(setter, true)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.BOOLEAN && it.getKey() == "key" }, true)
  }

  def "creates attribute binding for Boolean"() {
    when:
    AttributeBindingFactory.createBinding("key", Boolean).apply(setter, true)

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.BOOLEAN && it.getKey() == "key" }, true)
  }

  def "creates attribute binding for String[]"() {
    when:
    AttributeBindingFactory.createBinding("key", String[]).apply(setter, ["x", "y", "z", null] as String[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING_ARRAY && it.getKey() == "key" }, ["x", "y", "z", null])
  }

  def "creates attribute binding for int[]"() {
    when:
    AttributeBindingFactory.createBinding("key", int[]).apply(setter, [1, 2, 3] as int[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L])
  }

  def "creates attribute binding for Integer[]"() {
    when:
    AttributeBindingFactory.createBinding("key", Integer[]).apply(setter, [1, 2, 3, null] as Integer[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L, null])
  }

  def "creates attribute binding for long[]"() {
    when:
    AttributeBindingFactory.createBinding("key", long[]).apply(setter, [1L, 2L, 3L] as long[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L])
  }

  def "creates attribute binding for Long[]"() {
    when:
    AttributeBindingFactory.createBinding("key", Long[]).apply(setter, [1L, 2L, 3L, null] as Long[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L, null])
  }

  def "creates attribute binding for float[]"() {
    when:
    AttributeBindingFactory.createBinding("key", float[]).apply(setter, [1.0F, 2.0F, 3.0F] as float[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0] as List<Double>)
  }

  def "creates attribute binding for Float[]"() {
    when:
    AttributeBindingFactory.createBinding("key", Float[]).apply(setter, [1.0F, 2.0F, 3.0F, null] as Float[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0, null] as List<Double>)
  }

  def "creates attribute binding for double[]"() {
    when:
    AttributeBindingFactory.createBinding("key", double[]).apply(setter, [1.0, 2.0, 3.0] as double[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0] as List<Double>)
  }

  def "creates attribute binding for Double[]"() {
    when:
    AttributeBindingFactory.createBinding("key", Double[]).apply(setter, [1.0, 2.0, 3.0, null] as Double[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0, null] as List<Double>)
  }

  def "creates attribute binding for boolean[]"() {
    when:
    AttributeBindingFactory.createBinding("key", boolean[]).apply(setter, [true, false] as boolean[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.BOOLEAN_ARRAY && it.getKey() == "key" }, [true, false] as List<Boolean>)
  }

  def "creates attribute binding for Boolean[]"() {
    when:
    AttributeBindingFactory.createBinding("key", Boolean[]).apply(setter, [true, false, null] as Boolean[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.BOOLEAN_ARRAY && it.getKey() == "key" }, [true, false, null] as List<Boolean>)
  }

  def "creates default attribute binding"() {
    when:
    AttributeBindingFactory.createBinding("key", TestClass).apply(setter, new TestClass("foo"))

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING && it.getKey() == "key" }, "TestClass{value = foo}")
  }

  def "creates default attribute binding for array"() {
    when:
    AttributeBindingFactory.createBinding("key", TestClass[]).apply(setter, [new TestClass("foo"), new TestClass("bar"), null] as TestClass[])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING_ARRAY && it.getKey() == "key" }, ["TestClass{value = foo}", "TestClass{value = bar}", null])
  }

  def "creates attribute binding for List<String>"() {
    when:
    def type = TestFields.getDeclaredField("stringList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, ["x", "y", "z"])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING_ARRAY && it.getKey() == "key" }, ["x", "y", "z"])
  }

  def "creates attribute binding for List<Integer>"() {
    when:
    def type = TestFields.getDeclaredField("integerList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [1, 2, 3])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L])
  }

  def "creates attribute binding for List<Long>"() {
    when:
    def type = TestFields.getDeclaredField("longList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [1L, 2L, 3L])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L])
  }

  def "creates attribute binding for List<Float>"() {
    when:
    def type = TestFields.getDeclaredField("floatList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [1.0F, 2.0F, 3.0F])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0])
  }

  def "creates attribute binding for List<Double>"() {
    when:
    def type = TestFields.getDeclaredField("doubleList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [1.0, 2.0, 3.0])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.DOUBLE_ARRAY && it.getKey() == "key" }, [1.0, 2.0, 3.0])
  }

  def "creates attribute binding for List<Boolean>"() {
    when:
    def type = TestFields.getDeclaredField("booleanList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [true, false, null])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.BOOLEAN_ARRAY && it.getKey() == "key" }, [true, false, null])
  }

  def "creates attribute binding for List<?>"() {
    when:
    def type = TestFields.getDeclaredField("otherList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [new TestClass("foo"), new TestClass("bar")])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.STRING_ARRAY && it.getKey() == "key" }, ["TestClass{value = foo}", "TestClass{value = bar}"])
  }

  def "creates attribute binding for ArrayList<Long>"() {
    when:
    def type = TestFields.getDeclaredField("longArrayList").getGenericType()
    AttributeBindingFactory.createBinding("key", type).apply(setter, [1L, 2L, 3L])

    then:
    1 * setter.setAttribute({ it.getType() == AttributeType.LONG_ARRAY && it.getKey() == "key" }, [1L, 2L, 3L])
  }

  class TestClass {
    final String value

    TestClass(String value) {
      this.value = value
    }

    @Override
    String toString() {
      return "TestClass{value = " + value + "}"
    }
  }

  class TestFields {
    List<String> stringList
    List<Long> longList
    List<Double> doubleList
    List<Boolean> booleanList
    List<Integer> integerList
    List<Float> floatList
    List<TestClass> otherList
    ArrayList<Long> longArrayList
  }
}
