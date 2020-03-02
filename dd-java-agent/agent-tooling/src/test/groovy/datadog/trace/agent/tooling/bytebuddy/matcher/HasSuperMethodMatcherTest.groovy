package datadog.trace.agent.tooling.bytebuddy.matcher

import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.A
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.B
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.C
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.F
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.G
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.TracedClass
import datadog.trace.agent.tooling.bytebuddy.matcher.testclasses.UntracedClass
import datadog.trace.api.Trace
import datadog.trace.util.test.DDSpecification
import net.bytebuddy.description.method.MethodDescription

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.hasSuperMethod
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith
import static net.bytebuddy.matcher.ElementMatchers.none

class HasSuperMethodMatcherTest extends DDSpecification {

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
