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
    expect:
    InstrumentationClassPredicate.isInstrumentationClass(className)

    where:
    desc                            | className
    "auto instrumentation class"    | "io.opentelemetry.javaagent.instrumentation.some_instrumentation.Advice"
    "javaagent-tooling class"       | "io.opentelemetry.javaagent.tooling.Constants"
    "library instrumentation class" | "io.opentelemetry.instrumentation.LibraryClass"
  }

  @Unroll
  def "should not collect references for #desc"() {
    expect:
    !InstrumentationClassPredicate.isInstrumentationClass(className)

    where:
    desc                        | className
    "Java SDK class"            | "java.util.ArrayList"
    "instrumentation-api class" | "io.opentelemetry.instrumentation.api.InstrumentationVersion"
    "javaagent-api class"       | "io.opentelemetry.javaagent.instrumentation.api.ContextStore"
  }
}
