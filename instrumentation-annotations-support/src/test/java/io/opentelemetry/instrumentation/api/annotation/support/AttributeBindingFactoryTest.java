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
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.AttributesBuilder;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributeBindingFactoryTest {

  @Mock AttributesBuilder setter;

  @Test
  void createAttributeBindingForString() {
    AttributeBindingFactory.createBinding("key", String.class).apply(setter, "value");
    verify(setter).put(stringKey("key"), "value");
  }

  @Test
  void createAttributeBindingForIntPrimitive() {
    AttributeBindingFactory.createBinding("key", int.class).apply(setter, 1234);
    verify(setter).put(longKey("key"), 1234L);
  }

  @Test
  void createAttributeBindingForInteger() {
    AttributeBindingFactory.createBinding("key", Integer.class).apply(setter, 1234);
    verify(setter).put(longKey("key"), 1234L);
  }

  @Test
  void createAttributeBindingForLongPrimitive() {
    AttributeBindingFactory.createBinding("key", long.class).apply(setter, 1234L);
    verify(setter).put(longKey("key"), 1234L);
  }

  @Test
  void createAttributeBindingForLong() {
    AttributeBindingFactory.createBinding("key", Long.class).apply(setter, 1234L);
    verify(setter).put(longKey("key"), 1234L);
  }

  @Test
  void createAttributeBindingForFloatPrimitive() {
    AttributeBindingFactory.createBinding("key", float.class).apply(setter, 1234.0F);
    verify(setter).put(doubleKey("key"), 1234.0);
  }

  @Test
  void createAttributeBindingForFloat() {
    AttributeBindingFactory.createBinding("key", Float.class).apply(setter, 1234.0F);
    verify(setter).put(doubleKey("key"), 1234.0);
  }

  @Test
  void createAttributeBindingForDoublePrimitive() {
    AttributeBindingFactory.createBinding("key", double.class).apply(setter, 1234.0);
    verify(setter).put(doubleKey("key"), 1234.0);
  }

  @Test
  void createAttributeBindingForDouble() {
    AttributeBindingFactory.createBinding("key", Double.class).apply(setter, 1234.0);
    verify(setter).put(doubleKey("key"), 1234.0);
  }

  @Test
  void createAttributeBindingForBooleanPrimitive() {
    AttributeBindingFactory.createBinding("key", boolean.class).apply(setter, true);
    verify(setter).put(booleanKey("key"), true);
  }

  @Test
  void createAttributeBindingForBoolean() {
    AttributeBindingFactory.createBinding("key", Boolean.class).apply(setter, true);
    verify(setter).put(booleanKey("key"), true);
  }

  @Test
  void createAttributeBindingForStringArray() {
    String[] value = {"x", "y", "z", null};
    List<String> expected = Arrays.asList(value);
    AttributeBindingFactory.createBinding("key", String[].class).apply(setter, value);
    verify(setter).put(stringArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForPrimitiveIntArray() {
    int[] value = {1, 2, 3};
    List<Long> expected = Arrays.asList(1L, 2L, 3L);
    AttributeBindingFactory.createBinding("key", int[].class).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForIntegerArray() {
    Integer[] value = {1, 2, 3};
    List<Long> expected = Arrays.asList(1L, 2L, 3L);
    AttributeBindingFactory.createBinding("key", Integer[].class).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForPrimitiveLongArray() {
    long[] value = {1, 2, 3};
    List<Long> expected = Arrays.asList(1L, 2L, 3L);
    AttributeBindingFactory.createBinding("key", long[].class).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForLongArray() {
    Long[] value = {1L, 2L, 3L};
    List<Long> expected = Arrays.asList(1L, 2L, 3L);
    AttributeBindingFactory.createBinding("key", Long[].class).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForPrimitiveFloatArray() {
    float[] value = {1f, 2f, 3f};
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    AttributeBindingFactory.createBinding("key", float[].class).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForFloatArray() {
    Float[] value = {1f, 2f, 3f};
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    AttributeBindingFactory.createBinding("key", Float[].class).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForPrimitiveDoubleArray() {
    double[] value = {1f, 2f, 3f};
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    AttributeBindingFactory.createBinding("key", double[].class).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForDoubleArray() {
    Double[] value = {1.0, 2.0, 3.0};
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    AttributeBindingFactory.createBinding("key", Double[].class).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForPrimitiveBooleanArray() {
    boolean[] value = {true, false};
    List<Boolean> expected = Arrays.asList(true, false);
    AttributeBindingFactory.createBinding("key", boolean[].class).apply(setter, value);
    verify(setter).put(booleanArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForBooleanArray() {
    Boolean[] value = {true, false};
    List<Boolean> expected = Arrays.asList(true, false);
    AttributeBindingFactory.createBinding("key", Boolean[].class).apply(setter, value);
    verify(setter).put(booleanArrayKey("key"), expected);
  }

  @Test
  void createDefaultAttributeBinding() {
    AttributeBindingFactory.createBinding("key", TestClass.class)
        .apply(setter, new TestClass("foo"));
    verify(setter).put(stringKey("key"), "TestClass{value = foo}");
  }

  @Test
  void createDefaultAttributeBindingForArray() {
    List<String> expected = Arrays.asList("TestClass{value = foo}", "TestClass{value = bar}", null);
    AttributeBindingFactory.createBinding("key", TestClass[].class)
        .apply(setter, new TestClass[] {new TestClass("foo"), new TestClass("bar"), null});
    verify(setter).put(stringArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForStringList() throws Exception {
    List<String> value = Arrays.asList("x", "y", "z");
    Type type = TestFields.class.getDeclaredField("stringList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(stringArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForIntegerList() throws Exception {
    List<Integer> value = Arrays.asList(1, 2, 3);
    List<Long> expected = Arrays.asList(1L, 2L, 3L);
    Type type = TestFields.class.getDeclaredField("integerList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(longArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForLongList() throws Exception {
    List<Long> value = Arrays.asList(1L, 2L, 3L);
    Type type = TestFields.class.getDeclaredField("longList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(longArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForFloatList() throws Exception {
    List<Float> value = Arrays.asList(1.0f, 2.0f, 3.0f);
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    Type type = TestFields.class.getDeclaredField("floatList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForDoubleList() throws Exception {
    List<Double> value = Arrays.asList(1.0, 2.0, 3.0);
    List<Double> expected = Arrays.asList(1.0, 2.0, 3.0);
    Type type = TestFields.class.getDeclaredField("doubleList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(doubleArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForBooleanList() throws Exception {
    List<Boolean> value = Arrays.asList(true, false, null);
    Type type = TestFields.class.getDeclaredField("booleanList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(booleanArrayKey("key"), value);
  }

  @Test
  void createAttributeBindingForGenericList() throws Exception {
    List<TestClass> value = Arrays.asList(new TestClass("foo"), new TestClass("bar"));
    List<String> expected = Arrays.asList("TestClass{value = foo}", "TestClass{value = bar}");
    Type type = TestFields.class.getDeclaredField("otherList").getGenericType();
    AttributeBindingFactory.createBinding("key", type).apply(setter, value);
    verify(setter).put(stringArrayKey("key"), expected);
  }

  @Test
  void createAttributeBindingForLongArrayList() throws Exception {
    List<Long> value = Arrays.asList(1L, 2L, 3L);
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
