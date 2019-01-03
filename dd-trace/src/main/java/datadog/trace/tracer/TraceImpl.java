package datadog.trace.tracer;

import com.fasterxml.jackson.annotation.JsonValue;
import datadog.trace.tracer.writer.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

class TraceImpl implements TraceInternal {

  /* We use weakly referenced sets to track 'in-flight' spans and continuations. We use span/continuation's
  finalizer to notify trace that span/continuation is being GCed.
  If any part of the trace (span or continuation) has been was finished (closed) via GC then trace would be
  marked as 'invalid' and will not be reported the the backend. Instead only writer's counter would be incremented.
  This allows us not to report traces that have wrong timing information.
  Note: instead of using {@link WeakHashMap} we may want to consider using more fancy implementations from
  {@link datadog.trace.agent.tooling.WeakMapSuppliers}. If we do this care should be taken to avoid creating
  cleanup threads per trace.
   */
  private final Set<Span> inFlightSpans =
      Collections.newSetFromMap(new WeakHashMap<Span, Boolean>());
  private final Set<Continuation> inFlightContinuations =
      Collections.newSetFromMap(new WeakHashMap<Continuation, Boolean>());

  /** Strong refs to spans which are closed */
  private final List<Span> finishedSpans = new ArrayList();

  private final Tracer tracer;
  private final Clock clock;
  private final Span rootSpan;
  private boolean valid = true;
  private boolean finished = false;

  /**
   * Create a new Trace.
   *
   * @param tracer the Tracer to apply settings from.
   */
  TraceImpl(
      final Tracer tracer,
      final SpanContext rootSpanParentContext,
      final Timestamp rootSpanStartTimestamp) {
    this.tracer = tracer;
    clock = rootSpanStartTimestamp.getClock();
    rootSpan = new SpanImpl(this, rootSpanParentContext, rootSpanStartTimestamp);
    inFlightSpans.add(rootSpan);
  }

  @Override
  public Tracer getTracer() {
    return tracer;
  }

  @Override
  public Span getRootSpan() {
    return rootSpan;
  }

  @Override
  @JsonValue
  public synchronized List<Span> getSpans() {
    if (!finished) {
      tracer.reportError("Cannot get spans, trace is not finished yet: %s", this);
      return Collections.EMPTY_LIST;
    }
    return Collections.unmodifiableList(finishedSpans);
  }

  @Override
  public synchronized boolean isValid() {
    return valid;
  }

  @Override
  public Timestamp createCurrentTimestamp() {
    return clock.createCurrentTimestamp();
  }

  @Override
  public Span createSpan(final SpanContext parentContext) {
    return createSpan(parentContext, createCurrentTimestamp());
  }

  @Override
  public synchronized Span createSpan(
      final SpanContext parentContext, final Timestamp startTimestamp) {
    checkTraceFinished("create span");
    if (parentContext == null) {
      throw new TraceException("Got null parent context, trace: " + this);
    }
    if (!parentContext.getTraceId().equals(rootSpan.getContext().getTraceId())) {
      throw new TraceException(
          String.format(
              "Wrong trace id when creating a span. Got %s, expected %s",
              parentContext.getTraceId(), rootSpan.getContext().getTraceId()));
    }
    final Span span = new SpanImpl(this, parentContext, startTimestamp);
    inFlightSpans.add(span);
    return span;
  }

  @Override
  public synchronized Continuation createContinuation(final Span span) {
    checkTraceFinished("create continuation");
    if (span == null) {
      throw new TraceException("Got null parent span, trace: " + this);
    }
    if (!span.getContext().getTraceId().equals(rootSpan.getContext().getTraceId())) {
      throw new TraceException(
          String.format(
              "Wrong trace id when creating a span. Got %s, expected %s",
              span.getContext().getTraceId(), rootSpan.getContext().getTraceId()));
    }
    final Continuation continuation = new ContinuationImpl(this, span);
    inFlightContinuations.add(continuation);
    return continuation;
  }

  @Override
  public synchronized void finishSpan(final Span span, final boolean invalid) {
    checkTraceFinished("finish span");
    if (!inFlightSpans.contains(span)) {
      tracer.reportError("Trace doesn't contain continuation to finish: %s, trace: %s", span, this);
      return;
    }
    if (invalid) {
      valid = false;
    }
    inFlightSpans.remove(span);
    finishedSpans.add(span);
    checkAndWriteTrace();
  }

  @Override
  public synchronized void closeContinuation(
      final Continuation continuation, final boolean invalid) {
    checkTraceFinished("close continuation");
    if (!inFlightContinuations.contains(continuation)) {
      tracer.reportError(
          "Trace doesn't contain continuation to finish: %s, trace: %s", continuation, this);
      return;
    }
    if (invalid) {
      valid = false;
    }
    inFlightContinuations.remove(continuation);
    checkAndWriteTrace();
  }

  /**
   * Helper to check if trace is ready to be written and write it if it is.
   *
   * <p>Note: This has to be called under object lock.
   */
  private void checkAndWriteTrace() {
    if (inFlightSpans.isEmpty() && inFlightContinuations.isEmpty()) {
      final Writer writer = tracer.getWriter();
      writer.incrementTraceCount();
      final Trace trace = runInterceptorsBeforeTraceWritten(this);
      if (trace != null && tracer.getSampler().sample(trace)) {
        writer.write(trace);
      }
      finished = true;
    }
  }

  /**
   * Helper to run interceptor hooks before trace is finished.
   *
   * <p>Note: This has to be called under object lock.
   */
  private Trace runInterceptorsBeforeTraceWritten(Trace trace) {
    final List<Interceptor> interceptors = tracer.getInterceptors();
    // Run interceptors in 'reverse' order
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      // TODO: we probably should handle exceptions in interceptors more or less gracefully
      trace = interceptors.get(i).beforeTraceWritten(trace);
      if (trace == null) {
        break;
      }
    }
    return trace;
  }

  /**
   * Helper to check if trace is finished and report an error if it is.
   *
   * <p>This has to be called under object lock
   *
   * @param action action to report error with.
   */
  private void checkTraceFinished(final String action) {
    if (finished) {
      tracer.reportError("Cannot %s, trace has already been finished: %s", action, this);
    }
  }
}
