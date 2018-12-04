package datadog.trace.tracer.impl;

import datadog.trace.tracer.Span;
import datadog.trace.tracer.writer.Writer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TraceImpl {
  private final Writer writer = null;
  /**
   * Count of all unfinished spans and continuations.
   *
   * <p>When the count reaches 0 this trace will be reported.
   */
  private final AtomicInteger referenceCount = new AtomicInteger(0);

  /**
   * Thread safe list of spans. List is ordered by span-creation time. The root span will always be
   * the first element. Note that span creation time may differ from span start timestamp (in cases
   * where span creators specify a custom start timestamp).
   *
   * <p>References to these spans are weakly held. If api users create spans but do not finish them
   * we will clean up this trace and increment the trace count with the writer. However, the trace
   * itself will not be reported because incorrect api usage produces suspect data.
   *
   * <p>TODO: There is a potential edge-case with the root span. If the root span is GC'd everything
   * relying on getRootSpan() will break.
   */
  private final List<Span> spans = null;
}
