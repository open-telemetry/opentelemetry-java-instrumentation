/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle

import spock.lang.Specification
import spock.lang.Unroll

class ReferenceCreationPredicateTest extends Specification {
  @Unroll
  def "should create reference for #desc"() {
    expect:
    ReferenceCreationPredicate.shouldCreateReferenceFor(className)

    where:
    desc                            | className
    "auto instrumentation class"    | "io.opentelemetry.instrumentation.auto.some_instrumentation.Advice"
    "javaagent-tooling class"       | "io.opentelemetry.javaagent.tooling.Constants"
    "library instrumentation class" | "io.opentelemetry.instrumentation.LibraryClass"
  }

  @Unroll
  def "should not create reference for #desc"() {
    expect:
    !ReferenceCreationPredicate.shouldCreateReferenceFor(className)

    where:
    desc                        | className
    "Java SDK class"            | "java.util.ArrayList"
    "instrumentation-api class" | "io.opentelemetry.instrumentation.api.InstrumentationVersion"
    "auto-api class"            | "io.opentelemetry.instrumentation.auto.api.ContextStore"
  }
}
