/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.instrumentation.api.instrumenter.SpanNames
import org.spockframework.util.ReflectionUtil
import spock.lang.Specification

class SpanNamesTest extends Specification {

  def "test fromMethod"() {
    when:
    String result = SpanNames.fromMethod(method)

    then:
    result == expected

    where:
    method                                                  | expected
    ReflectionUtil.getMethodByName(SpanNames, "fromMethod") | "SpanNames.fromMethod"
    ReflectionUtil.getMethodByName(String, "length")        | "String.length"
  }

  def "test fromMethod with class and method ref"() {
    when:
    String result = SpanNames.fromMethod(clazz, method)

    then:
    result == expected

    where:
    clazz = SpanNames
    method = ReflectionUtil.getMethodByName(SpanNames, "fromMethod")
    expected = "SpanNames.fromMethod"
  }

  def "test fromMethod with class and method name"() {
    when:
    String result = SpanNames.fromMethod(clazz, method)

    then:
    result == expected

    where:
    clazz = SpanNames
    method = "test"
    expected = "SpanNames.test"
  }
}
