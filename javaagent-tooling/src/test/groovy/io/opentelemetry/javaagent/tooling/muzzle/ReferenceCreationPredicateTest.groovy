/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    desc                      | className
    "Instrumentation class"   | "io.opentelemetry.instrumentation.some_instrumentation.Advice"
    "javaagent-tooling class" | "io.opentelemetry.javaagent.tooling.Constants"
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
