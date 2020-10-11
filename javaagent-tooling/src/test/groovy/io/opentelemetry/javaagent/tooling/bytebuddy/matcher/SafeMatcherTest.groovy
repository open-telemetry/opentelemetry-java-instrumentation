/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.failSafe

import net.bytebuddy.matcher.ElementMatcher
import spock.lang.Specification

class SafeMatcherTest extends Specification {

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
