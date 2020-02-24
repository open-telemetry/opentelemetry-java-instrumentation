package datadog.trace.agent.tooling.bytebuddy.matcher

import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.A
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.B
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.E
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.F
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.G
import datadog.trace.util.test.DDSpecification
import net.bytebuddy.description.type.TypeDescription

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType
import static net.bytebuddy.matcher.ElementMatchers.named

class SafeHasSuperTypeMatcherTest extends DDSpecification {

  def "test matcher #matcherClass.simpleName -> #type.simpleName"() {
    expect:
    safeHasSuperType(matcher).matches(argument) == result

    where:
    matcherClass | type | result
    A            | A    | true
    A            | B    | true
    B            | A    | false
    A            | E    | true
    A            | F    | true
    F            | A    | false
    F            | F    | true
    F            | G    | true

    matcher = named(matcherClass.name)
    argument = TypeDescription.ForLoadedType.of(type)
  }

  def "test traversal exceptions"() {
    setup:
    def type = Mock(TypeDescription)
    def typeGeneric = Mock(TypeDescription.Generic)
    def matcher = safeHasSuperType(named(Object.name))

    when:
    def result = matcher.matches(type)

    then:
    !result // default to false
    noExceptionThrown()
    1 * type.asGenericType() >> typeGeneric
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
    1 * type.getInterfaces() >> { throw new Exception("getInterfaces exception") }
    1 * type.getSuperClass() >> { throw new Exception("getSuperClass exception") }
    2 * type.getTypeName() >> "type-name"
    0 * _
  }
}
