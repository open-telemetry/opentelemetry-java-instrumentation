package datadog.opentracing;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** The default implementation of the LogHandler. */
@Slf4j
public class DefaultLogHandler implements LogHandler {
  @Override
  public void log(Map<String, ?> fields) {
    log.debug("`log` method is not implemented. Doing nothing");
  }

  @Override
  public void log(long timestampMicroseconds, Map<String, ?> fields) {
    log.debug("`log` method is not implemented. Doing nothing");
  }

  @Override
  public void log(String event) {
    log.debug("`log` method is not implemented. Provided log: {}", event);
  }

  @Override
  public void log(long timestampMicroseconds, String event) {
    log.debug("`log` method is not implemented. Provided log: {}", event);
  }
}
