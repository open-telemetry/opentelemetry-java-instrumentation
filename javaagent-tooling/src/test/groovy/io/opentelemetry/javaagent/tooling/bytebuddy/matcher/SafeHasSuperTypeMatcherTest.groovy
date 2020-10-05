/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType
import static net.bytebuddy.matcher.ElementMatchers.named

import io.opentelemetry.javaagent.tooling.AgentTooling
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.E
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.description.type.TypeList
import net.bytebuddy.jar.asm.Opcodes
import spock.lang.Shared
import spock.lang.Specification

class SafeHasSuperTypeMatcherTest extends Specification {
  @Shared
  def typePool =
    AgentTooling.poolStrategy()
      .typePool(AgentTooling.locationStrategy().classFileLocator(this.class.classLoader, null), this.class.classLoader)

  def "test matcher #matcherClass.simpleName -> #type.simpleName"() {
    expect:
    safeHasSuperType(matcher).matches(argument) == result

    where:
    matcherClass | type | result
    A            | A    | false
    A            | B    | false
    B            | A    | false
    A            | E    | false
    A            | F    | true
    B            | G    | true
    F            | A    | false
    F            | F    | true
    F            | G    | true

    matcher = named(matcherClass.name)
    argument = typePool.describe(type.name).resolve()
  }

  def "test exception getting interfaces"() {
    setup:
    def type = Mock(TypeDescription)
    def typeGeneric = Mock(TypeDescription.Generic)
    def matcher = safeHasSuperType(named(Object.name))

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.getModifiers() >> Opcodes.ACC_ABSTRACT
    1 * type.asGenericType() >> typeGeneric
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
    1 * type.getInterfaces() >> { throw new Exception("getInterfaces exception") }
    1 * type.getSuperClass() >> { throw new Exception("getSuperClass exception") }
    2 * type.getTypeName() >> "type-name"
    0 * _
  }

  def "test traversal exceptions"() {
    setup:
    def type = Mock(TypeDescription)
    def typeGeneric = Mock(TypeDescription.Generic)
    def matcher = safeHasSuperType(named(Object.name))
    def interfaces = Mock(TypeList.Generic)
    def it = new ThrowOnFirstElement()

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.getModifiers() >> Opcodes.ACC_ABSTRACT
    1 * type.getInterfaces() >> interfaces
    1 * interfaces.iterator() >> it
    1 * type.asGenericType() >> typeGeneric
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
  }
}
