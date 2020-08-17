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

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasSuperMethod
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith
import static net.bytebuddy.matcher.ElementMatchers.none

import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.C
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.Trace
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.TracedClass
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.UntracedClass
import net.bytebuddy.description.method.MethodDescription

class HasSuperMethodMatcherTest extends AgentSpecification {

  def "test matcher #type.simpleName #method"() {
    expect:
    hasSuperMethod(isAnnotatedWith(Trace)).matches(argument) == result

    where:
    type          | method | result
    A             | "a"    | false
    B             | "b"    | true
    C             | "c"    | false
    F             | "f"    | true
    G             | "g"    | false
    TracedClass   | "a"    | true
    UntracedClass | "a"    | false
    UntracedClass | "b"    | true

    argument = new MethodDescription.ForLoadedMethod(type.getDeclaredMethod(method))
  }

  def "test constructor never matches"() {
    setup:
    def method = Mock(MethodDescription)
    def matcher = hasSuperMethod(none())

    when:
    def result = matcher.matches(method)

    then:
    !result
    1 * method.isConstructor() >> true
    0 * _
  }

  def "test traversal exceptions"() {
    setup:
    def method = Mock(MethodDescription)
    def matcher = hasSuperMethod(none())
    def sigToken = new MethodDescription.ForLoadedMethod(A.getDeclaredMethod("a")).asSignatureToken()

    when:
    def result = matcher.matches(method)

    then:
    !result // default to false
    1 * method.isConstructor() >> false
    1 * method.asSignatureToken() >> sigToken
    1 * method.getDeclaringType() >> null
    0 * _
  }
}
