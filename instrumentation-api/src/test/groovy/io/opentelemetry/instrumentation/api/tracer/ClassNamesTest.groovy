/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.instrumentation.api.instrumenter.ClassNames
import spock.lang.Specification

class ClassNamesTest extends Specification {

  def "test simpleName"() {
    when:
    String result = ClassNames.simpleName(clazz)

    then:
    result == expected

    where:
    clazz          | expected
    ClassNamesTest | "ClassNamesTest"
    ClassNames     | "ClassNames"
  }
}
