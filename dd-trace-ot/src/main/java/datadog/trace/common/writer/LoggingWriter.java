package datadog.trace.common.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import datadog.opentracing.DDSpan;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingWriter implements Writer {
  private final ObjectMapper serializer = new ObjectMapper();

  @Override
  public void write(final List<DDSpan> trace) {
    if (log.isInfoEnabled()) {
      try {
        log.info("write(trace): {}", serializer.writeValueAsString(trace));
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
