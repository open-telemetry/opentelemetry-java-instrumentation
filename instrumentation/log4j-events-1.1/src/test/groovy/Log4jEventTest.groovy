import io.opentelemetry.auto.test.log.events.LogEventsTestBase
import org.apache.log4j.Logger

class Log4jEventTest extends LogEventsTestBase {

  @Override
  Object createLogger(String name) {
    Logger.getLogger(name)
  }
}
