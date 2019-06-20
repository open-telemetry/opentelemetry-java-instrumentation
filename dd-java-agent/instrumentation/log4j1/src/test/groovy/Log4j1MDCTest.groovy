import datadog.trace.agent.test.log.injection.LogContextContextInjectionTest
import org.apache.log4j.MDC

class Log4j1MDCTest extends LogContextContextInjectionTest {

  @Override
  def put(String key, Object value) {
    return MDC.put(key, value)
  }

  @Override
  def get(String key) {
    return MDC.get(key)
  }
}
