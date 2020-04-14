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
package io.opentelemetry.auto.tooling.bytebuddy.matcher

import io.opentelemetry.auto.util.test.AgentSpecification
import net.bytebuddy.matcher.ElementMatcher

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.failSafe

class SafeMatcherTest extends AgentSpecification {

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
