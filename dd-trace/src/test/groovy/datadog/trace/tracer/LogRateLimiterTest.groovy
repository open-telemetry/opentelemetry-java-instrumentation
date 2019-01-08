package datadog.trace.tracer

import org.slf4j.Logger
import spock.lang.Specification


class LogRateLimiterTest extends Specification {

  private static final String MESSAGE = "message"
  private static final int REPEAT_COUNT = 10

  def log = Mock(Logger)
  def object = new Object()

  def "test debugging enabled: #method"() {
    setup:
    log.isDebugEnabled() >> true
    def logRateLimiter = new LogRateLimiter(log, 10)

    when: "message is logged"
    logRateLimiter."${method}"(MESSAGE, object)

    then: "debug message is output"
    1 * log.debug(MESSAGE, object)

    where:
    method | _
    "warn" | _
    "error" | _
  }

  def "test debugging disabled, no delay: #method"() {
    setup: "debug is disabled and delay between log is zero"
    log.isDebugEnabled() >> false
    def logRateLimiter = new LogRateLimiter(log, 0)

    when: "messages are logged"
    for (int i = 0; i < REPEAT_COUNT; i++) {
      logRateLimiter."${method}"(MESSAGE, object)
    }

    then: "all messages are output with appropriate log level"
    REPEAT_COUNT * log."${method}"({it.contains(MESSAGE)}, object)

    where:
    method | _
    "warn" | _
    "error" | _
  }

  def "test debugging disabled, large delay: #method"() {
    setup: "debug is disabled and delay between log is large"
    log.isDebugEnabled() >> false
    def logRateLimiter = new LogRateLimiter(log, 10000000)

    when: "messages are logged"
    for (int i = 0; i < REPEAT_COUNT; i++) {
      logRateLimiter."${method}"(MESSAGE, object)
    }

    then: "message is output once"
    1 * log."${method}"({it.contains(MESSAGE)}, object)

    where:
    method | _
    "warn" | _
    "error" | _
  }

}
