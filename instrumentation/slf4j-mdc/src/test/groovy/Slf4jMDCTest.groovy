import io.opentelemetry.auto.test.log.injection.LogContextInjectionTestBase
import org.slf4j.MDC

class Slf4jMDCTest extends LogContextInjectionTestBase {

  @Override
  def put(String key, Object value) {
    return MDC.put(key, value as String)
  }

  @Override
  def get(String key) {
    return MDC.get(key)
  }

  @Override
  def remove(String key) {
    return MDC.remove(key)
  }

  @Override
  def clear() {
    return MDC.clear()
  }
}
