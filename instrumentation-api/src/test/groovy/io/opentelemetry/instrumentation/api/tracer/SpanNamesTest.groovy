/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import org.spockframework.util.ReflectionUtil
import spock.lang.Specification

class SpanNamesTest extends Specification {

  def "test from Class"() {
    when:
    String result = ClassNames.simpleName(clazz)

    then:
    result == expected

    where:
    clazz         | expected
    SpanNamesTest | "SpanNamesTest"
    SpanNames     | "SpanNames"
  }

  def "test from Method"() {
    when:
    String result = SpanNames.from(method)

    then:
    result == expected

    where:
    method                                            | expected
    ReflectionUtil.getMethodByName(SpanNames, "from") | "SpanNames.from"
    ReflectionUtil.getMethodByName(String, "length")  | "String.length"
  }

  def "test from Class and Method"() {
    when:
    String result = SpanNames.from(clazz, method)

    then:
    result == expected

    where:
    clazz = SpanNames
    method = ReflectionUtil.getMethodByName(SpanNames, "from")
    expected = "SpanNames.from"
  }

  def "test from Class and method name"() {
    when:
    String result = SpanNames.from(clazz, method)

    then:
    result == expected

    where:
    clazz = SpanNames
    method = "test"
    expected = "SpanNames.test"
  }
}
