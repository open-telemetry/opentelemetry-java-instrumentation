import io.opentelemetry.auto.test.log.events.LogEventsTestBase
import unshaded.ch.qos.logback.classic.LoggerContext

class LogbackEventTest extends LogEventsTestBase {

  @Override
  Object createLogger(String name) {
    new LoggerContext().getLogger(name)
  }
}
