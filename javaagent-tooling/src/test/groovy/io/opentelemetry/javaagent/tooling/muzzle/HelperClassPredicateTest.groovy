/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle

import spock.lang.Specification
import spock.lang.Unroll

class HelperClassPredicateTest extends Specification {
  @Unroll
  def "should collect references for #desc"() {
    setup:
    def predicate = new HelperClassPredicate({ it.startsWith("com.example.instrumentation.library") })

    expect:
    predicate.isHelperClass(className)

    where:
    desc                                       | className
    "javaagent instrumentation class"          | "io.opentelemetry.javaagent.instrumentation.some_instrumentation.Advice"
    "library instrumentation class"            | "io.opentelemetry.instrumentation.LibraryClass"
    "additional library instrumentation class" | "com.example.instrumentation.library.ThirdPartyExternalInstrumentation"
  }

  @Unroll
  def "should not collect references for #desc"() {
    setup:
    def predicate = new HelperClassPredicate({ false })

    expect:
    !predicate.isHelperClass(className)

    where:
    desc                                  | className
    "Java SDK class"                      | "java.util.ArrayList"
    "javaagent-tooling class"             | "io.opentelemetry.javaagent.tooling.Constants"
    "instrumentation-api class"           | "io.opentelemetry.instrumentation.api.InstrumentationVersion"
    "bootstrap class"                     | "io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge"
  }
}
