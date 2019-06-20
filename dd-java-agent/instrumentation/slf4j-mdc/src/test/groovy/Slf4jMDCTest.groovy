import datadog.trace.agent.test.log.injection.LogContextInjectionTest
import org.slf4j.MDC

class Slf4jMDCTest extends LogContextInjectionTest {

  @Override
  def put(String key, Object value) {
    return MDC.put(key, value as String)
  }

  @Override
  def get(String key) {
    return MDC.get(key)
  }
}
