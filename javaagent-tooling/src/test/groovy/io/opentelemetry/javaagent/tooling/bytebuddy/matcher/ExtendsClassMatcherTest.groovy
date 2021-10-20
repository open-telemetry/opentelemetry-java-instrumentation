/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling
import net.bytebuddy.description.type.TypeDescription
import org.objectweb.asm.Opcodes
import spock.lang.Shared
import spock.lang.Specification

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass
import static net.bytebuddy.matcher.ElementMatchers.named

class ExtendsClassMatcherTest extends Specification {
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
