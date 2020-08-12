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

package io.opentelemetry.auto.tooling.bytebuddy.matcher

import io.opentelemetry.auto.tooling.AgentTooling
import io.opentelemetry.auto.tooling.bytebuddy.matcher.testclasses.A
import io.opentelemetry.auto.tooling.bytebuddy.matcher.testclasses.B
import io.opentelemetry.auto.tooling.bytebuddy.matcher.testclasses.F
import io.opentelemetry.auto.tooling.bytebuddy.matcher.testclasses.G
import io.opentelemetry.auto.util.test.AgentSpecification
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.Opcodes
import spock.lang.Shared

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass
import static net.bytebuddy.matcher.ElementMatchers.named

class ExtendsClassMatcherTest extends AgentSpecification {
  @Shared
  def typePool =
    AgentTooling.poolStrategy()
      .typePool(AgentTooling.locationStrategy().classFileLocator(this.class.classLoader, null), this.class.classLoader)

  def "test matcher #matcherClass.simpleName -> #type.simpleName"() {
    expect:
    extendsClass(matcher).matches(argument) == result

    where:
    matcherClass | type | result
    A            | B    | false
    A            | F    | false
    G            | F    | false
    F            | F    | true
    F            | G    | true

    matcher = named(matcherClass.name)
    argument = typePool.describe(type.name).resolve()
  }

  def "test traversal exceptions"() {
    setup:
    def type = Mock(TypeDescription)
    def typeGeneric = Mock(TypeDescription.Generic)
    def matcher = extendsClass(named(Object.name))

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.getModifiers() >> Opcodes.ACC_ABSTRACT
    1 * type.asGenericType() >> typeGeneric
    1 * type.getTypeName() >> "type-name"
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
    1 * type.getSuperClass() >> { throw new Exception("getSuperClass exception") }
    0 * _
  }
}
