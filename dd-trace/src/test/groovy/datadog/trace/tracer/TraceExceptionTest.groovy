package datadog.trace.tracer

import spock.lang.Specification


class TraceExceptionTest extends Specification {

  static final MESSAGE = "message"

  def "test default constructor"() {
    when:
    def exception = new TraceException()

    then:
    exception != null
  }

  def "test message constructor"() {
    when:
    def exception = new TraceException(MESSAGE)

    then:
    exception.getMessage() == MESSAGE
  }

  def "test cause constructor"() {
    setup:
    def cause = new RuntimeException()

    when:
    def exception = new TraceException(cause)

    then:
    exception.getCause() == cause
  }

  def "test cause and message constructor"() {
    setup:
    def cause = new RuntimeException()

    when:
    def exception = new TraceException(MESSAGE, cause)

    then:
    exception.getMessage() == MESSAGE
    exception.getCause() == cause
  }

}
