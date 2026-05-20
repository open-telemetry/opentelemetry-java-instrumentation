/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributeBindingFactoryTest {

  @Mock AttributesBuilder setter;

  private static Stream<Arguments> bindingCases() {
    return Stream.of(
        // scalar types
        arguments("String", String.class, "value", stringKey("key"), "value"),
        arguments("int primitive", int.class, 1234, longKey("key"), 1234L),
        arguments("Integer", Integer.class, 1234, longKey("key"), 1234L),
        arguments("long primitive", long.class, 1234L, longKey("key"), 1234L),
        arguments("Long", Long.class, 1234L, longKey("key"), 1234L),
        arguments("float primitive", float.class, 1234.0F, doubleKey("key"), 1234.0),
        arguments("Float", Float.class, 1234.0F, doubleKey("key"), 1234.0),
        arguments("double primitive", double.class, 1234.0, doubleKey("key"), 1234.0),
        arguments("Double", Double.class, 1234.0, doubleKey("key"), 1234.0),
        arguments("boolean primitive", boolean.class, true, booleanKey("key"), true),
        arguments("Boolean", Boolean.class, true, booleanKey("key"), true),
        // array types
        arguments(
            "String[]",
            String[].class,
            new String[] {"x", "y", "z", null},
            stringArrayKey("key"),
            asList("x", "y", "z", null)),
        arguments(
            "int[]", int[].class, new int[] {1, 2, 3}, longArrayKey("key"), asList(1L, 2L, 3L)),
        arguments(
            "Integer[]",
            Integer[].class,
            new Integer[] {1, 2, 3},
            longArrayKey("key"),
            asList(1L, 2L, 3L)),
        arguments(
            "long[]", long[].class, new long[] {1, 2, 3}, longArrayKey("key"), asList(1L, 2L, 3L)),
        arguments(
            "Long[]",
            Long[].class,
            new Long[] {1L, 2L, 3L},
            longArrayKey("key"),
            asList(1L, 2L, 3L)),
        arguments(
            "float[]",
            float[].class,
            new float[] {1f, 2f, 3f},
            doubleArrayKey("key"),
            asList(1.0, 2.0, 3.0)),
        arguments(
            "Float[]",
            Float[].class,
            new Float[] {1f, 2f, 3f},
            doubleArrayKey("key"),
            asList(1.0, 2.0, 3.0)),
        arguments(
            "double[]",
            double[].class,
            new double[] {1, 2, 3},
            doubleArrayKey("key"),
            asList(1.0, 2.0, 3.0)),
        arguments(
            "Double[]",
            Double[].class,
            new Double[] {1.0, 2.0, 3.0},
            doubleArrayKey("key"),
            asList(1.0, 2.0, 3.0)),
        arguments(
            "boolean[]",
            boolean[].class,
            new boolean[] {true, false},
            booleanArrayKey("key"),
            asList(true, false)),
        arguments(
            "Boolean[]",
            Boolean[].class,
            new Boolean[] {true, false},
            booleanArrayKey("key"),
            asList(true, false)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bindingCases")
  <T> void createAttributeBinding(
      String name, Type type, Object value, AttributeKey<T> expectedKey, T expectedValue) {
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(expectedKey, expectedValue);
  }

  @Test
  void createDefaultAttributeBinding() {
    AttributeBindingFactory.createBinding("key", TestClass.class)
        .apply(setter, new TestClass("foo"));
    verify(setter).put(stringKey("key"), "TestClass{value = foo}");
  }

  @Test
  void createDefaultAttributeBindingForArray() {
    List<String> expected = asList("TestClass{value = foo}", "TestClass{value = bar}", null);
    AttributeBindingFactory.createBinding("key", TestClass[].class)
        .apply(setter, new TestClass[] {new TestClass("foo"), new TestClass("bar"), null});
    verify(setter).put(stringArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForStringList() throws Exception {
    List<String> value = asList("x", "y", "z");
    Type type = TestFields.class.getDeclaredField("stringList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(stringArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForIntegerList() throws Exception {
    List<Integer> value = asList(1, 2, 3);
    List<Long> expected = asList(1L, 2L, 3L);
    Type type = TestFields.class.getDeclaredField("integerList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForLongList() throws Exception {
    List<Long> value = asList(1L, 2L, 3L);
    Type type = TestFields.class.getDeclaredField("longList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(longArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForFloatList() throws Exception {
    List<Float> value = asList(1.0f, 2.0f, 3.0f);
    List<Double> expected = asList(1.0, 2.0, 3.0);
    Type type = TestFields.class.getDeclaredField("floatList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForDoubleList() throws Exception {
    List<Double> value = asList(1.0, 2.0, 3.0);
    List<Double> expected = asList(1.0, 2.0, 3.0);
    Type type = TestFields.class.getDeclaredField("doubleList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForBooleanList() throws Exception {
    List<Boolean> value = asList(true, false, null);
    Type type = TestFields.class.getDeclaredField("booleanList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(booleanArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForGenericList() throws Exception {
    List<TestClass> value = asList(new TestClass("foo"), new TestClass("bar"));
    List<String> expected = asList("TestClass{value = foo}", "TestClass{value = bar}");
    Type type = TestFields.class.getDeclaredField("otherList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(stringArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForLongArrayList() throws Exception {
    List<Long> value = asList(1L, 2L, 3L);
    Type type = TestFields.class.getDeclaredField("longArrayList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(longArrayKey("key"), value);
  }

  static class TestClass {
    final String value;

    TestClass(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "TestClass{value = " + value + "}";
    }
  }

  static class TestFields {
    List<String> stringList;
    List<Long> longList;
    List<Double> doubleList;
    List<Boolean> booleanList;
    List<Integer> integerList;
    List<Float> floatList;
    List<TestClass> otherList;
    ArrayList<Long> longArrayList;
  }
}
