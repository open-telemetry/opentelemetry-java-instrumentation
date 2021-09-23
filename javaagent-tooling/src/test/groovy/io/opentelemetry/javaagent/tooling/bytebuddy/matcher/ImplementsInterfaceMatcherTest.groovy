/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.E
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.description.type.TypeList
import spock.lang.Shared
import spock.lang.Specification

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface
import static net.bytebuddy.matcher.ElementMatchers.named

class ImplementsInterfaceMatcherTest extends Specification {
  @Shared
  def typePool =
    AgentTooling.poolStrategy()
      .typePool(AgentTooling.locationStrategy().classFileLocator(this.class.classLoader, null), this.class.classLoader)

  def "test matcher #matcherClass.simpleName -> #type.simpleName"() {
    expect:
    implementsInterface(matcher).matches(argument) == result

    where:
    matcherClass | type | result
    A            | A    | true
    A            | B    | true
    B            | A    | false
    A            | E    | true
    A            | F    | true
    A            | G    | true
    F            | A    | false
    F            | F    | false
    F            | G    | false

    matcher = named(matcherClass.name)
    argument = typePool.describe(type.name).resolve()
  }

  def "test exception getting interfaces"() {
    setup:
    def type = Mock(TypeDescription)
    def typeGeneric = Mock(TypeDescription.Generic)
    def matcher = implementsInterface(named(Object.name))

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.isInterface() >> true
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
    def matcher = implementsInterface(named(Object.name))
    def interfaces = Mock(TypeList.Generic)
    def it = new ThrowOnFirstElement()

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.isInterface() >> true
    1 * type.asGenericType() >> typeGeneric
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
    1 * type.getInterfaces() >> interfaces
    1 * interfaces.iterator() >> it
    2 * type.getTypeName() >> "type-name"
    1 * type.getSuperClass() >> { throw new Exception("getSuperClass exception") }
    0 * _
  }
}
