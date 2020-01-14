package io.opentelemetry.auto.tooling

import io.opentelemetry.auto.util.test.AgentSpecification

class UtilsTest extends AgentSpecification {

  def "getStackTraceAsString() returns the stack trace as a single new line separated string"() {
    setup:
    def stackTrace = Utils.stackTraceAsString

    expect:
    stackTrace.contains('io.opentelemetry.auto.tooling.Utils')
    stackTrace.contains(System.getProperty("line.separator"))
  }
}
