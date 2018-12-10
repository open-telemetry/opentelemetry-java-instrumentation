package datadog.trace.tracer;

import datadog.trace.tracer.writer.Writer;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

public class TraceImpl implements Trace {
  private final Writer writer = null;

  // TODO: Document approach to weak-referencdes and cleanup. If a span has to be closed by our GC
  // logic the trace should increment the writer's count but not report (invalid api usage produces
  // suspect data).
  private final Set<WeakReference<Span>> inFlightSpans = null;
  private final Set<WeakReference<Trace.Continuation>> inFlightContinuations = null;

  /** Strong refs to spans which are closed */
  private final List<Span> finishedSpans = null;

  private final Span rootSpan = null;

  /**
   * Create a new Trace.
   *
   * @param tracer the Tracer to apply settings from.
   */
  TraceImpl(
      Tracer tracer,
      SpanContext rootSpanParentContext,
      final long rootSpanStartTimestampNanoseconds) {}

  @Override
  public Tracer getTracer() {
    return null;
  }

  @Override
  public Span getRootSpan() {
    return null;
  }

  @Override
  public Span createSpan(Span parentSpan) {
    return null;
  }

  @Override
  public Continuation createContinuation(Span parentSpan) {
    return null;
  }

  // TODO methods to inform the trace that continuations and spans finished/closed. Also be able to
  // inform trace when a span finishes due to GC.
}
