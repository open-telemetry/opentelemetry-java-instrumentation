package datadog.trace.agent.tooling.bytebuddy.matcher

import datadog.trace.util.test.DDSpecification
import net.bytebuddy.matcher.ElementMatcher

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.failSafe

class SafeMatcherTest extends DDSpecification {

  def mockMatcher = Mock(ElementMatcher)

  def "test matcher"() {
    setup:
    def matcher = failSafe(mockMatcher, "test")

    when:
    def result = matcher.matches(new Object())

    then:
    1 * mockMatcher.matches(_) >> match
    result == match

    where:
    match << [true, false]
  }

  def "test matcher exception"() {
    setup:
    def matcher = failSafe(mockMatcher, "test")

    when:
    def result = matcher.matches(new Object())

    then:
    1 * mockMatcher.matches(_) >> { throw new Exception("matcher exception") }
    0 * _
    noExceptionThrown()
    !result // default to false
  }
}
