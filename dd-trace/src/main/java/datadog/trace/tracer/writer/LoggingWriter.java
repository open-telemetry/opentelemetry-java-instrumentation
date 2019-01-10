package datadog.trace.tracer.writer;

import datadog.trace.tracer.Trace;
import lombok.extern.slf4j.Slf4j;

/** Writer implementation that just logs traces as they are being written */
@Slf4j
public class LoggingWriter implements Writer {
  @Override
  public void write(final Trace trace) {
    log.debug("Trace written: {}", trace);
  }

  @Override
  public void incrementTraceCount() {
    // Nothing to do here.
  }

  @Override
  public SampleRateByService getSampleRateByService() {
    return SampleRateByService.EMPTY_INSTANCE;
  }

  @Override
  public void start() {
    // TODO: do we really need this? and if we do - who is responsible for calling this?
    log.debug("{} started", getClass().getSimpleName());
  }

  @Override
  public void close() {
    log.debug("{} closed", getClass().getSimpleName());
  }
}
