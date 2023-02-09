/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

class ParameterizedClassTest {

  Type stringArrayListType = getGeneric(TestFields.class, "stringArrayListField");

  static Type getGeneric(Class<?> clazz, String fieldName) {
    try {
      return clazz.getField(fieldName).getGenericType();
    } catch (NoSuchFieldException e) {
      fail("No such field");
      throw new RuntimeException("No such field", e);
    }
  }

  @Test
  void reflectsClass() {

    ParameterizedClass underTest = ParameterizedClass.of(ArrayList.class);
    assertThat(underTest.getRawClass()).isEqualTo(ArrayList.class);
    assertThat(underTest.getActualTypeArguments())
        .hasSize(1)
        .allMatch(x -> x instanceof TypeVariable);
  }

  @Test
  void reflectsParameterizedType() {
    ParameterizedClass underTest = ParameterizedClass.of(stringArrayListType);
    assertThat(underTest.getRawClass()).isEqualTo(ArrayList.class);
    assertThat(underTest.getActualTypeArguments()).hasSize(1).allMatch(x -> x == String.class);
  }

  @Test
  void getsParameterizedSuperclassWithMappedtypeArguments() {
    ParameterizedClass underTest =
        ParameterizedClass.of(stringArrayListType).getParameterizedSuperclass();
    assertThat(underTest.getActualTypeArguments()).hasSize(1).allMatch(x -> x == String.class);
  }

  @Test
  void getsParameterizedInterfacesWithMappedTypeArguments() {
    ParameterizedClass[] underTest =
        ParameterizedClass.of(stringArrayListType).getParameterizedInterfaces();
    assertThat(underTest)
        .satisfiesExactlyInAnyOrder(
            x -> matchesParameterizedClass(List.class, new Type[] {String.class}).matches(x),
            x -> matchesParameterizedClass(Cloneable.class, new Type[0]).matches(x),
            x -> matchesParameterizedClass(RandomAccess.class, new Type[0]).matches(x),
            x -> matchesParameterizedClass(Serializable.class, new Type[0]).matches(x));
  }

  @Test
  void findsParameterizedInterfaceWithMappedTypeArguments() {
    Optional<ParameterizedClass> underTest =
        ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(List.class);
    assertThat(underTest).isPresent();
    assertThat(underTest.get().getRawClass()).isEqualTo(List.class);
    assertThat(underTest.get().getActualTypeArguments())
        .hasSize(1)
        .allMatch(x -> x == String.class);
  }

  @Test
  void findsParameterizedInterfaceOfSuperclassWithMappedTypeArguments() {
    Optional<ParameterizedClass> underTest =
        ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(Collection.class);
    assertThat(underTest.get().getRawClass()).isEqualTo(Collection.class);
    assertThat(underTest.get().getActualTypeArguments())
        .hasSize(1)
        .allMatch(x -> x == String.class);
  }

  @Test
  void doesNotFindParameterizedSuperclassThatTypeDoesNotExtend() {
    Optional<ParameterizedClass> underTest =
        ParameterizedClass.of(stringArrayListType)
            .findParameterizedSuperclass(AbstractTestClass.class);
    assertThat(underTest).isNotPresent();
  }

  @Test
  void doesNotFindParameterizedInterfaceThatTypeDoesNotImplement() {
    Optional<ParameterizedClass> underTest =
        ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(TestInterface.class);
    assertThat(underTest).isNotPresent();
  }

  static class TestFields {
    private TestFields() {}
    ;

    public static List<String> stringListField;
    public static ArrayList<String> stringArrayListField;
  }

  interface TestInterface {}

  abstract static class AbstractTestClass {}

  static Matcher<ParameterizedClass> matchesParameterizedClass(
      Class<?> rawClass, Type[] typeArguments) {
    return new TypeSafeMatcher<ParameterizedClass>() {
      @Override
      public boolean matchesSafely(ParameterizedClass item) {
        return (item != null)
            && (item.getRawClass() == rawClass)
            && Arrays.equals(item.getActualTypeArguments(), typeArguments);
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("a ParameterizedClass with raw type ")
            .appendValue(rawClass)
            .appendText(" and type parameters ")
            .appendValue(typeArguments);
      }
    };
  }
}
