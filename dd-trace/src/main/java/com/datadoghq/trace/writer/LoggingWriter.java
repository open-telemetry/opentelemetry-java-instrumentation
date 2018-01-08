package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.Service;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(Writer.class)
public class LoggingWriter implements Writer {

  @Override
  public void write(final List<DDBaseSpan<?>> trace) {
    log.info("write(trace): {}", trace);
  }

  @Override
  public void writeServices(final Map<String, Service> services) {
    log.info("additional service information: {}", services.values());
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
