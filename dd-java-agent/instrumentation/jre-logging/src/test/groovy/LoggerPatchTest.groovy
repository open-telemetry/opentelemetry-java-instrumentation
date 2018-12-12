import datadog.trace.agent.test.AgentTestRunner

import java.util.logging.Logger

class LoggerPatchTest extends AgentTestRunner {
  def "datadog threads do not initialize the log manager"() {
    setup:
    String threadName = Thread.currentThread().getName()
    Thread.currentThread().setName("dd-test")
    Logger log = Logger.getLogger("foobar")

    expect:
    log.getName() == "datadog-logger"

    cleanup:
    Thread.currentThread().setName(threadName)
  }

  def "normal threads can reach the normal logger"() {
    setup:
    Logger log = Logger.getLogger("foobar")

    expect:
    log.getName() == "foobar"
  }
}
