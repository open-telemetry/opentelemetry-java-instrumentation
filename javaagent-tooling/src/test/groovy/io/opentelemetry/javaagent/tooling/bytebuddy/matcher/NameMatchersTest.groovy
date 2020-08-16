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

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

import io.opentelemetry.javaagent.tooling.matcher.NameMatchers
import io.opentelemetry.auto.util.test.AgentSpecification
import net.bytebuddy.description.NamedElement

class NameMatchersTest extends AgentSpecification {

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
