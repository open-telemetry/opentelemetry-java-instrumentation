/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle

import spock.lang.Specification
import spock.lang.Unroll

class InstrumentationClassPredicateTest extends Specification {
  @Unroll
  def "should collect references for #desc"() {
    setup:
    def predicate = new InstrumentationClassPredicate({ it.startsWith("com.example.instrumentation.library") })

    expect:
    predicate.isInstrumentationClass(className)

    where:
    desc                                       | className
    "javaagent instrumentation class"          | "io.opentelemetry.javaagent.instrumentation.some_instrumentation.Advice"
    "library instrumentation class"            | "io.opentelemetry.instrumentation.LibraryClass"
    "additional library instrumentation class" | "com.example.instrumentation.library.ThirdPartyExternalInstrumentation"
  }

  @Unroll
  def "should not collect references for #desc"() {
    setup:
    def predicate = new InstrumentationClassPredicate({ false })

    expect:
    !predicate.isInstrumentationClass(className)

    where:
    desc                                  | className
    "Java SDK class"                      | "java.util.ArrayList"
    "javaagent-tooling class"             | "io.opentelemetry.javaagent.tooling.Constants"
    "instrumentation-api class"           | "io.opentelemetry.instrumentation.api.InstrumentationVersion"
    "javaagent-instrumentation-api class" | "io.opentelemetry.instrumentation.api.field.ContextStore"
  }
}
