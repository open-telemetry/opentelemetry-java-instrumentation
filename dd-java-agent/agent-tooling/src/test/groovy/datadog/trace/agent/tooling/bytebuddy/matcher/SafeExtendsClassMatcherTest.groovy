package datadog.trace.agent.tooling.bytebuddy.matcher

import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.A
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.B
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.F
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.G
import datadog.trace.util.test.DDSpecification
import net.bytebuddy.description.type.TypeDescription

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.extendsClass
import static net.bytebuddy.matcher.ElementMatchers.named

class SafeExtendsClassMatcherTest extends DDSpecification {

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
    argument = TypeDescription.ForLoadedType.of(type)
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
    1 * type.asGenericType() >> typeGeneric
    1 * type.getTypeName() >> "type-name"
    1 * typeGeneric.asErasure() >> { throw new Exception("asErasure exception") }
    1 * typeGeneric.getTypeName() >> "typeGeneric-name"
    1 * type.getSuperClass() >> { throw new Exception("getSuperClass exception") }
    0 * _
  }
}
