package datadog.opentracing;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// TODO: figure out better concurrency model.
class TraceCollection extends ConcurrentLinkedQueue<DDSpan> {
  private final DDTracer tracer;

  /** Ensure a trace is never written multiple times */
  private final AtomicBoolean isWritten = new AtomicBoolean(false);

  TraceCollection(final DDTracer tracer) {
    this.tracer = tracer;
  }

  public void write() {
    if (isWritten.compareAndSet(false, true)) {
      for (final DDSpan span : this) {
        if (span.getDurationNano() == 0L) {
          log.warn(
              "{} - The trace is being written but this span isn't finished. You have to close each children.",
              this);
        }
      }
      log.debug("{} - Writing to {}.", this, tracer.writer);
      tracer.write(this);
    }
  }
}
