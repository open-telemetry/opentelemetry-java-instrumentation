import io.opentelemetry.auto.test.log.events.LogEventsTestBase

import java.util.logging.Logger

class JavaUtilLoggingEventTest extends LogEventsTestBase {

  @Override
  Object createLogger(String name) {
    Logger.getLogger(name)
  }

  String warn() {
    return "warning"
  }

  String error() {
    return "severe"
  }
}
