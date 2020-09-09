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

package io.opentelemetry.javaagent.tooling.matcher

import net.bytebuddy.description.type.TypeDescription
import spock.lang.Specification

class AdditionalLibraryIgnoresMatcherTest extends Specification {

  def underTest = new AdditionalLibraryIgnoresMatcher()

  def "spring boot - match not instrumented class"() {

    setup:
    def type = Mock(TypeDescription)
    type.getActualName() >> typeName

    when:
    def matches = underTest.matches(type)

    then:
    matches == true

    where:
    typeName << ["org.springframework.boot.NotInstrumentedClass",
                 "org.springframework.boot.embedded.tomcat.NotInstrumentedClass"]
  }

  def "spring boot - don't match instrumented class"() {

    setup:
    def type = Mock(TypeDescription)
    type.getActualName() >> typeName

    when:
    def matches = underTest.matches(type)

    then:
    matches == false

    where:
    typeName << ["org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext",
                 "org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedWebappClassLoader"]
  }

  def "spring boot - don't match instrumented inner class"() {

    setup:
    def type = Mock(TypeDescription)
    type.getActualName() >> typeName

    when:
    def matches = underTest.matches(type)

    then:
    matches == false

    where:
    typeName << ["org.springframework.boot.autoconfigure.BackgroundPreinitializer\$InnerClass1",
                 "org.springframework.boot.autoconfigure.condition.OnClassCondition\$ConditionMatch"]
  }
}
