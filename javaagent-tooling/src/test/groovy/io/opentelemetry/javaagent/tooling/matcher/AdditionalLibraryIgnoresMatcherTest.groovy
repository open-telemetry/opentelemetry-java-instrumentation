/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
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

  def "logback - don't match logger and logging events"() {
    setup:
    def type = Mock(TypeDescription)
    type.getActualName() >> typeName

    when:
    def matches = underTest.matches(type)

    then:
    !matches

    where:
    typeName << [
      "ch.qos.logback.classic.Logger",
      "ch.qos.logback.classic.spi.LoggingEvent",
      "ch.qos.logback.classic.spi.LoggingEventVO"
    ]
  }
}
