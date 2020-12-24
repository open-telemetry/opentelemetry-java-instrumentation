/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher


import net.bytebuddy.description.NamedElement
import spock.lang.Specification

class NameMatchersTest extends Specification {

  def "test namedOneOf"() {
    setup:
    def named = Mock(NamedElement)
    named.getActualName() >> { name }
    def matcher = NameMatchers.namedOneOf("foo", "bar")

    when:
    def result = matcher.matches(named)

    then:
    result == expected

    where:
    name      | expected
    "foo"     | true
    "bar"     | true
    "missing" | false
  }


  def "test namedNoneOf"() {
    setup:
    def named = Mock(NamedElement)
    named.getActualName() >> { name }
    def matcher = NameMatchers.namedNoneOf("foo", "bar")

    when:
    def result = matcher.matches(named)

    then:
    result == expected

    where:
    name      | expected
    "foo"     | false
    "bar"     | false
    "missing" | true
  }
}
