/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

import static org.hamcrest.Matchers.arrayContainingInAnyOrder
import static spock.util.matcher.HamcrestSupport.expect

class ParameterizedClassTest extends Specification {
  @Shared
  def stringListType = TestFields.getField("stringListField").getGenericType()

  @Shared
  def stringArrayListType = TestFields.getField("stringArrayListField").getGenericType()

  def "reflects Class"() {
    when:
    def underTest = ParameterizedClass.of(ArrayList)

    then:
    underTest.getRawClass() == ArrayList
    underTest.getActualTypeArguments()[0] instanceof TypeVariable
  }

  def "reflects ParameterizedType"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType)

    then:
    underTest.getRawClass() == ArrayList
    underTest.getActualTypeArguments() == [String] as Type[]
  }

  def "gets parameterized superclass with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).getParameterizedSuperclass()

    then:
    underTest.getRawClass() == AbstractList
    underTest.getActualTypeArguments() == [String] as Type[]
  }

  def "gets parameterized interfaces with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).getParameterizedInterfaces()

    then:
    expect underTest, arrayContainingInAnyOrder(
      matchesParameterizedClass(List, [String] as Type[]),
      matchesParameterizedClass(Cloneable, [] as Type[]),
      matchesParameterizedClass(RandomAccess, [] as Type[]),
      matchesParameterizedClass(Serializable, [] as Type[]),
    )
  }

  def "finds parameterized interface with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(List)

    then:
    underTest.isPresent()
    underTest.get().getRawClass() == List
    underTest.get().getActualTypeArguments() == [String] as Type[]
  }

  def "finds parameterized interface of superclass with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(Collection)

    then:
    underTest.isPresent()
    underTest.get().getRawClass() == Collection
    underTest.get().getActualTypeArguments() == [String] as Type[]
  }

  def "does not find parameterized superclass that type does not extend"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(C)

    then:
    !underTest.isPresent()
  }

  def "does not find parameterized interface that type does not implement"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(I)

    then:
    !underTest.isPresent()
  }

  interface I {}

  abstract class C {}

  static class TestFields {
    public static List<String> stringListField
    public static ArrayList<String> stringArrayListField
  }

  static Matcher<ParameterizedClass> matchesParameterizedClass(Class<?> rawClass, Type[] typeArguments) {
    return new TypeSafeMatcher<ParameterizedClass>() {
      @Override
      boolean matchesSafely(ParameterizedClass item) {
        item != null && item.getRawClass() == rawClass && Arrays.equals(item.getActualTypeArguments(), typeArguments)
      }

      @Override
      void describeTo(Description description) {
        description
          .appendText("a ParameterizedClass with raw type ")
          .appendValue(rawClass)
          .appendText(" and type parameters ")
          .appendValue(typeArguments)
      }
    }
  }
}
