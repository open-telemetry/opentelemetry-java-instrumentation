package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingWriter implements Writer {

  @Override
  public void write(final List<DDSpan> trace) {
    if (log.isInfoEnabled() && !trace.isEmpty()) {
      try {
        log.info("write(trace): {}", trace.get(0).getTraceId());
      } catch (final Exception e) {
        log.error("error writing(trace): {}", trace);
      }
    }
  }

  @Override
  public void incrementTraceCount() {
    log.info("incrementTraceCount()");
  }

  @Override
  public void close() {
    log.info("close()");
  }

  @Override
  public void start() {
    log.info("start()");
  }

  @Override
  public String toString() {
    return "LoggingWriter { }";
  }
}
