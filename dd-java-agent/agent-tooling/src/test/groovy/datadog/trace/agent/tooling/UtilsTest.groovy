package datadog.trace.agent.tooling

import spock.lang.Specification


class UtilsTest extends Specification {

  def "getStackTraceAsString() returns the stack trace as a single new line separated string"() {
    setup:
    def stackTrace = Utils.stackTraceAsString

    expect:
    stackTrace.contains('datadog.trace.agent.tooling.Utils')
    stackTrace.contains(System.getProperty("line.separator"))
  }
}
