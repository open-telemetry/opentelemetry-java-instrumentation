/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotation.support

import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

class ParameterizedClassTest extends Specification {
  @Shared
  def stringListType = TestFields.getField("stringListField").getGenericType()

  @Shared
  def stringArrayListType = TestFields.getField("stringArrayListField").getGenericType()

  def "reflects Class"() {
    when:
    def underTest = ParameterizedClass.of(ArrayList)

    then:
    underTest.getRawType() == ArrayList
    underTest.getOwnerType() == null
    underTest.getActualTypeArguments()[0] instanceof TypeVariable
    underTest.getTypeName() == "java.util.ArrayList<E>"
  }

  def "reflects ParameterizedType"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType)

    then:
    underTest.getRawType() == ArrayList
    underTest.getOwnerType() == null
    underTest.getActualTypeArguments() == [ String ] as Type[]
    underTest.getTypeName() == "java.util.ArrayList<java.lang.String>"
  }

  def "gets parameterized superclass with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).getParameterizedSuperclass()

    then:
    underTest.getRawType() == AbstractList
    underTest.getOwnerType() == null
    underTest.getActualTypeArguments() == [ String ] as Type[]
    underTest.getTypeName() == "java.util.AbstractList<java.lang.String>"
  }

  def "gets parameterized interfaces with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).getParameterizedInterfaces()

    then:
    underTest.contains(ParameterizedClass.of(stringListType))
  }

  def "finds parameterized interface with mapped type arguments"() {
    when:
    def underTest = ParameterizedClass.of(stringArrayListType).findParameterizedSuperclass(List)

    then:
    underTest.isPresent()
    underTest.get().getRawType() == List
    underTest.get().getOwnerType() == null
    underTest.get().getActualTypeArguments() == [ String ] as Type[]
    underTest.get().getTypeName() == "java.util.List<java.lang.String>"
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

  interface I { }
  abstract class C { }

  static class TestFields {
    public static List<String> stringListField
    public static ArrayList<String> stringArrayListField
  }
}
