package datadog.trace.agent.integration

import io.opentracing.util.GlobalTracer
import java.lang.reflect.Field
import spock.lang.Specification

class TestLoggerRewrite extends Specification {
  def "java getLogger rewritten to safe logger"() {
    setup:
    Field logField = GlobalTracer.getDeclaredField("LOGGER")
    logField.setAccessible(true)
    Object logger = logField.get(null)

    expect:
    !logger.getClass().getName().startsWith("java.util.logging")

    cleanup:
    logField?.setAccessible(false)
  }
}
