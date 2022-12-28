/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support

import groovy.transform.CompileStatic
import spock.lang.Specification

import java.lang.invoke.MethodHandles

class AnnotationReflectionHelperTest extends Specification {

  def "returns the annotation type by name"() {
    given:
    def cls = AnnotationReflectionHelper.forNameOrNull(
      AnnotationReflectionHelperTest.classLoader,
      "io.opentelemetry.instrumentation.api.annotation.support.CustomAnnotation")

    expect:
    cls == CustomAnnotation
  }

  def "returns null for an annotation not found"() {
    given:
    def cls = AnnotationReflectionHelper.forNameOrNull(
      AnnotationReflectionHelperTest.classLoader,
      "io.opentelemetry.instrumentation.api.annotation.support.NonExistentAnnotation")

    expect:
    cls == null
  }

  def "returns null for a class that is not an annotation"() {
    given:
    def cls = AnnotationReflectionHelper.forNameOrNull(
      AnnotationReflectionHelperTest.classLoader,
      "java.util.List")

    expect:
    cls == null
  }

  // Using @CompileStatic to ensure that groovy does not call MethodHandles.lookup using method
  // handles. MethodHandles.lookup is caller sensitive. Test fails with lookup where the caller is
  // an anonymous class.
  @CompileStatic
  private static MethodHandles.Lookup getLookup() {
    return MethodHandles.lookup()
  }

  def "returns bound functional interface to annotation element"() {
    given:
    def function = AnnotationReflectionHelper.bindAnnotationElementMethod(
      getLookup(),
      CustomAnnotation,
      "value",
      String
    )
    def annotation = Annotated.getDeclaredAnnotation(CustomAnnotation)

    expect:
    function.apply(annotation) == "Value"
  }

  @CustomAnnotation(value = "Value")
  class Annotated {}
}
