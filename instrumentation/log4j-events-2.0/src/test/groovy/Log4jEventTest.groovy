import io.opentelemetry.auto.test.log.events.LogEventsTestBase
import org.apache.logging.log4j.LogManager

class Log4jEventTest extends LogEventsTestBase {

  static {
    // need to initialize logger before tests to flush out init warning message:
    // "Unable to instantiate org.fusesource.jansi.WindowsAnsiOutputStream"
    LogManager.getLogger(Log4jEventTest)
  }

  @Override
  Object createLogger(String name) {
    LogManager.getLogger(name)
  }
}
